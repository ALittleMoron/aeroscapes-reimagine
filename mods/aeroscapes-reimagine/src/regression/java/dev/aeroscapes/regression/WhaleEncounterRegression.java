package dev.aeroscapes.regression;

import dev.aeroscapes.sky.WhaleCompanionGoal;
import dev.aeroscapes.sky.WhaleEncounters;
import dev.aeroscapes.sky.WhaleHabitats;
import dev.aeroscapes.sky.WhaleLifecycle;
import dev.aeroscapes.sky.WhalePods;
import dev.aeroscapes.sky.WhaleSpawnClearance;
import dev.aeroscapes.sky.WhaleVariantAccess;
import dev.aeroscapes.sky.WhaleVariety;
import dev.dacite.skywhales.config.SWConfig;
import dev.dacite.skywhales.entity.SkyWhale;
import dev.dacite.skywhales.spawn.WhaleSpawner;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Separates placement correctness from the native probabilistic opportunity and population gate.
 */
public final class WhaleEncounterRegression {
  private WhaleEncounterRegression() {}

  public static void verify(ServerLevel level) {
    var center = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 80);
    for (int x = -6; x <= 6; x++) {
      for (int z = -6; z <= 6; z++) {
        level.getChunk((center.getX() >> 4) + x, (center.getZ() >> 4) + z);
      }
    }
    var floor = center.below(4);
    var previous = level.getBlockState(floor);
    long originalTime = level.getGameTime();
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("a7960ec7-d5e3-4224-aab2-902fc67240e7"),
            "WhaleEncounter",
            Vec3.atCenterOf(center),
            null)) {
      session.player.setGameMode(GameType.SURVIVAL);
      level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
      session.player.setYRot(0);
      require(
          WhaleEncounters.trySpawnNear(level, session.player, RandomSource.create(42)),
          "A lower island no longer excludes otherwise clear loaded sky");
      var whale = whales(level, center).getFirst();
      require(whale.getZ() > session.player.getZ(), "Whale appears in the forward view");
      require(WhaleSpawnClearance.clear(level, whale), "Placement clears the complete body");
      require(
          whale.distanceTo(session.player) >= 56,
          "Large whales no longer materialize within the former close ring");
      verifyLifecycle(level, session.player, whale);
      whale.discard();

      level.getServer().getWorldData().overworldData().setGameTime(1);
      WhaleSpawner.onLevelTick(new LevelTickEvent.Post(() -> true, level));
      require(whales(level, center).isEmpty(), "Native interval gate remains active");
      level.getServer().getWorldData().overworldData().setGameTime(0);
      level.getRandom().setSeed(917);
      for (int opportunity = 0; opportunity < 40; opportunity++) {
        WhaleSpawner.onLevelTick(new LevelTickEvent.Post(() -> true, level));
      }
      var natural = whales(level, center);
      require(
          natural.stream()
                  .filter(entity -> !entity.getPersistentData().hasUUID(WhalePods.LEADER))
                  .count()
              == SWConfig.maxNearbyWhales(),
          "Regional chance produces an encounter while the nearby cap stays bounded");
      require(natural.size() <= 3, "A pod adds at most two companions");
      for (var entity : whales(level, center)) entity.discard();
      verifyPods(level, session.player);
      verifyHabitats(level, center);
      session.player.setGameMode(GameType.SPECTATOR);
      for (int opportunity = 0; opportunity < 40; opportunity++) {
        WhaleSpawner.onLevelTick(new LevelTickEvent.Post(() -> true, level));
      }
      require(whales(level, center).isEmpty(), "Spectators do not receive natural whale spawns");
      session.player.setGameMode(GameType.SURVIVAL);
      var unloaded = new BlockPos(1_000_000, center.getY(), 1_000_000);
      require(!level.isLoaded(unloaded), "Far placement fixture starts unloaded");
      var untracked =
          new net.neoforged.neoforge.common.util.FakePlayer(
              level,
              new com.mojang.authlib.GameProfile(
                  UUID.fromString("51b06a8b-40ca-4afd-94ea-73344128b269"), "WhaleUnloaded"));
      untracked.absMoveTo(unloaded.getX(), unloaded.getY(), unloaded.getZ());
      require(
          !WhaleEncounters.trySpawnNear(level, untracked, RandomSource.create(42))
              && !level.isLoaded(unloaded),
          "Failed placements do not request chunks");
      for (int x = -4; x <= 4; x++) {
        for (int z = -4; z <= 4; z++) {
          require(
              !level.isLoaded(unloaded.offset(x * 16, 0, z * 16)),
              "The entire rejected placement area remains unloaded");
        }
      }
      var nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
      require(nether != null, "Native Nether exists for the dimension exclusion");
      var visitor = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(nether);
      require(
          !WhaleEncounters.trySpawnNear(nether, visitor, RandomSource.create(42)),
          "Whales cannot be placed in another dimension");
    } finally {
      for (var entity : whales(level, center)) entity.discard();
      level.setBlockAndUpdate(floor, previous);
      level.getServer().getWorldData().overworldData().setGameTime(originalTime);
    }
  }

  private static void verifyLifecycle(
      ServerLevel level, net.minecraft.server.level.ServerPlayer player, SkyWhale whale) {
    var origin = whale.position();
    whale.moveTo(player.getX() + 100, player.getY(), player.getZ());
    whale.checkDespawn();
    require(!whale.isRemoved(), "Crossing the old 80-block boundary retains a whale");
    whale.moveTo(player.getX() + 160, player.getY(), player.getZ());
    for (int tick = 0; tick < 100; tick++) whale.checkDespawn();
    var variant = (WhaleVariantAccess) whale;
    variant.aeroscapes$variety(WhaleVariety.BASALT);
    var saved = whale.saveWithoutId(new net.minecraft.nbt.CompoundTag());
    var restored = dev.dacite.skywhales.registry.SWEntities.SKY_WHALE.get().create(level);
    require(restored != null, "Native restored whale exists");
    restored.load(saved);
    require(
        ((WhaleVariantAccess) restored).aeroscapes$variety() == WhaleVariety.BASALT,
        "Ecological form survives native save/load");
    for (var form : WhaleVariety.values()) {
      variant.aeroscapes$variety(form);
      restored.load(whale.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
      require(
          ((WhaleVariantAccess) restored).aeroscapes$variety() == form,
          "Every ecological form survives native save/load");
      require(
          WhaleVariety.class.getResource("/assets/aeroscapes_reimagine/whales/material_layout.json")
              != null,
          "Ecological textures have a shipped block-material layout");
      require(
          WhaleVariety.class.getResource(
                  "/assets/" + form.model().getNamespace() + "/" + form.model().getPath())
              != null,
          "Every saved form has a shipped native-rig model");
    }
    require(
        restored.getEntityData().getNonDefaultValues() != null,
        "Variant data enters native entity synchronization");
    require(
        restored.getPersistentData().getInt("aeroscapesWhaleDistantTicks") == 100,
        "Only active distant ticks accrue through save/load");
    restored.moveTo(player.getX(), player.getY(), player.getZ());
    restored.checkDespawn();
    require(
        restored.getPersistentData().getInt("aeroscapesWhaleDistantTicks") == 0,
        "Returning near a whale resets distant grace");
    restored.moveTo(player.getX() + 160, player.getY(), player.getZ());
    for (int tick = 0; tick < WhaleLifecycle.DISTANT_GRACE_TICKS - 1; tick++)
      restored.checkDespawn();
    require(!restored.isRemoved(), "Distant whale remains throughout its two-minute grace");
    restored.checkDespawn();
    require(restored.isRemoved(), "Continuously distant ticking whale eventually leaves");
    whale.moveTo(origin.x, origin.y, origin.z);
    whale.checkDespawn();
  }

  private static void verifyPods(
      ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
    for (int x = -10; x <= 10; x++) {
      for (int z = -10; z <= 10; z++) {
        level.getChunk((player.getBlockX() >> 4) + x, (player.getBlockZ() >> 4) + z);
      }
    }
    var leader = WhaleEncounters.spawnNear(level, player, RandomSource.create(42));
    require(leader != null, "Pod leader uses ordinary encounter placement");
    int companions = WhalePods.addCompanions(level, leader, RandomSource.create(7), 2);
    require(companions >= 1, "Open sky admits pod companions");
    var pod =
        level.getEntitiesOfClass(SkyWhale.class, leader.getBoundingBox().inflate(64)).stream()
            .filter(entity -> entity != leader)
            .toList();
    require(pod.size() == companions, "Every placed companion is present");
    for (var companion : pod) {
      require(WhaleSpawnClearance.clear(level, companion), "Companions clear their whole body");
      require(
          ((WhaleVariantAccess) companion).aeroscapes$variety()
              == ((WhaleVariantAccess) leader).aeroscapes$variety(),
          "A pod shares one ecological form");
      require(WhalePods.leader(level, companion) == leader, "Companions know their leader");
      require(
          companion.distanceTo(leader) >= WhalePods.MINIMUM_SIDE - 1.0,
          "Companions keep a whale's width apart");
    }
    var companion = pod.getFirst();
    companion.moveTo(leader.getX() + 70, leader.getY(), leader.getZ());
    var goal = new WhaleCompanionGoal(companion);
    require(goal.canUse(), "A distant companion returns to its leader");
    goal.start();
    goal.tick();
    require(
        companion.getMoveControl().hasWanted()
            && companion.getMoveControl().getWantedX() < companion.getX(),
        "The companion steers back toward its leader");
    companion.moveTo(leader.getX() + 20, leader.getY(), leader.getZ());
    require(!goal.canContinueToUse(), "A close companion leaves wandering to native drift");
    leader.discard();
    require(!new WhaleCompanionGoal(companion).canUse(), "Without a leader, no following");
    for (var entity : pod) entity.discard();
  }

  private static void verifyHabitats(ServerLevel level, BlockPos center) {
    var position = center.below(40);
    var previous =
        new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
    try {
      for (int x = -2; x <= 2; x++) {
        for (int z = -2; z <= 2; z++) {
          var block = position.offset(x, 0, z);
          previous.put(block, level.getBlockState(block));
          previous.put(block.above(), level.getBlockState(block.above()));
          level.setBlockAndUpdate(block, Blocks.LAVA.defaultBlockState());
          level.setBlockAndUpdate(block.above(), Blocks.AIR.defaultBlockState());
        }
      }
      require(WhaleHabitats.lavaLake(level, position), "A real source-lava lake is recognized");
      require(
          WhaleHabitats.at(level, position.above(20)) == WhaleVariety.LAVA,
          "Lava ecology requires the lake below its placement");
      level.setBlockAndUpdate(position.offset(2, 0, 2), Blocks.STONE.defaultBlockState());
      require(!WhaleHabitats.lavaLake(level, position), "A partial patch is not a lava lake");
      require(
          WhaleHabitats.at(level, center.atY(330)) == WhaleVariety.CLOUD,
          "Highest sky has cloud ecology");
    } finally {
      previous.forEach(level::setBlockAndUpdate);
    }
  }

  private static java.util.List<SkyWhale> whales(ServerLevel level, BlockPos center) {
    return level.getEntitiesOfClass(SkyWhale.class, new AABB(center).inflate(112));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
