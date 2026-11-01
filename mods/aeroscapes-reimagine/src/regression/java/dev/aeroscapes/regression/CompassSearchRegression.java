package dev.aeroscapes.regression;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.chaosthedude.explorerscompass.worker.RandomSpreadSearchWorker;
import com.chaosthedude.explorerscompass.worker.SearchWorkerManager;
import com.chaosthedude.explorerscompass.worker.StructureSearchWorker;
import com.chaosthedude.naturescompass.NaturesCompass;
import com.chaosthedude.naturescompass.util.BiomeSearchWorker;
import com.chaosthedude.naturescompass.util.BiomeUtils;
import com.chaosthedude.naturescompass.util.CompassState;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.qualityoflife.CompassManagerBridge;
import dev.aeroscapes.qualityoflife.CompassRandomSpreadBridge;
import dev.aeroscapes.qualityoflife.CompassSearch;
import dev.aeroscapes.qualityoflife.CompassSearchNetwork.Request;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.Vec3;

/** Controlled native callbacks plus one real noise-biome worker and native pre-load/ring guards. */
public final class CompassSearchRegression {
  private static final UUID PLAYER = UUID.fromString("8939046b-53ec-47ae-a561-7628ac80f0dc");
  private final ServerPlayer player;
  private final ResourceLocation biome;

