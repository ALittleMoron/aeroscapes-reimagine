package dev.aeroscapes.regression;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.chaosthedude.explorerscompass.worker.ConcentricRingsSearchWorker;
import com.chaosthedude.explorerscompass.worker.GenericSearchWorker;
import com.chaosthedude.explorerscompass.worker.RandomSpreadSearchWorker;
import com.chaosthedude.explorerscompass.worker.SearchWorkerManager;
import com.chaosthedude.explorerscompass.worker.StructureSearchWorker;
import com.chaosthedude.naturescompass.NaturesCompass;
import com.chaosthedude.naturescompass.util.BiomeSearchWorker;
import com.chaosthedude.naturescompass.util.BiomeUtils;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.qualityoflife.CompassManagerBridge;
import dev.aeroscapes.qualityoflife.CompassRandomSpreadBridge;
import dev.aeroscapes.qualityoflife.CompassSearch;
import dev.aeroscapes.qualityoflife.CompassSearchNetwork.Request;
import dev.aeroscapes.qualityoflife.CompassWorkerBridge;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** Native cursor/counter tests; empty target sets isolate traversal from chunk generation. */
public final class CompassDensePlacementRegression {
  private CompassDensePlacementRegression() {}

  /** Invoke while CompassSearchRegression's existing fixture UUID registration is active. */
  public static void verify(ServerPlayer player) {
    require(
        player.server.getPlayerList().getPlayer(player.getUUID()) == player, "Registered fixture");
    int limit = com.chaosthedude.explorerscompass.config.ConfigHandler.GENERAL.maxSamples.get();
    require(limit == 100000, "This case verifies the selected native100000-sample profile");
    ItemStack previous = player.getMainHandItem();
    int levels = player.experienceLevel;
    float progress = player.experienceProgress;
    int total = player.totalExperience;
    GameType mode = player.gameMode.getGameModeForPlayer();
    try {
      player.setGameMode(GameType.SURVIVAL);
      player.setExperienceLevels(2);
      ResourceLocation target =
          List.of("minecraft:mineshaft", "minecraft:buried_treasure").stream()
              .map(ResourceLocation::parse)
              .filter(StructureUtils.getAllowedStructureKeys(player.serverLevel())::contains)
              .findFirst()
              .orElseThrow();
      CompassSearch.Job job = request(player, target);
      RandomSpreadSearchWorker worker = spread(job);
      require(
          ((CompassRandomSpreadBridge) worker).aeroscapes$spacing() == 1,
          "Actual selected dense placement has spacing1");
      noTargets(worker);
      set(StructureSearchWorker.class, worker, "samples", limit - 1);
      CompassRandomSpreadBridge cursor = (CompassRandomSpreadBridge) worker;
      cursor.aeroscapes$ring(158);
      cursor.aeroscapes$cellX(-158);
      cursor.aeroscapes$cellZ(-158);
      worker.doWork();
      require(
          current(player) == job && job.radius() == 10000,
          "Native free budget exhaustion reaches opted-in paid phase");
      require(
          Boolean.TRUE.equals(read(CompassSearch.Job.class, job, "sampleLimitReached")),
          "Incomplete free budget remains recorded honestly");
      require(player.experienceLevel == 2, "No charge for exhausting the free budget");

      RandomSpreadSearchWorker extended = spread(job);
      noTargets(extended);
      require(
          ((CompassRandomSpreadBridge) extended).aeroscapes$ring() > 0,
          "Paid dense worker does not restart at the inner origin");
      probeFar(job, extended, 1_000_000, "random-spread");
      genericFamily(job, extended);
      CompassSearch.cancel(player);
      concentricFamily(player);
      nativeBiomeTraversal(player);
      nativePriceMaps(player, target);
    } finally {
      CompassSearch.cancel(player);
      player.setItemInHand(InteractionHand.MAIN_HAND, previous);
      player.setExperienceLevels(levels);
      player.experienceProgress = progress;
      player.totalExperience = total;
      player.setGameMode(mode);
    }
    LogUtils.getLogger()
        .info(
            "Compass dense placement regression passed: nativeBudgetTransition=true paidRandomSpread=true paidGeneric=true paidConcentric=true nativeBiomeTraversal=true nativePriceMapsZero=true");
  }

