package dev.aeroscapes.sky.fauna;

import dev.aeroscapes.sky.WhaleEncounters;
import dev.dacite.skywhales.entity.SkyWhale;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

public final class FaunaEncounters {
  private static final ResourceKey<Biome> VOID =
      ResourceKey.create(Registries.BIOME, ResourceLocation.parse("aeroscapes_reimagine:void"));
  private static final int ENCOUNTER_INTERVAL = 600;
  private static final int PLACEMENT_ATTEMPTS = 3;

  private FaunaEncounters() {}

  public static void tick(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel level)
        || level.getGameTime() % ENCOUNTER_INTERVAL != ENCOUNTER_INTERVAL / 2
        || !enabled(level)
        || level.players().isEmpty()) return;
    var random = level.getRandom();
    int roll = random.nextInt(8);
    if (roll > 1) return;
    var player = level.players().get(random.nextInt(level.players().size()));
    spawnNear(level, player, random, roll == 1);
  }

  private static boolean enabled(ServerLevel level) {
    return level.dimension() == Level.OVERWORLD
        && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
        && level.getServer().isSpawningAnimals();
  }

  public static int spawnNear(
      ServerLevel level, ServerPlayer player, RandomSource random, boolean colony) {
    if (!enabled(level) || !player.isAlive() || player.isSpectator()) return 0;
    Vec3 observer = Sable.HELPER.projectOutOfSubLevel(level, player.position());
    Vec3 aim =
        Sable.HELPER
            .projectOutOfSubLevel(
                level, player.position().add(Vec3.directionFromRotation(0, player.getYRot())))
            .subtract(observer);
    float observerYaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
    var players = level.getServer().getPlayerList();
    int chunks =
        Math.min(
            Math.min(players.getSimulationDistance(), players.getViewDistance()),
            player.requestedViewDistance());
    int maximumDistance = Math.clamp(chunks * 16 - 8, 30, 70);
    int minimumDistance = Math.max(26, maximumDistance - 18);
    Class<? extends Mob> species = colony ? CloudJelly.class : SkyRay.class;
    if (!level.getEntitiesOfClass(species, new AABB(observer, observer).inflate(112)).isEmpty())
      return 0;
    for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
      double angle = WhaleEncounters.angle(random.nextDouble() * Math.TAU, observerYaw);
      double distance = minimumDistance + random.nextDouble() * (maximumDistance - minimumDistance);
      Vec3 center =
          observer.add(
              Math.cos(angle) * distance, 12 + random.nextInt(13), Math.sin(angle) * distance);
      if (!level.getEntitiesOfClass(species, new AABB(center, center).inflate(96)).isEmpty())
        continue;
      float heading = random.nextFloat() * 360;
      int placed = colony ? colony(level, center, heading, random) : ray(level, center, heading);
      if (placed > 0) return placed;
    }
    return 0;
  }

  private static int ray(ServerLevel level, Vec3 center, float heading) {
    var ray = AeroFauna.SKY_RAY.get().create(level);
    if (ray == null) return 0;
    ray.moveTo(center.x, center.y, center.z, heading, 0);
    return site(level, ray.clearanceBounds()) && add(level, ray) ? 1 : 0;
  }

  private static int colony(ServerLevel level, Vec3 center, float heading, RandomSource random) {
    // Each colony has a larger bell with small companions; giants occur in one in 1,000 rolls.
    int roll = random.nextInt(1000);
    float diameter =
        roll == 0
            ? 12 + random.nextFloat() * 4
            : roll <= 80 ? 6 + random.nextFloat() * 3 : 3 + random.nextFloat() * 2;
    var larger = jelly(level, center, heading, diameter, random);
    if (larger == null || !site(level, larger.clearanceBounds().minmax(larger.getBoundingBox())))
      return 0;
    var small = companion(level, center, heading, random, larger);
    if (small == null || !add(level, larger)) return 0;
    if (!add(level, small)) {
      larger.discard();
      return 0;
    }
    int target = 3 + random.nextInt(3);
    int placed = 2;
    for (int index = 2; index < target; index++) {
      var member = companion(level, center, heading, random, larger);
      if (member != null && add(level, member)) placed++;
    }
    return placed;
  }

  private static @Nullable CloudJelly companion(
      ServerLevel level, Vec3 center, float heading, RandomSource random, CloudJelly larger) {
    for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
      double angle = random.nextDouble() * Math.TAU;
      double distance = 14 + random.nextDouble() * 4;
      var bell =
          center.add(Math.cos(angle) * distance, random.nextInt(7) - 3, Math.sin(angle) * distance);
      var small = jelly(level, bell, heading, 1 + random.nextFloat(), random);
      if (small != null) {
        var bounds = small.clearanceBounds().minmax(small.getBoundingBox());
        if (!bounds.intersects(larger.clearanceBounds().minmax(larger.getBoundingBox()))
            && site(level, bounds)) return small;
      }
    }
    return null;
  }

  private static @Nullable CloudJelly jelly(
      ServerLevel level, Vec3 bell, float heading, float diameter, RandomSource random) {
    var jelly = AeroFauna.CLOUD_JELLY.get().create(level);
    if (jelly != null) {
      jelly.setForm(net.minecraft.Util.getRandom(CloudJellyAnatomy.Form.values(), random));
      jelly.initSize(diameter);
      jelly.moveTo(bell.x, bell.y - jelly.getBbHeight(), bell.z, heading, 0);
      jelly.setDriftHeading(heading);
    }
    return jelly;
  }

  private static boolean add(ServerLevel level, Mob mob) {
    if (!EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) return false;
    EventHooks.finalizeMobSpawn(
        mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.NATURAL, null);
    return level.addFreshEntity(mob);
  }

  private static boolean site(ServerLevel level, AABB bounds) {
    var center = BlockPos.containing(bounds.getCenter());
    var chunk = level.getChunkSource().getChunkNow(center.getX() >> 4, center.getZ() >> 4);
    if (chunk == null
        || !level.isNaturalSpawningAllowed(center)
        || !level.areEntitiesLoaded(ChunkPos.asLong(center))) return false;
    // The empty biome includes clear air between stacked islands, beneath the column's heightmap.
    if (chunk.getHeight(Heightmap.Types.WORLD_SURFACE, center.getX(), center.getZ())
            > bounds.minY - 12
        && !chunk
            .getNoiseBiome(center.getX() >> 2, center.getY() >> 2, center.getZ() >> 2)
            .is(VOID)) return false;
    return clear(level, bounds)
        && level.getEntitiesOfClass(SkyWhale.class, bounds.inflate(24)).isEmpty()
        && level.getEntitiesOfClass(CloudJelly.class, bounds.inflate(42)).stream()
            .noneMatch(jelly -> jelly.clearanceBounds().inflate(2).intersects(bounds))
        && level.getEntitiesOfClass(SkyRay.class, bounds.inflate(8)).isEmpty()
        && level.players().stream()
            .filter(player -> player.isAlive() && !player.isSpectator())
            .noneMatch(
                player ->
                    bounds.distanceToSqr(
                            Sable.HELPER.projectOutOfSubLevel(level, player.position()))
                        < 24.0 * 24.0);
  }

  /**
   * Includes the space around the visible animal and the neighbor chunks used by collision probes.
   */
  public static boolean clear(ServerLevel level, AABB body) {
    AABB bounds = body.inflate(0.5);
    if (bounds.minY < level.getMinBuildHeight()
        || bounds.maxY > level.getMaxBuildHeight()
        || !level.getWorldBorder().isWithinBounds(bounds)) return false;
    for (int x = SectionPos.blockToSectionCoord(Mth.floor(bounds.minX) - 1);
        x <= SectionPos.blockToSectionCoord(Mth.ceil(bounds.maxX) + 1);
        x++) {
      for (int z = SectionPos.blockToSectionCoord(Mth.floor(bounds.minZ) - 1);
          z <= SectionPos.blockToSectionCoord(Mth.ceil(bounds.maxZ) + 1);
          z++) {
        if (level.getChunkSource().getChunkNow(x, z) == null) return false;
      }
    }
    var ships = SubLevelContainer.getContainer(level);
    return (ships == null
            || !ships.queryIntersecting(new BoundingBox3d(bounds)).iterator().hasNext())
        && level.noCollision(bounds)
        && !level.containsAnyLiquid(bounds);
  }
}