  private CompassSearchRegression(ServerPlayer player) {
    this.player = player;
    BlockPos origin = player.blockPosition();
    var level = player.serverLevel();
    var allowed = BiomeUtils.getAllowedBiomeKeys(level);
    biome =
        Mth.outFromOrigin(
                origin.getY(), level.getMinBuildHeight() + 1, level.getMaxBuildHeight(), 64)
            .mapToObj(
                y ->
                    level
                        .getChunkSource()
                        .getGenerator()
                        .getBiomeSource()
                        .getNoiseBiome(
                            QuartPos.fromBlock(origin.getX()), QuartPos.fromBlock(y),
                            QuartPos.fromBlock(origin.getZ()),
                                level.getChunkSource().randomState().sampler())
                        .value())
            .map(value -> BiomeUtils.getKeyForBiome(level, value).orElseThrow())
            .filter(allowed::contains)
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("No allowed biome in fixture's origin column"));
  }

  public static void verify(ServerLevel level) {
    require(level.getServer().getPlayerList().getPlayerCount() == 0, "Disposable server only");
    int y =
        Math.max(
            level.getMinBuildHeight() + 2,
            Math.min(level.getMaxBuildHeight() - 2, level.getSharedSpawnPos().getY()));
    try (var session =
            TestShipPlayerSession.create(
                level,
                PLAYER,
                "CompassTests",
                new Vec3(
                    level.getSharedSpawnPos().getX() + 0.5,
                    y,
                    level.getSharedSpawnPos().getZ() + 0.5),
                null);
        var registration = Registration.install(session.player)) {
      session.player.setNoGravity(true);
      CompassSearchRegression fixture = new CompassSearchRegression(session.player);
      try {
        CompassSearchCodecRegression.verify(level.registryAccess());
        fixture.boundaries();
        fixture.affordability();
        fixture.cancellations(registration);
        fixture.copiedStacks();
        fixture.nativeStructureGuards();
        CompassDensePlacementRegression.verify(session.player);
        fixture.nativeBiomeWorker();
      } finally {
        CompassSearch.cancel(session.player);
        session.player.getInventory().clearContent();
      }
    }
    LogUtils.getLogger()
        .info(
            "Compass search regression passed: controlledPayments=true lifecycle=true nativeBiome=true nativeStructureGuards=true codec=true");
  }

  private void boundaries() {
    require(CompassSearch.within(BlockPos.ZERO, 3000, 4000, 5000), "Circular free boundary");
    require(!CompassSearch.within(BlockPos.ZERO, 5000, 1, 5000), "Free radius is circular");
    require(CompassSearch.within(BlockPos.ZERO, 6000, 8000, 10000), "Circular paid boundary");
    require(!CompassSearch.within(BlockPos.ZERO, 10000, 1, 10000), "Paid radius is circular");
    for (int distance : new int[] {0, 5000}) {
      experience(2);
      CompassSearch.Job job = controlledBiome(true);
      found(job, 0, distance);
      require(
          player.experienceLevel == 2 && state(job) == CompassState.FOUND,
          "Free result must not charge at " + distance);
    }
    for (int distance : new int[] {5001, 10000}) {
      experience(2);
      CompassSearch.Job job = controlledBiome(true);
      advanceBiome(job);
      require(player.experienceLevel == 2, "Extended phase does not pre-charge");
      found(job, 1, distance);
      require(
          player.experienceLevel == 1
              && player.experienceProgress == 0.375F
              && state(job) == CompassState.FOUND,
          "Far result costs exactly one level at " + distance);
      int firstX = NaturesCompass.naturesCompass.getFoundBiomeX(job.stack);
      found(job, 1, distance + 1);
      require(
          player.experienceLevel == 1
              && NaturesCompass.naturesCompass.getFoundBiomeX(job.stack) == firstX,
          "Duplicate callback cannot charge or overwrite");
    }
    experience(2);
    CompassSearch.Job beyond = controlledBiome(true);
    advanceBiome(beyond);
    found(beyond, 1, 10001);
    require(
        player.experienceLevel == 2 && state(beyond) == CompassState.NOT_FOUND,
        "Beyond10000 is rejected without payment");

    experience(2);
    CompassSearch.Job freeOnly = controlledBiome(false);
    found(freeOnly, 0, 5001);
    require(
        player.experienceLevel == 2 && state(freeOnly) == CompassState.NOT_FOUND,
        "Free request cannot publish a paid result");

    experience(2);
    CompassSearch.Job nearDuringPaid = controlledBiome(true);
    advanceBiome(nearDuringPaid);
    found(nearDuringPaid, 1, 5000);
    require(
        player.experienceLevel == 2 && state(nearDuringPaid) == CompassState.FOUND,
        "A near result stays free even during phase1");
  }

  private void affordability() {
    experience(0);
    holdNature();
    CompassSearch.request(player, new Request(true, biome, false, false, true));
    require(job() == null && player.experienceLevel == 0, "Insufficient at start creates no job");

    experience(2);
    CompassSearch.Job commit = controlledBiome(true);
    advanceBiome(commit);
    player.setExperienceLevels(0);
    found(commit, 1, 5001);
    require(
        player.experienceLevel == 0 && state(commit) == CompassState.NOT_FOUND,
        "Insufficient at commit does not publish or charge");

    experience(2);
    CompassSearch.Job transition = controlledBiome(true);
    player.setExperienceLevels(0);
    CompassSearch.biomeFailed(transition, 0, false);
    require(
        job() == null && player.experienceLevel == 0,
        "Affordability is checked again before extended phase");

    experience(0);
    player.setGameMode(GameType.CREATIVE);
    CompassSearch.Job creative = controlledBiome(true);
    advanceBiome(creative);
    found(creative, 1, 10000);
    require(
        player.experienceLevel == 0 && state(creative) == CompassState.FOUND,
        "Creative searches are exempt");
    player.setGameMode(GameType.SURVIVAL);
  }

  private void cancellations(Registration registration) {
    experience(2);
    CompassSearch.Job cancelled = controlledBiome(true);
    advanceBiome(cancelled);
    var previousPose = player.getPose();
    boolean previousShift = player.isShiftKeyDown();
    try {
      player.setShiftKeyDown(true);
      player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
      require(player.isCrouching(), "Native crouch state is active");
      NaturesCompass.naturesCompass.use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
    } finally {
      player.setShiftKeyDown(previousShift);
      player.setPose(previousPose);
    }
    found(cancelled, 1, 5001);
    require(
        player.experienceLevel == 2 && job() == null,
        "Native crouch-use cancellation does not charge");

    experience(2);
    CompassSearch.Job replaced = controlledBiome(true);
    advanceBiome(replaced);
    CompassSearch.Job replacement = controlledBiome(false);
    found(replaced, 1, 5001);
    require(
        player.experienceLevel == 2 && job() == replacement,
        "Replaced callback cannot charge or clear newer request");
    CompassSearch.cancel(player);

    experience(2);
    CompassSearch.Job disconnected = controlledBiome(true);
    advanceBiome(disconnected);
    registration.remove();
    try {
      found(disconnected, 1, 5001);
      require(
          player.experienceLevel == 2 && job() == null, "Disconnected callback does not charge");
    } finally {
      registration.restoreFixture();
    }

    experience(2);
    CompassSearch.Job moved = controlledBiome(true);
    advanceBiome(moved);
    ServerLevel original = player.serverLevel();
    ServerLevel nether = original.getServer().getLevel(Level.NETHER);
    require(nether != null, "Nether exists for dimension guard");
    player.setServerLevel(nether);
    try {
      found(moved, 1, 5001);
      require(player.experienceLevel == 2 && job() == null, "Dimension change cancels payment");
    } finally {
      player.setServerLevel(original);
    }

    experience(2);
    CompassSearch.Job removed = controlledBiome(true);
    advanceBiome(removed);
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    found(removed, 1, 5001);
    require(player.experienceLevel == 2 && job() == null, "Removed compass cancels payment");

    experience(2);
    CompassSearch.Job consumed = controlledBiome(true);
    advanceBiome(consumed);
    consumed.stack.shrink(1);
    found(consumed, 1, 5001);
    require(
        player.experienceLevel == 2 && job() == null,
        "Zero-count compass retained in the same inventory slot cannot charge");
  }

  private void copiedStacks() {
    for (boolean carried : new boolean[] {false, true}) {
      experience(2);
      CompassSearch.Job job = controlledBiome(true);
      advanceBiome(job);
      ItemStack moved = player.getInventory().removeItem(player.getInventory().selected, 1);
      require(
          !moved.isEmpty() && moved != job.stack,
          "Actual inventory split creates a different compass stack");
      try {
        if (carried) player.containerMenu.setCarried(moved);
        else player.getInventory().setItem(1, moved);
        CompassSearch.reconcile(player);
        require(
            job() == null
                && player.experienceLevel == 2
                && NaturesCompass.naturesCompass.getState(moved) == CompassState.INACTIVE,
            "Copied compass cancels without payment and clears its orphaned searching state");
        found(job, 1, 5001);
        require(player.experienceLevel == 2, "Moved compass rejects stale paid callback");
      } finally {
        player.containerMenu.setCarried(ItemStack.EMPTY);
      }
    }
  }

  private void nativeBiomeWorker() {
    experience(2);
    holdNature();
    CompassSearch.request(player, new Request(true, biome, false, false, true));
    CompassSearch.Job job = requireJob();
    BiomeSearchWorker worker = field(job, "biomeWorker", BiomeSearchWorker.class);
    worker.doWork();
    require(
        state(job) == CompassState.FOUND && player.experienceLevel == 2 && job() == null,
        "Real native biome noise sampling publishes a free result");
    require(
        NaturesCompass.naturesCompass.getFoundBiomeX(job.stack) == job.origin.getX()
            && NaturesCompass.naturesCompass.getFoundBiomeZ(job.stack) == job.origin.getZ(),
        "Native worker found the sampled origin biome");
  }

  private void nativeStructureGuards() {
    ServerLevel level = player.serverLevel();
    ResourceLocation target =
        StructureUtils.getAllowedStructureKeys(level).stream()
            .filter(
                key -> {
                  var structure = StructureUtils.getStructureForKey(level, key);
                  return structure != null
                      && level
                          .getChunkSource()
                          .getGeneratorState()
                          .getPlacementsForStructure(
                              StructureUtils.getHolderForStructure(level, structure))
                          .stream()
                          .anyMatch(
                              placement -> placement instanceof RandomSpreadStructurePlacement);
                })
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No random-spread target in fixture"));
    experience(2);
    player.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ExplorersCompass.explorersCompass));
    CompassSearch.request(player, new Request(false, target, false, false, true));
    CompassSearch.Job near = requireJob();
    CompassSearch.request(
        player,
        new Request(
            false,
            ResourceLocation.fromNamespaceAndPath("aeroscapes_regression", "missing_group"),
            true,
            false,
            true));
    require(
        job() == near && player.experienceLevel == 2,
        "Unknown network group is rejected without replacing an owned search or charging");
    guards(near);
    BlockPos nearResult = near.origin.offset(5000, 0, 0);
    CompassSearch.structureFound(
        near, 0, nearResult, near.structures.getFirst(), List.of(nearResult), 1);
    require(
        player.experienceLevel == 2 && job() == null,
        "Native structure job can commit free boundary");
    near.stack.set(ExplorersCompass.STRUCTURE_ID_COMPONENT, "Malformed structure ID");
    CompassSearch.request(player, new Request(false, target, false, true, false));
    require(
        job() == null && player.experienceLevel == 2,
        "Malformed stored target is rejected by Next without an exception or payment");

    player.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ExplorersCompass.explorersCompass));
    CompassSearch.request(player, new Request(false, target, false, false, true));
    CompassSearch.Job far = requireJob();
    SearchWorkerManager manager = field(far, "manager", SearchWorkerManager.class);
    var workers = List.copyOf(((CompassManagerBridge) manager).aeroscapes$workers());
    require(!workers.isEmpty(), "Native structure workers were created");
    for (StructureSearchWorker<?> worker : workers) {
      worker.stop();
      CompassSearch.structureFailed(far, 0, false);
    }
    require(
        far.radius() == 10000 && player.experienceLevel == 2,
        "All native free-phase workers finish before paid phase starts");
    guards(far);
    BlockPos farResult = far.origin.offset(10000, 0, 0);
    CompassSearch.structureFound(
        far, 1, farResult, far.structures.getFirst(), List.of(farResult), 1);
    require(
        player.experienceLevel == 1 && job() == null,
        "Controlled far callback through a native structure job charges one level");
  }

  private void guards(CompassSearch.Job job) {
    SearchWorkerManager manager = field(job, "manager", SearchWorkerManager.class);
    StructureSearchWorker<?> worker =
        ((CompassManagerBridge) manager)
            .aeroscapes$workers().stream()
                .filter(candidate -> candidate instanceof RandomSpreadSearchWorker)
                .findFirst()
                .orElseThrow();
    CompassRandomSpreadBridge bridge = (CompassRandomSpreadBridge) worker;
    require(
        worker.hasWork() && bridge.aeroscapes$spacing() > 0, "Native random-spread worker is live");
    long spacing = bridge.aeroscapes$spacing() * 16L;
    int ringLimit = Math.toIntExact((job.radius() + spacing - 1) / spacing + 2);
    Field ring = declared(RandomSpreadSearchWorker.class, "length");
    try {
      int previous = ring.getInt(worker);
      ring.setInt(worker, ringLimit + 1);
      require(
          !worker.hasWork(),
          "Pre-load traversal is bounded by region ring, not last corner radius");
      ring.setInt(worker, previous);
      require(worker.hasWork(), "Ring guard test did not merely stop the worker");
      ChunkPos outside =
          new ChunkPos((job.origin.getX() + job.radius() + 8192) >> 4, job.origin.getZ() >> 4);
      var map = job.level.getChunkSource().chunkMap;
      var updating = ChunkMap.class.getDeclaredMethod("getUpdatingChunkIfPresent", long.class);
      require(updating.trySetAccessible(), "Native updating-holder query is accessible to fixture");
      require(
          map.getVisibleChunkIfPresent(outside.toLong()) == null
              && updating.invoke(map, outside.toLong()) == null,
          "Outside candidate starts unloaded");
      var probe =
          StructureSearchWorker.class.getDeclaredMethod("getStructureGeneratingAt", ChunkPos.class);
      require(probe.trySetAccessible(), "Native candidate method is accessible to fixture");
      Object result = probe.invoke(worker, outside);
      require(
          result == null
              && map.getVisibleChunkIfPresent(outside.toLong()) == null
              && updating.invoke(map, outside.toLong()) == null,
          "Outside native candidate is rejected before a chunk holder is loaded");
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
      throw new IllegalStateException("Cannot exercise pinned native structure worker", failure);
    }
  }

  private CompassSearch.Job controlledBiome(boolean extended) {
    holdNature();
    CompassSearch.request(player, new Request(true, biome, false, false, extended));
    CompassSearch.Job job = requireJob();
    field(job, "biomeWorker", BiomeSearchWorker.class).stop();
    return job;
  }

  private void advanceBiome(CompassSearch.Job job) {
    CompassSearch.biomeFailed(job, 0, false);
    require(
        job.radius() == 10000 && job() == job, "Controlled complete free phase starts paid phase");
    field(job, "biomeWorker", BiomeSearchWorker.class).stop();
  }

  private static void found(CompassSearch.Job job, int phase, int distance) {
    BlockPos result = job.origin.offset(distance, 0, 0);
    CompassSearch.biomeFound(job, phase, result.getX(), result.getZ(), List.of(result), 1);
  }

  private static CompassState state(CompassSearch.Job job) {
    return NaturesCompass.naturesCompass.getState(job.stack);
  }

  private void holdNature() {
    player.getInventory().selected = 0;
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NaturesCompass.naturesCompass));
  }

  private void experience(int levels) {
    player.setGameMode(GameType.SURVIVAL);
    player.setExperienceLevels(levels);
    player.experienceProgress = 0.375F;
    player.totalExperience = 1234;
  }

  private CompassSearch.Job requireJob() {
    CompassSearch.Job job = job();
    require(job != null, "Production request created a job");
    return job;
  }

  private CompassSearch.Job job() {
    Object value = get(declared(CompassSearch.class, "JOBS"), null);
    require(value instanceof Map<?, ?>, "Production job registry type");
    Object current = ((Map<?, ?>) value).get(player.getUUID());
    if (current == null) return null;
    require(current instanceof CompassSearch.Job, "Production job value type");
    return (CompassSearch.Job) current;
  }

  private static <T> T field(Object target, String name, Class<T> type) {
    Object value = get(declared(target.getClass(), name), target);
    require(type.isInstance(value), "Expected pinned field " + name);
    return type.cast(value);
  }

  private static Field declared(Class<?> owner, String name) {
    try {
      Field field = owner.getDeclaredField(name);
      require(field.trySetAccessible(), "Accessible fixture field " + name);
      return field;
    } catch (NoSuchFieldException failure) {
      throw new IllegalStateException("Pinned regression field changed: " + name, failure);
    }
  }

  private static Object get(Field field, Object owner) {
    try {
      return field.get(owner);
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException("Cannot read pinned regression field", failure);
    }
  }

  private static final class Registration implements AutoCloseable {
    private final Map<UUID, ServerPlayer> players;
    private final ServerPlayer fixture;
    private final ServerPlayer previous;

    private Registration(Map<UUID, ServerPlayer> players, ServerPlayer fixture) {
      this.players = players;
      this.fixture = fixture;
      previous = players.put(fixture.getUUID(), fixture);
    }

    @SuppressWarnings("unchecked") // Exact native field signature and every existing entry checked.
    private static Registration install(ServerPlayer player) {
      Object value =
          get(declared(PlayerList.class, "playersByUUID"), player.server.getPlayerList());
      require(value instanceof Map<?, ?>, "Native player UUID registry type");
      Map<?, ?> entries = (Map<?, ?>) value;
      for (var entry : entries.entrySet()) {
        require(
            entry.getKey() instanceof UUID && entry.getValue() instanceof ServerPlayer,
            "Native player registry entries match exact source signature");
      }
      return new Registration((Map<UUID, ServerPlayer>) entries, player);
    }

    private void remove() {
      require(players.remove(fixture.getUUID(), fixture), "Fixture UUID registration removed");
    }

    private void restoreFixture() {
      require(
          players.putIfAbsent(fixture.getUUID(), fixture) == null, "Fixture registration restored");
    }

    @Override
    public void close() {
      if (players.get(fixture.getUUID()) != fixture) return;
      if (previous == null) players.remove(fixture.getUUID(), fixture);
      else players.put(fixture.getUUID(), previous);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
