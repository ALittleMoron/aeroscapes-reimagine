package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandLayout;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Native marine candidates must retain actual wet structures and their native rewards. */
public final class MarineBasinRegression {
  private MarineBasinRegression() {}

  public static void verify(ServerLevel level) {
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    require(terrain != null, "Missing basin terrain for marine regression");
    if (terrain == null) throw new IllegalStateException("Missing terrain");
    natural(level, terrain, "supplementaries:galleon");
    natural(level, terrain, "minecraft:shipwreck");
    natural(level, terrain, "minecraft:ocean_ruin_cold");
    natural(level, terrain, "minecraft:ocean_ruin_warm");
  }

  private static void natural(ServerLevel level, IslandTerrain terrain, String id) {
    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    Structure structure = registry.get(ResourceLocation.parse(id));
    require(structure != null, "Missing marine structure " + id);
    if (structure == null) throw new IllegalStateException("Missing structure");
    var state = level.getChunkSource().getGeneratorState();
    var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
    long deadline = System.nanoTime() + 60_000_000_000L;
    int checked = 0;
    for (var placement : state.getPlacementsForStructure(registry.wrapAsHolder(structure))) {
      if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
      for (int radius = 0; radius <= 64; radius++) {
        for (int regionX = -radius; regionX <= radius; regionX++) {
          for (int regionZ = -radius; regionZ <= radius; regionZ++) {
            if (Math.max(Math.abs(regionX), Math.abs(regionZ)) != radius) continue;
            require(
                System.nanoTime() < deadline, "Marine candidate search exceeded 60 seconds " + id);
            ChunkPos chunk =
                spread.getPotentialStructureChunk(
                    level.getSeed(), regionX * spread.spacing(), regionZ * spread.spacing());
            int x = chunk.getMiddleBlockX();
            int z = chunk.getMiddleBlockZ();
            IslandLayout.Basin basin = terrain.basin(x, z);
            if (basin == null || basin.radius(x, z) > 0.5) continue;
            if (!generator
                    .getBaseColumn(x, z, level, level.getChunkSource().randomState())
                    .getBlock(basin.waterline())
                    .is(Blocks.WATER)
                || generator.getBaseHeight(
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level,
                        level.getChunkSource().randomState())
                    > basin.waterline() + 1) continue;
            if (!spread.isStructureChunk(state, chunk.x, chunk.z)) continue;
            checked++;
            var context =
                new Structure.GenerationContext(
                    level.registryAccess(),
                    generator,
                    generator.getBiomeSource(),
                    level.getChunkSource().randomState(),
                    level.getStructureManager(),
                    level.getSeed(),
                    chunk,
                    level,
                    structure.biomes()::contains);
            if (structure.findValidGenerationPoint(context).isEmpty()) continue;
            StructureStart start = level.getChunk(chunk.x, chunk.z).getStartForStructure(structure);
            if (start == null || !start.isValid()) continue;
            contents(level, start, id, basin);
            if (id.equals("supplementaries:galleon")) wildlife(level, basin);
            LogUtils.getLogger()
                .info(
                    "Marine natural FULL {} chunk={} pieces={} checked={} localWaterline={}",
                    id,
                    chunk,
                    start.getPieces().size(),
                    checked,
                    basin.waterline());
            return;
          }
        }
      }
    }
    throw new IllegalStateException("No naturally selected wet " + id + "; checked=" + checked);
  }

