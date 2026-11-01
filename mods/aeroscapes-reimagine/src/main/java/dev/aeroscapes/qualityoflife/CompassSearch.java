package dev.aeroscapes.qualityoflife;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.chaosthedude.explorerscompass.worker.GenericSearchWorker;
import com.chaosthedude.explorerscompass.worker.SearchWorkerManager;
import com.chaosthedude.explorerscompass.worker.StructureSearchWorker;
import com.chaosthedude.naturescompass.NaturesCompass;
import com.chaosthedude.naturescompass.items.NaturesCompassItem;
import com.chaosthedude.naturescompass.util.BiomeSearchWorker;
import com.chaosthedude.naturescompass.util.BiomeUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Server-thread policy for the exact3.4.0 compass workers. */
public final class CompassSearch {
  public static final int FREE_RADIUS = 5000;
  public static final int EXTENDED_RADIUS = 10000;
  public static final int FAR_COST_LEVELS = 1;
  private static final Map<UUID, Job> JOBS = new HashMap<>();

  private CompassSearch() {}

  public static void request(ServerPlayer player, CompassSearchNetwork.Request request) {
    if (!player.serverLevel().getServer().isSameThread()) {
      throw new IllegalStateException("Compass requests must run on the server thread");
    }
    ItemStack stack = heldCompass(player, request.biome());
    if (stack.isEmpty()) return;
    if (request.extended() && !affordable(player)) {
      message(player, "insufficient", FAR_COST_LEVELS);
      return;
    }
    ResourceLocation target = request.target();
    boolean group = request.group();
    List<BlockPos> previous = List.of();
    List<Structure> structures = List.of();
    if (request.biome()) {
      if (group || NaturesCompass.naturesCompass.isBroken(stack)) return;
      if (request.next()) {
        if (NaturesCompass.naturesCompass.getState(stack)
            != com.chaosthedude.naturescompass.util.CompassState.FOUND) return;
        List<BlockPos> stored = stack.get(NaturesCompass.PREV_POS);
        String storedKey = stack.get(NaturesCompass.BIOME_ID);
        if (stored == null || stored.isEmpty() || storedKey == null) return;
        int limit =
            com.chaosthedude.naturescompass.config.ConfigHandler.GENERAL.maxNextSearches.get();
        if (limit <= 0 || stored.size() > limit) return;
        target = ResourceLocation.tryParse(storedKey);
        previous = List.copyOf(stored);
      }
      if (target == null || !BiomeUtils.getAllowedBiomeKeys(player.level()).contains(target))
        return;
    } else {
      if (ExplorersCompass.explorersCompass.isBroken(stack)) return;
      if (!player.getServer().getWorldData().worldGenOptions().generateStructures()) {
        message(player, "not_found");
        return;
      }
      if (request.next()) {
        if (ExplorersCompass.explorersCompass.getState(stack)
            != com.chaosthedude.explorerscompass.util.CompassState.FOUND) return;
        List<BlockPos> stored = ExplorersCompass.explorersCompass.getPrevPos(stack);
        if (stored == null || stored.isEmpty()) return;
        int limit =
            com.chaosthedude.explorerscompass.config.ConfigHandler.GENERAL.maxNextSearches.get();
        if (limit <= 0 || stored.size() > limit) return;
        String storedKey = stack.get(ExplorersCompass.STRUCTURE_ID_COMPONENT);
        if (storedKey == null) return;
        target = ResourceLocation.tryParse(storedKey);
        if (target == null) return;
        previous = List.copyOf(stored);
        group = ExplorersCompass.explorersCompass.getIsGroup(stack);
        if (group) {
          Structure found = StructureUtils.getStructureForKey(player.serverLevel(), target);
          if (found == null) return;
          target = StructureUtils.getTypeForStructure(player.serverLevel(), found);
        }
      }
      List<ResourceLocation> allowed = StructureUtils.getAllowedStructureKeys(player.serverLevel());
      List<ResourceLocation> keys =
          group
              ? new ArrayList<>(
                  StructureUtils.getTypeKeysToStructureKeys(player.serverLevel()).get(target))
              : List.of(target);
      structures =
          keys.stream()
              .filter(allowed::contains)
              .distinct()
              .map(key -> StructureUtils.getStructureForKey(player.serverLevel(), key))
              .filter(java.util.Objects::nonNull)
              .toList();
      if (structures.isEmpty()) return;
    }
    Job existing = JOBS.get(player.getUUID());
    if (existing != null) cancel(existing);
    Job job =
        new Job(
            player,
            stack,
            request.biome(),
            target,
            group,
            request.extended(),
            previous,
            structures);
    JOBS.put(player.getUUID(), job);
    if (job.biome) {
      NaturesCompass.naturesCompass.setSearching(stack, target, player);
      stack.set(NaturesCompass.SAMPLES, 0);
      stack.set(NaturesCompass.SEARCH_RADIUS, 0);
    } else {
      ExplorersCompass.explorersCompass.setSearching(stack, target, group, player);
      ExplorersCompass.explorersCompass.setSearchRadius(stack, 0, player);
    }
    startPhase(job);
  }