  private static void nativeBiomeTraversal(ServerPlayer player) {
    CompassSearch.cancel(player);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NaturesCompass.naturesCompass));
    ResourceLocation target = BiomeUtils.getAllowedBiomeKeys(player.serverLevel()).getFirst();
    CompassSearch.request(player, new Request(true, target, false, false, true));
    CompassSearch.Job job = current(player);
    require(job != null && job.radius() == 5000, "Production biome free phase starts");
    BiomeSearchWorker free = biomeWorker(job);
    require(
        Integer.valueOf(64).equals(read(BiomeSearchWorker.class, free, "sampleSpace"))
            && Integer.valueOf(50000).equals(read(BiomeSearchWorker.class, free, "maxSamples")),
        "Selected native64-spacing50000-sample biome profile");
    set(BiomeSearchWorker.class, free, "yValues", new int[0]);
    Set<BiomeCursor> traversedNorthCursors = new HashSet<>();
    for (int calls = 0; calls < 20000 && current(player) == job && job.radius() == 5000; calls++) {
      BiomeCursor cursor = biomeCursor(free);
      if (cursor.direction() == Direction.NORTH && cursor.length() == 0)
        traversedNorthCursors.add(cursor);
      free.doWork();
    }
    require(
        current(player) == job && job.radius() == 10000,
        "Actual native free traversal reaches paid phase without fabricated callbacks");
    BiomeSearchWorker paid = biomeWorker(job);
    BiomeCursor seed = biomeCursor(paid);
    require(
        traversedNorthCursors.contains(seed),
        "Seed is an exact cursor state actually reached by native free traversal");
    require(
        (seed.x() != job.origin.getX() || seed.z() != job.origin.getZ())
            && CompassSearch.within(job.origin, seed.x(), seed.z(), 5000),
        "Paid traversal starts beyond origin but inside the free circle");
    set(BiomeSearchWorker.class, paid, "yValues", new int[0]);
    boolean reachedFar = false;
    boolean reachedOuterCircle = false;
    for (int calls = 0; calls <= 50000 && current(player) == job; calls++) {
      paid.doWork();
      BiomeCursor cursor = biomeCursor(paid);
      boolean inside = CompassSearch.within(job.origin, cursor.x(), cursor.z(), 10000);
      if (inside && !CompassSearch.within(job.origin, cursor.x(), cursor.z(), 5000))
        reachedFar = true;
      if (inside && !CompassSearch.within(job.origin, cursor.x(), cursor.z(), 9900))
        reachedOuterCircle = true;
      if (!inside)
        require(
            !CompassSearch.permits(job, 1, cursor.x(), cursor.z()),
            "Native outside-circle probe remains rejected");
    }
    require(
        current(player) == null && reachedFar && reachedOuterCircle,
        "Native paid traversal reaches outer circle and terminates within the unchanged cap");
    require(
        (Integer) read(BiomeSearchWorker.class, paid, "samples") < 50000
            && Boolean.FALSE.equals(read(CompassSearch.Job.class, job, "sampleLimitReached")),
        "Default paid traversal ends at radius boundary rather than sample cap");
    require(player.experienceLevel == 2, "No charge for biome traversal without a result");
  }

  private static BiomeSearchWorker biomeWorker(CompassSearch.Job job) {
    Object worker = read(CompassSearch.Job.class, job, "biomeWorker");
    require(worker instanceof BiomeSearchWorker, "Native biome worker exists");
    return (BiomeSearchWorker) worker;
  }

  private static BiomeCursor biomeCursor(BiomeSearchWorker worker) {
    return new BiomeCursor(
        (Integer) read(BiomeSearchWorker.class, worker, "x"),
        (Integer) read(BiomeSearchWorker.class, worker, "z"),
        (Integer) read(BiomeSearchWorker.class, worker, "nextLength"),
        (Integer) read(BiomeSearchWorker.class, worker, "length"),
        (Direction) read(BiomeSearchWorker.class, worker, "direction"));
  }

  private record BiomeCursor(int x, int z, int nextLength, int length, Direction direction) {}

  private static void genericFamily(CompassSearch.Job job, StructureSearchWorker<?> template) {
    StructurePlacement placement =
        (StructurePlacement) read(StructureSearchWorker.class, template, "placement");
    SearchWorkerManager manager = manager(job);
    manager.stop();
    manager.clear();
    GenericSearchWorker worker =
        new GenericSearchWorker(
            job.level,
            job.player,
            job.stack,
            job.origin,
            new ArrayList<>(),
            placement,
            List.of(),
            job.target,
            false,
            "generic-budget-regression");
    ((CompassWorkerBridge) worker).aeroscapes$attach(job, 1);
    try {
      var prepare =
          CompassSearch.class.getDeclaredMethod(
              "prepareExtendedStructureWorker",
              CompassSearch.Job.class,
              StructureSearchWorker.class);
      require(prepare.trySetAccessible(), "Owned phase initializer is accessible to fixture");
      prepare.invoke(null, job, worker);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
      throw new IllegalStateException("Cannot exercise owned generic phase initializer", failure);
    }
    ((CompassManagerBridge) manager).aeroscapes$workers().add(worker);
    worker.start();
    probeFar(job, worker, 10000, "generic");
  }

  private static void concentricFamily(ServerPlayer player) {
    CompassSearch.Job job = request(player, ResourceLocation.parse("minecraft:stronghold"));
    ConcentricRingsSearchWorker worker = rings(job);
    noTargets(worker);
    Object raw = read(ConcentricRingsSearchWorker.class, worker, "potentialChunks");
    require(
        raw instanceof List<?> list && list.size() == 128,
        "Actual default stronghold placement has128 finite candidates");
    for (int count = 0; count < 256 && job.radius() == 5000 && current(player) == job; count++) {
      worker.doWork();
    }
    require(
        current(player) == job && job.radius() == 10000,
        "Actual finite native ring traversal reaches paid phase without artificial fail callback");
    ConcentricRingsSearchWorker extended = rings(job);
    noTargets(extended);
    probeFar(job, extended, 128, "concentric");
    CompassSearch.cancel(player);
  }

  private static void probeFar(
      CompassSearch.Job job, StructureSearchWorker<?> worker, int maximumCalls, String family) {
    StructurePlacement placement =
        (StructurePlacement) read(StructureSearchWorker.class, worker, "placement");
    Object registry = read(CompassSearch.class, null, "JOBS");
    require(registry instanceof Map<?, ?>, "Owned job registry type");
    Map<?, ?> jobs = (Map<?, ?>) registry;
    Field position = field(StructureSearchWorker.class, "currentPos");
    boolean reached = false;
    try {
      for (int calls = 0; calls < maximumCalls && jobs.get(job.player.getUUID()) == job; calls++) {
        worker.doWork();
        Object rawPosition = position.get(worker);
        require(rawPosition instanceof BlockPos, "Native candidate position type");
        BlockPos locate = placement.getLocatePos(new ChunkPos((BlockPos) rawPosition));
        if (!CompassSearch.within(job.origin, locate.getX(), locate.getZ(), 5000)
            && CompassSearch.within(job.origin, locate.getX(), locate.getZ(), 10000)) {
          require(
              CompassSearch.permits(job, 1, locate.getX(), locate.getZ()),
              "Actual paid candidate is accepted within10000");
          require(
              !CompassSearch.permits(job, 1, job.origin.getX() + 10001, job.origin.getZ()),
              "Outside10000 remains rejected");
          require(
              job.level
                      .getChunkSource()
                      .chunkMap
                      .getVisibleChunkIfPresent(new ChunkPos(locate).toLong())
                  == null,
              "Traversal-only far candidate did not load a chunk");
          reached = true;
          break;
        }
      }
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException("Cannot read pinned native cursor", failure);
    }
    require(
        reached && job.player.experienceLevel == 2,
        family + " native cursor reaches paid-distance candidate without charging failed probes");
  }

  private static void nativePriceMaps(ServerPlayer player, ResourceLocation structure) {
    ResourceLocation biome = BiomeUtils.getAllowedBiomeKeys(player.serverLevel()).getFirst();
    var nature = com.chaosthedude.naturescompass.config.ConfigHandler.GENERAL.defaultXpLevels;
    var explorer = com.chaosthedude.explorerscompass.config.ConfigHandler.GENERAL.defaultXpLevels;
    require(
        com.chaosthedude.naturescompass.config.ConfigHandler.GENERAL
                .perBiomeXpLevels
                .get()
                .isEmpty()
            && com.chaosthedude.explorerscompass.config.ConfigHandler.GENERAL
                .xpLevelOverrides
                .get()
                .isEmpty(),
        "Selected profile has no legacy target price overrides");
    int oldNature = nature.get();
    int oldExplorer = explorer.get();
    try {
      nature.set(2);
      explorer.set(2);
      require(
          BiomeUtils.getXpLevelsForBiome(biome) == 0
              && BiomeUtils.getXpLevelsForAllowedBiomes(List.of(biome)).get(biome) == 0,
          "Native biome price getter and GUI sync map stay zero despite legacy config");
      require(
          StructureUtils.getXpLevelsForStructure(player.serverLevel(), structure) == 0
              && StructureUtils.getXpLevelsForAllowedStructures(
                          player.serverLevel(), List.of(structure))
                      .get(structure)
                  == 0,
          "Native structure price getter and GUI sync map stay zero despite legacy config");
    } finally {
      nature.set(oldNature);
      explorer.set(oldExplorer);
    }
  }

  private static CompassSearch.Job request(ServerPlayer player, ResourceLocation target) {
    CompassSearch.cancel(player);
    player.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ExplorersCompass.explorersCompass));
    CompassSearch.request(player, new Request(false, target, false, false, true));
    CompassSearch.Job job = current(player);
    require(job != null && job.radius() == 5000, "Production request starts native free phase");
    return job;
  }

  private static SearchWorkerManager manager(CompassSearch.Job job) {
    Object manager = read(CompassSearch.Job.class, job, "manager");
    require(manager instanceof SearchWorkerManager, "Native manager exists");
    return (SearchWorkerManager) manager;
  }

  private static RandomSpreadSearchWorker spread(CompassSearch.Job job) {
    return ((CompassManagerBridge) manager(job))
        .aeroscapes$workers().stream()
            .filter(worker -> worker instanceof RandomSpreadSearchWorker)
            .map(worker -> (RandomSpreadSearchWorker) worker)
            .findFirst()
            .orElseThrow();
  }

  private static ConcentricRingsSearchWorker rings(CompassSearch.Job job) {
    return ((CompassManagerBridge) manager(job))
        .aeroscapes$workers().stream()
            .filter(worker -> worker instanceof ConcentricRingsSearchWorker)
            .map(worker -> (ConcentricRingsSearchWorker) worker)
            .findFirst()
            .orElseThrow();
  }

  private static void noTargets(StructureSearchWorker<?> worker) {
    set(StructureSearchWorker.class, worker, "structureSet", List.of());
  }

  private static CompassSearch.Job current(ServerPlayer player) {
    Object value = read(CompassSearch.class, null, "JOBS");
    require(value instanceof Map<?, ?>, "Owned registry type");
    Object job = ((Map<?, ?>) value).get(player.getUUID());
    if (job == null) return null;
    require(job instanceof CompassSearch.Job, "Owned job type");
    return (CompassSearch.Job) job;
  }

  private static Object read(Class<?> owner, Object target, String name) {
    try {
      return field(owner, name).get(target);
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException("Cannot read pinned field " + name, failure);
    }
  }

  private static void set(Class<?> owner, Object target, String name, Object value) {
    try {
      field(owner, name).set(target, value);
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException("Cannot set pinned fixture field " + name, failure);
    }
  }

  private static Field field(Class<?> owner, String name) {
    try {
      Field field = owner.getDeclaredField(name);
      require(field.trySetAccessible(), "Pinned fixture field is accessible");
      return field;
    } catch (NoSuchFieldException failure) {
      throw new IllegalStateException("Pinned field changed " + name, failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