  private static void contents(
      ServerLevel level, StructureStart start, String id, IslandLayout.Basin basin) {
    var bounds = start.getBoundingBox();
    require(
        bounds.minY() >= level.getMinBuildHeight() && bounds.maxY() < level.getMaxBuildHeight(),
        "Marine structure clipped by build height " + id);
    for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
      for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) level.getChunk(x, z);
    int water = 0;
    int loot = 0;
    int archaeology = 0;
    int cannons = 0;
    String expectedLoot =
        id.equals("supplementaries:galleon")
            ? "supplementaries:loot/galleon/"
            : id.equals("minecraft:shipwreck")
                ? "minecraft:chests/shipwreck_"
                : "minecraft:chests/underwater_ruin_";
    // StructureStart caches its earlier box; native template pieces may have moved vertically.
    bounds =
        BoundingBox.encapsulatingBoxes(
                start.getPieces().stream().map(piece -> piece.getBoundingBox()).toList())
            .orElseThrow();
    ResourceLocation cannon = ResourceLocation.parse("supplementaries:cannon");
    for (BlockPos position :
        BlockPos.betweenClosed(
            bounds.minX(),
            bounds.minY(),
            bounds.minZ(),
            bounds.maxX(),
            bounds.maxY(),
            bounds.maxZ())) {
      var block = level.getBlockState(position);
      if (block.is(Blocks.WATER)) water++;
      if (block.is(Blocks.SUSPICIOUS_GRAVEL) || block.is(Blocks.SUSPICIOUS_SAND)) archaeology++;
      if (BuiltInRegistries.BLOCK.getKey(block.getBlock()).equals(cannon)) cannons++;
      if (level.getBlockEntity(position) instanceof RandomizableContainerBlockEntity container
          && container.getLootTable() != null
          && container.getLootTable().location().toString().startsWith(expectedLoot)) loot++;
    }
    require(
        water > 20 && loot > 0, "Missing wet marine contents " + id + ": " + water + "/" + loot);
    if (id.equals("supplementaries:galleon")) {
      var root = start.getPieces().getFirst().getBoundingBox();
      require(
          root.minY() <= basin.waterline() && root.minY() >= basin.waterline() - 8,
          "Galleon has not retained its native water draft");
      require(cannons > 0, "Galleon lost its native cannons");
    } else if (id.startsWith("minecraft:ocean_ruin")) {
      require(archaeology > 0, "Wet ruin lost native archaeology");
    }
    LogUtils.getLogger()
        .info(
            "Marine contents {} water={} loot={} archaeology={} cannons={}",
            id,
            water,
            loot,
            archaeology,
            cannons);
  }

  private static void wildlife(ServerLevel level, IslandLayout.Basin basin) {
    BlockPos deep = new BlockPos(basin.centerX(), basin.waterline() - 24, basin.centerZ());
    level.getChunk(deep.getX() >> 4, deep.getZ() >> 4);
    require(
        level.getBlockState(deep).is(Blocks.WATER), "Drowned control is not actual basin water");
    boolean drowned = false;
    RandomSource random = RandomSource.create(1);
    for (int attempt = 0; attempt < 1024 && !drowned; attempt++)
      drowned =
          Drowned.checkDrownedSpawnRules(
              EntityType.DROWNED, level, MobSpawnType.NATURAL, deep, random);
    require(drowned, "Native ocean drowned cannot spawn in local deep water");
    drownedSwimming(level, deep, basin.waterline());
    BlockPos glow = deep.below(16);
    require(
        GlowSquid.checkGlowSquidSpawnRules(
            EntityType.GLOW_SQUID, level, MobSpawnType.NATURAL, glow, random),
        "Native glow squid cannot spawn in local dark deep water");
    Set<Long> checkedShoreChunks = new HashSet<>();
    for (int angle = 0; angle < 360; angle += 15) {
      double radians = Math.toRadians(angle);
      for (int step = 82; step <= 100; step++) {
        int x = basin.centerX() + (int) (Math.cos(radians) * basin.radiusX() * step / 100);
        int z = basin.centerZ() + (int) (Math.sin(radians) * basin.radiusZ() * step / 100);
        double naturalFloor = basin.floor(x, z);
        if (naturalFloor < basin.waterline() - 1 || naturalFloor >= basin.waterline() + 4) continue;
        if (!checkedShoreChunks.add(ChunkPos.asLong(x >> 4, z >> 4))) continue;
        BlockPos shore = turtleShore(level, basin, new ChunkPos(x >> 4, z >> 4), random);
        if (shore != null) {
          LogUtils.getLogger()
              .info(
                  "Marine native drowned={} and turtle={} predicates passed; shoreChunks={}",
                  deep,
                  shore,
                  checkedShoreChunks.size());
          return;
        }
      }
    }
    throw new IllegalStateException("No native sandy local shore for turtle spawn predicate");
  }

  private static BlockPos turtleShore(
      ServerLevel level, IslandLayout.Basin basin, ChunkPos chunk, RandomSource random) {
    level.getChunk(chunk.x, chunk.z);
    // Retained basalt features can leave narrow sandy gaps between the sparse
    // geometry probes. Inspect the contiguous footprint of each already requested
    // shore chunk rather than assuming every probe itself must support turtles.
    for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
      for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
        if (basin.radius(x, z) >= 1.1) continue;
        double floor = basin.floor(x, z);
        if (floor < basin.waterline() - 1 || floor >= basin.waterline() + 4) continue;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y < basin.waterline() || y >= basin.waterline() + 4) continue;
        BlockPos shore = new BlockPos(x, y, z);
        if (level.getBiome(shore).is(Biomes.BEACH)
            && Turtle.checkTurtleSpawnRules(
                EntityType.TURTLE, level, MobSpawnType.NATURAL, shore, random)) return shore;
      }
    }
    return null;
  }

  private static void drownedSwimming(ServerLevel level, BlockPos deep, int waterline) {
    // Exercise the actual native goal without changing global daylight or running
    // a whole entity tick. Only its navigation request is captured in this fixture.
    NavigationDrowned drowned = new NavigationDrowned(level);
    var goal =
        drowned.goalSelector.getAvailableGoals().stream()
            .map(wrapped -> wrapped.getGoal())
            .filter(value -> value.getClass().getName().endsWith("$DrownedSwimUpGoal"))
            .findFirst()
            .orElseThrow();
    drowned.moveTo(deep.getX() + 0.5, deep.getY(), deep.getZ() + 0.5, 0, 0);
    drowned.getRandom().setSeed(1);
    for (int attempt = 0; attempt < 32 && drowned.destination == null; attempt++) goal.tick();
    require(drowned.destination != null, "Native drowned swim-up goal ignored the local sea level");
    drowned.destination = null;
    drowned.moveTo(deep.getX() + 0.5, waterline, deep.getZ() + 0.5, 0, 0);
    goal.tick();
    require(
        drowned.destination == null, "Drowned swim-up goal did not stop near the local surface");
    drowned.discard();
    LogUtils.getLogger().info("Marine native drowned swim-up tick passed at {}", deep);
  }

  private static final class NavigationDrowned extends Drowned {
    private BlockPos destination;

    private NavigationDrowned(ServerLevel level) {
      super(EntityType.DROWNED, level);
      navigation =
          new WaterBoundPathNavigation(this, level) {
            @Override
            public boolean moveTo(double x, double y, double z, double speed) {
              destination = BlockPos.containing(x, y, z);
              return true;
            }
          };
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