  private static ItemStack heldCompass(ServerPlayer player, boolean biome) {
    for (var hand : net.minecraft.world.InteractionHand.values()) {
      ItemStack stack = player.getItemInHand(hand);
      if (stack.getItem()
          == (biome ? NaturesCompass.naturesCompass : ExplorersCompass.explorersCompass)) {
        return stack;
      }
    }
    return ItemStack.EMPTY;
  }

  public static void cancel(ServerPlayer player) {
    Job job = JOBS.get(player.getUUID());
    if (job != null) cancel(job);
  }

  public static void reconcile(ServerPlayer player) {
    Job job = JOBS.get(player.getUUID());
    if (job != null && !current(job, job.phase)) job = null;
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      resetOrphanedStack(player, player.getInventory().getItem(slot), job);
    }
    resetOrphanedStack(player, player.containerMenu.getCarried(), job);
  }

  private static void resetOrphanedStack(ServerPlayer player, ItemStack stack, Job job) {
    if (stack.isEmpty() || (job != null && job.stack == stack)) return;
    // Slot pickup copies stacks. A copied searching state must not inherit a job or stay stuck.
    if (stack.getItem() == NaturesCompass.naturesCompass
        && NaturesCompass.naturesCompass.getState(stack)
            == com.chaosthedude.naturescompass.util.CompassState.SEARCHING) {
      NaturesCompass.naturesCompass.setInactive(stack, player);
    } else if (stack.getItem() == ExplorersCompass.explorersCompass
        && ExplorersCompass.explorersCompass.getState(stack)
            == com.chaosthedude.explorerscompass.util.CompassState.SEARCHING) {
      ExplorersCompass.explorersCompass.setInactive(stack, player);
    }
  }

  public static void clear() {
    for (Job job : new ArrayList<>(JOBS.values())) cancel(job);
  }

  private static void cancel(Job job) {
    boolean owned = stillOwned(job);
    close(job);
    if (!owned) return;
    if (job.biome) NaturesCompass.naturesCompass.setInactive(job.stack, job.player);
    else ExplorersCompass.explorersCompass.setInactive(job.stack, job.player);
  }

  public static int displayRadius(Player player, ItemStack stack, int radius) {
    if (player instanceof ServerPlayer serverPlayer) {
      Job job = JOBS.get(serverPlayer.getUUID());
      if (job != null && job.stack == stack && current(job, job.phase)) {
        return Math.min(Math.max(0, radius), job.radius());
      }
    }
    return radius;
  }

  public static boolean current(Job job, int phase) {
    if (job.closed || JOBS.get(job.player.getUUID()) != job || job.phase != phase) return false;
    if (job.player.level() != job.level
        || !job.player.isAlive()
        || job.level.getServer().getPlayerList().getPlayer(job.player.getUUID()) != job.player
        || !stillOwned(job)) {
      cancel(job);
      return false;
    }
    return true;
  }

  private static boolean stillOwned(Job job) {
    if (job.stack.isEmpty()
        || job.stack.getItem()
            != (job.biome ? NaturesCompass.naturesCompass : ExplorersCompass.explorersCompass))
      return false;
    for (int slot = 0; slot < job.player.getInventory().getContainerSize(); slot++) {
      if (job.player.getInventory().getItem(slot) == job.stack) return true;
    }
    return false;
  }

  public static boolean within(BlockPos origin, int x, int z, int radius) {
    long deltaX = (long) x - origin.getX();
    long deltaZ = (long) z - origin.getZ();
    if (Math.abs(deltaX) > radius || Math.abs(deltaZ) > radius) return false;
    return deltaX * deltaX + deltaZ * deltaZ <= (long) radius * radius;
  }

  public static boolean permits(Job job, int phase, int x, int z) {
    return current(job, phase) && within(job.origin, x, z, job.radius());
  }

  private static boolean affordable(ServerPlayer player) {
    return player.hasInfiniteMaterials() || player.experienceLevel >= FAR_COST_LEVELS;
  }

  private static void startPhase(Job job) {
    int phase = job.phase;
    if (!current(job, phase)) return;
    if (job.biome) {
      job.biomeWorker =
          new BiomeSearchWorker(
              job.level,
              job.player,
              job.stack,
              job.target,
              job.origin,
              new ArrayList<>(job.previous));
      ((CompassWorkerBridge) job.biomeWorker).aeroscapes$attach(job, phase);
      job.biomeWorker.start();
    } else {
      job.manager = new SearchWorkerManager();
      job.manager.createWorkers(
          job.level,
          job.player,
          job.stack,
          job.structures,
          job.target,
          job.group,
          job.origin,
          new ArrayList<>(job.previous));
      for (StructureSearchWorker<?> worker :
          ((CompassManagerBridge) job.manager).aeroscapes$workers()) {
        ((CompassWorkerBridge) worker).aeroscapes$attach(job, phase);
        if (phase == 1) prepareExtendedStructureWorker(job, worker);
      }
      if (!job.manager.start()) phaseFailed(job, phase);
    }
  }

  private static void prepareExtendedStructureWorker(Job job, StructureSearchWorker<?> worker) {
    if (worker instanceof CompassRandomSpreadBridge spread) {
      int ring = firstExtendedRing(Math.multiplyExact(spread.aeroscapes$spacing(), 16));
      spread.aeroscapes$ring(ring);
      spread.aeroscapes$cellX(-ring);
      spread.aeroscapes$cellZ(-ring);
    } else if (worker instanceof GenericSearchWorker generic) {
      int ring = firstExtendedRing(16);
      if (ring == 0) return;
      generic.chunkX = (job.origin.getX() >> 4) - ring + 1;
      generic.chunkZ = (job.origin.getZ() >> 4) + ring - 1;
      generic.length = 0;
      generic.nextLength = 2.0 * ring - 1.0;
      generic.direction = Direction.NORTH;
    }
    // Concentric workers enumerate their finite candidate list, not an expanding grid.
  }

  private static int firstExtendedRing(int cellBlocks) {
    if (cellBlocks < 16) throw new IllegalArgumentException("Invalid structure placement spacing");
    // Square corners reach the paid circle first; two cells cover jitter and locate offsets.
    return Math.max(0, (int) Math.floor(FREE_RADIUS / (Math.sqrt(2.0) * cellBlocks)) - 2);
  }

  public static void biomeFailed(Job job, int phase, boolean exhausted) {
    if (!current(job, phase)) return;
    if (exhausted) job.sampleLimitReached = true;
    phaseFailed(job, phase);
  }

  public static void structureFailed(Job job, int phase, boolean exhausted) {
    if (!current(job, phase)) return;
    if (exhausted) job.sampleLimitReached = true;
    job.manager.pop();
    if (!job.manager.start()) phaseFailed(job, phase);
  }

  private static void phaseFailed(Job job, int phase) {
    if (!current(job, phase)) return;
    stopWorkers(job);
    if (phase == 0 && job.extended) {
      if (!affordable(job.player)) {
        finishFailure(job, "insufficient", FAR_COST_LEVELS);
        return;
      }
      job.phase = 1;
      startPhase(job);
    } else {
      finishFailure(job, job.sampleLimitReached ? "sample_limit" : "not_found");
    }
  }

  public static void biomeFound(
      Job job, int phase, int x, int z, List<BlockPos> previous, int samples) {
    if (!prepareResult(job, phase, x, z)) return;
    NaturesCompassItem item = NaturesCompass.naturesCompass;
    item.setFound(job.stack, x, z, samples, job.player);
    item.setDisplayCoordinates(
        job.stack,
        com.chaosthedude.naturescompass.config.ConfigHandler.GENERAL.displayCoordinates.get());
    job.stack.set(NaturesCompass.PREV_POS, List.copyOf(previous));
    int durability =
        com.chaosthedude.naturescompass.config.ConfigHandler.GENERAL.compassDurability.get();
    if (durability > 0)
      job.stack.set(NaturesCompass.DAMAGE, job.stack.getOrDefault(NaturesCompass.DAMAGE, 0) + 1);
  }

  public static void structureFound(
      Job job, int phase, BlockPos pos, Structure structure, List<BlockPos> previous, int samples) {
    if (!prepareResult(job, phase, pos.getX(), pos.getZ())) return;
    ExplorersCompassItem item = ExplorersCompass.explorersCompass;
    item.setFound(
        job.stack,
        StructureUtils.getKeyForStructure(job.level, structure),
        job.group,
        pos.getX(),
        pos.getZ(),
        samples);
    item.setDisplayCoordinates(
        job.stack,
        com.chaosthedude.explorerscompass.config.ConfigHandler.GENERAL.displayCoordinates.get());
    job.stack.set(ExplorersCompass.PREV_POS_COMPONENT, List.copyOf(previous));
    int durability =
        com.chaosthedude.explorerscompass.config.ConfigHandler.GENERAL.compassDurability.get();
    if (durability > 0)
      job.stack.set(ExplorersCompass.DAMAGE_COMPONENT, item.getDamage(job.stack) + 1);
  }

  private static boolean prepareResult(Job job, int phase, int x, int z) {
    if (!current(job, phase)) return false;
    if (!within(job.origin, x, z, job.radius())) {
      finishFailure(job, "not_found");
      return false;
    }
    boolean far = !within(job.origin, x, z, FREE_RADIUS);
    if (far && (!job.extended || phase != 1)) {
      finishFailure(job, "not_found");
      return false;
    }
    if (far && !affordable(job.player)) {
      finishFailure(job, "insufficient", FAR_COST_LEVELS);
      return false;
    }
    // Close first: duplicate/reentrant worker callbacks cannot charge the same request twice.
    close(job);
    if (far && !job.player.hasInfiniteMaterials()) {
      job.player.giveExperienceLevels(-FAR_COST_LEVELS);
      message(job.player, "charged", FAR_COST_LEVELS);
    }
    return true;
  }

  private static void finishFailure(Job job, String reason, Object... arguments) {
    int radius = job.radius();
    close(job);
    if (job.biome) NaturesCompass.naturesCompass.setNotFound(job.stack, job.player, radius, 0);
    else ExplorersCompass.explorersCompass.setNotFound(job.stack, radius, 0);
    message(job.player, reason, arguments);
  }

  private static void close(Job job) {
    job.closed = true;
    JOBS.remove(job.player.getUUID(), job);
    stopWorkers(job);
  }

  private static void stopWorkers(Job job) {
    if (job.biomeWorker != null) job.biomeWorker.stop();
    if (job.manager != null) {
      job.manager.stop();
      job.manager.clear();
    }
  }

  private static void message(ServerPlayer player, String key, Object... arguments) {
    player.sendSystemMessage(
        Component.translatable("aeroscapes_reimagine.compass." + key, arguments));
  }

  public static final class Job {
    public final ServerPlayer player;
    public final ServerLevel level;
    public final ItemStack stack;
    public final BlockPos origin;
    public final boolean biome;
    public final ResourceLocation target;
    public final boolean group;
    public final boolean extended;
    public final List<BlockPos> previous;
    public final List<Structure> structures;
    private int phase;
    private boolean closed;
    private boolean sampleLimitReached;
    private BiomeSearchWorker biomeWorker;
    private SearchWorkerManager manager;

    private Job(
        ServerPlayer player,
        ItemStack stack,
        boolean biome,
        ResourceLocation target,
        boolean group,
        boolean extended,
        List<BlockPos> previous,
        List<Structure> structures) {
      this.player = player;
      this.level = player.serverLevel();
      this.stack = stack;
      this.origin = player.blockPosition().immutable();
      this.biome = biome;
      this.target = target;
      this.group = group;
      this.extended = extended;
      this.previous = previous;
      this.structures = structures;
    }

    public int radius() {
      return phase == 0 ? FREE_RADIUS : EXTENDED_RADIUS;
    }
  }
}
