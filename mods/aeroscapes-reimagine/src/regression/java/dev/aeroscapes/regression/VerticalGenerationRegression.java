package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.BasinAquifer;
import dev.aeroscapes.worldgen.IslandLayout;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Natural positive habitats, actual saved terrain and water retention for the extended world. */
public final class VerticalGenerationRegression {
  private static ServerLevel pendingLevel;
  private static BlockPos waterControl;
  private static int remainingTicks;

  private VerticalGenerationRegression() {}

  public static void begin(ServerLevel level) {
    require(level.getSeed() == 1, "Use host seed 1 for vertical regression");
    require(
        level.getMinBuildHeight() == -128 && level.getMaxBuildHeight() == 384,
        "Extended dimension bounds are missing");
    var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    require(terrain != null, "Missing extended terrain density");
    if (terrain == null) throw new IllegalStateException("Missing terrain");
    int natureBiomes = 0;
    for (int x = -8192; x <= 8192; x += 512)
      for (int z = -8192; z <= 8192; z += 512) {
        String biome =
            generator
                .getBiomeSource()
                .getNoiseBiome(x >> 2, 48, z >> 2, level.getChunkSource().randomState().sampler())
                .unwrapKey()
                .orElseThrow()
                .location()
                .toString();
        if (biome.startsWith("natures_spirit:")) natureBiomes++;
      }
    require(natureBiomes > 0, "TerraBlender delegate was not initialized");
    LogUtils.getLogger().info("Vertical actual Nature's Spirit source selections={}", natureBiomes);
    aquiferState(terrain);
    for (int x = -8192; x <= 8192; x += 512) {
      var column =
          generator.getBaseColumn(x, x * 3 / 2, level, level.getChunkSource().randomState());
      require(column.getBlock(-128).isAir(), "A continuous bottom fluid/stone layer returned");
    }
    basinAndMountain(level, generator, terrain);
    naturalShell(level, "minecraft:stronghold", 160, -273);
    naturalShell(level, "minecraft:ancient_city", -54, 48);
    naturalShell(level, "minecraft:trial_chambers", 175, -126);
    extraStructure(level, "nova_structures:shrine_combat_tier_5", 17, -44);
    extraStructure(level, "nova_structures:witch_villa", -1408, 1223);
    extraStructure(level, "nova_structures:conduit_ruin", 992, 156);
    naturalMonument(level, generator, terrain);
    lowerResources(level, terrain);
    MarineBasinRegression.verify(level);
    FrozenBasinRegression.verify(level);
    pendingLevel = level;
    remainingTicks = 100;
    NeoForge.EVENT_BUS.addListener(VerticalGenerationRegression::tick);
  }

  private static void extraStructure(ServerLevel level, String id, int chunkX, int chunkZ) {
    Structure structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.parse(id));
    StructureStart start = level.getChunk(chunkX, chunkZ).getStartForStructure(structure);
    if (start == null || !start.isValid())
      start = replacementStart(level, structure, chunkX, chunkZ);
    require(start != null && start.isValid(), "No selected natural FULL start " + id);
    if (start == null) throw new IllegalStateException("Missing start " + id);
    var bounds = start.getBoundingBox();
    for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
      for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) level.getChunk(x, z);
    int loot = 0;
    int water = 0;
    for (BlockPos point :
        BlockPos.betweenClosed(
            bounds.minX(),
            bounds.minY(),
            bounds.minZ(),
            bounds.maxX(),
            bounds.maxY(),
            bounds.maxZ())) {
      if (level.getBlockState(point).is(Blocks.WATER)) water++;
      if (level.getBlockEntity(point)
              instanceof
              net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container
          && container.getLootTable() != null) loot++;
      var blockEntity = level.getBlockEntity(point);
      if (level.getBlockState(point).is(Blocks.VAULT)
          && blockEntity != null
          && blockEntity
              .saveWithFullMetadata(level.registryAccess())
              .getCompound("config")
              .getString("loot_table")
              .startsWith("nova_structures:")) loot++;
    }
    require(loot > 0, "Native loot missing " + id);
    if (id.equals("nova_structures:conduit_ruin"))
      require(water > 20, "Conduit ruin lacks actual water");
    LogUtils.getLogger()
        .info(
            "Vertical extra natural FULL {} chunk={} pieces={} bounds={} loot={} water={}",
            id,
            start.getChunkPos(),
            start.getPieces().size(),
            bounds,
            loot,
            water);
  }

  private static void aquiferState(IslandTerrain terrain) {
    Aquifer scheduled =
        new Aquifer() {
          @Override
          public BlockState computeSubstance(
              net.minecraft.world.level.levelgen.DensityFunction.FunctionContext position,
              double density) {
            return Blocks.AIR.defaultBlockState();
          }

          @Override
          public boolean shouldScheduleFluidUpdate() {
            return true;
          }
        };
    BasinAquifer aquifer = new BasinAquifer(scheduled, terrain);
    for (int x = -8192; x < 8192; x += 1024) {
      for (int z = -8192; z < 8192; z += 1024) {
        IslandLayout.Basin basin = terrain.basin(x, z);
        if (basin == null) continue;
        aquifer.computeSubstance(
            new SinglePointContext(basin.centerX(), basin.waterline() + 100, basin.centerZ()), -1);
        require(aquifer.shouldScheduleFluidUpdate(), "Lost native fluid update scheduling");
        require(
            aquifer
                .computeSubstance(
                    new SinglePointContext(basin.centerX(), basin.waterline(), basin.centerZ()), -1)
                .is(Blocks.WATER),
            "Local water not supplied");
        require(!aquifer.shouldScheduleFluidUpdate(), "Stale native update flag leaked into basin");
        return;
      }
    }
    throw new IllegalStateException("No basin for aquifer regression");
  }

  private static void basinAndMountain(
      ServerLevel level, NoiseBasedChunkGenerator generator, IslandTerrain terrain) {
    boolean basinFound = false;
    boolean mountainFound = false;
    for (int x = -32768; x < 32768 && !(basinFound && mountainFound); x += 512) {
      for (int z = -32768; z < 32768 && !(basinFound && mountainFound); z += 512) {
        IslandLayout.Basin basin = terrain.basin(x, z);
        if (!basinFound && basin != null) {
          int centerX = basin.centerX();
          int centerZ = basin.centerZ();
          var column =
              generator.getBaseColumn(
                  centerX, centerZ, level, level.getChunkSource().randomState());
          require(column.getBlock(basin.waterline()).is(Blocks.WATER), "Raw basin lacks water");
          level.getChunk(centerX >> 4, centerZ >> 4);
          waterControl = new BlockPos(centerX, basin.waterline() - 1, centerZ);
          require(level.getBlockState(waterControl).is(Blocks.WATER), "FULL basin lacks water");
          require(
              net.minecraft.world.entity.animal.WaterAnimal.checkSurfaceWaterAnimalSpawnRules(
                  net.minecraft.world.entity.EntityType.COD,
                  level,
                  net.minecraft.world.entity.MobSpawnType.NATURAL,
                  waterControl.below(3),
                  net.minecraft.util.RandomSource.create(1)),
              "Native surface-water animals cannot spawn at the local basin waterline");
          String biome =
              level.getBiome(waterControl).unwrapKey().orElseThrow().location().toString();
          require(biome.contains("ocean"), "Physical basin has nonmarine biome " + biome);
          basinFound = true;
          LogUtils.getLogger().info("Vertical basin control {} biome={}", waterControl, biome);
        }
        IslandLayout.LargeIsland island = terrain.layout().largeIsland(x, z);
        if (!mountainFound && island != null && island.anchor() + island.peakHeight() > 320) {
          int peakX = (int) island.centerX();
          int peakZ = (int) island.centerZ();
          double highest = Double.NEGATIVE_INFINITY;
          for (int dx = -256; dx <= 256; dx += 8)
            for (int dz = -256; dz <= 256; dz += 8) {
              int candidateX = (int) island.centerX() + dx;
              int candidateZ = (int) island.centerZ() + dz;
              if (island.radius(candidateX, candidateZ) >= 0.8) continue;
              double height = island.surface(candidateX, candidateZ);
              if (height > highest) {
                highest = height;
                peakX = candidateX;
                peakZ = candidateZ;
              }
            }
          int top = (int) island.surface(peakX, peakZ) - 2;
          var column =
              generator.getBaseColumn(peakX, peakZ, level, level.getChunkSource().randomState());
          require(!column.getBlock(top).isAir(), "Mountain relief is missing");
          String biome =
              generator
                  .getBiomeSource()
                  .getNoiseBiome(
                      peakX >> 2,
                      top >> 2,
                      peakZ >> 2,
                      level.getChunkSource().randomState().sampler())
                  .unwrapKey()
                  .orElseThrow()
                  .location()
                  .toString();
          require(biome.endsWith("peaks"), "Mountain peak has wrong biome " + biome);
          level.getChunk(peakX >> 4, peakZ >> 4);
          require(
              !level.getBlockState(new BlockPos(peakX, top, peakZ)).isAir(),
              "FULL mountain disappeared during surface/features");
          java.util.Set<String> plateauBiomes = new java.util.HashSet<>();
          int plateauLand = 0;
          for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
              int plainX = (int) (island.centerX() + dx * island.radiusX() / 5);
              int plainZ = (int) (island.centerZ() + dz * island.radiusZ() / 5);
              if (island.peak(plainX, plainZ) > 20) continue;
              int plainY = (int) island.surface(plainX, plainZ) - 2;
              var plainColumn =
                  generator.getBaseColumn(
                      plainX, plainZ, level, level.getChunkSource().randomState());
              if (plainColumn.getBlock(plainY).isAir()) continue;
              plateauLand++;
              plateauBiomes.add(
                  generator
                      .getBiomeSource()
                      .getNoiseBiome(
                          plainX >> 2,
                          plainY >> 2,
                          plainZ >> 2,
                          level.getChunkSource().randomState().sampler())
                      .unwrapKey()
                      .orElseThrow()
                      .location()
                      .toString());
            }
          require(
              plateauLand > 8 && plateauBiomes.size() >= 3,
              "Large mountain island lacks broad multibiome land: " + plateauBiomes);
          LogUtils.getLogger()
              .info(
                  "Vertical large island plateau samples={} biomes={}", plateauLand, plateauBiomes);
          mountainFound = true;
          LogUtils.getLogger()
              .info(
                  "Vertical mountain {},{},{} base={} relief={} biome={}",
                  peakX,
                  top,
                  peakZ,
                  island.anchor(),
                  island.peakHeight(),
                  biome);
        }
      }
    }
    require(basinFound && mountainFound, "Missing positive basin/mountain habitat");
  }

  private static void lowerResources(ServerLevel level, IslandTerrain terrain) {
    for (int x = -16384; x < 16384; x += 1536) {
      for (int z = -16384; z < 16384; z += 1536) {
        var island = terrain.layout().largeIsland(x, z);
        if (island == null || island.anchor() != -48) continue;
        int centerX = (int) island.centerX();
        int centerZ = (int) island.centerZ();
        java.util.Map<String, Integer> ores = new java.util.TreeMap<>();
        for (int dx = -16; dx < 16; dx++)
          for (int dz = -16; dz < 16; dz++)
            for (int y = -112; y < -16; y++) {
              BlockPos point = new BlockPos(centerX + dx, y, centerZ + dz);
              String block =
                  net.minecraft.core.registries.BuiltInRegistries.BLOCK
                      .getKey(level.getBlockState(point).getBlock())
                      .toString();
              if (block.contains("ore")) ores.merge(block, 1, Integer::sum);
            }
        LogUtils.getLogger()
            .info(
                "Vertical lower FULL sample {},{} biome={} ores={}",
                centerX,
                centerZ,
                level
                    .getBiome(new BlockPos(centerX, -64, centerZ))
                    .unwrapKey()
                    .orElseThrow()
                    .location(),
                ores);
        require(!ores.isEmpty(), "Lower large island has no generated ores");
        require(
            ores.keySet().stream().anyMatch(id -> id.contains("iron")),
            "Missing lower iron resource");
        LogUtils.getLogger()
            .info("Vertical lower FULL resource sample {},{} ores={}", centerX, centerZ, ores);
        return;
      }
    }
    throw new IllegalStateException("Missing lower large island for resource check");
  }

  private static void naturalShell(ServerLevel level, String id, int chunkX, int chunkZ) {
    Structure structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.parse(id));
    StructureStart start = level.getChunk(chunkX, chunkZ).getStartForStructure(structure);
    if (start == null || !start.isValid()) {
      var context =
          new Structure.GenerationContext(
              level.registryAccess(),
              level.getChunkSource().getGenerator(),
              level.getChunkSource().getGenerator().getBiomeSource(),
              level.getChunkSource().randomState(),
              level.getStructureManager(),
              level.getSeed(),
              new ChunkPos(chunkX, chunkZ),
              level,
              structure.biomes()::contains);
      LogUtils.getLogger()
          .info(
              "Vertical prior site {} {},{} stub={}",
              id,
              chunkX,
              chunkZ,
              structure.findValidGenerationPoint(context).isPresent());
      start = replacementStart(level, structure, chunkX, chunkZ);
    }
    if (start == null) throw new IllegalStateException("Missing " + id);
    var allBounds = start.getBoundingBox();
    for (int x = allBounds.minX() >> 4; x <= allBounds.maxX() >> 4; x++)
      for (int z = allBounds.minZ() >> 4; z <= allBounds.maxZ() >> 4; z++) level.getChunk(x, z);
    var assembly = start.getPieces();
    int rawVoid = 0;
    int filled = 0;
    int insideOtherRooms = 0;
    Set<Long> sampled = new HashSet<>();
    for (var piece : start.getPieces()) {
      var box = piece.getBoundingBox();
      BlockPos center = box.getCenter();
      for (BlockPos point :
          java.util.List.of(
              new BlockPos(center.getX(), box.minY() - 2, center.getZ()),
              new BlockPos(center.getX(), box.maxY() + 2, center.getZ()),
              new BlockPos(box.minX() - 2, center.getY(), center.getZ()),
              new BlockPos(box.maxX() + 2, center.getY(), center.getZ()),
              new BlockPos(center.getX(), center.getY(), box.minZ() - 2),
              new BlockPos(center.getX(), center.getY(), box.maxZ() + 2))) {
        int x = point.getX();
        int y = point.getY();
        int z = point.getZ();
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
        if (!sampled.add(point.asLong())) continue;
        // A roof/floor probe can be inside a different storey of the same complex.
        // Those cells must remain passages, not be filled to satisfy a shell oracle.
        if (assembly.stream()
            .anyMatch(other -> other.getBoundingBox().inflatedBy(1).isInside(point))) {
          insideOtherRooms++;
          continue;
        }
        var column =
            level
                .getChunkSource()
                .getGenerator()
                .getBaseColumn(x, z, level, level.getChunkSource().randomState());
        if (!column.getBlock(y).isAir()) continue;
        rawVoid++;
        level.getChunk(x >> 4, z >> 4);
        var actual = level.getBlockState(point);
        // Later native decoration can occupy raw AIR (for example Galosphere mushroom caps).
        // This oracle detects geological room shells; shared air and roof are checked separately.
        if (actual.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
            || actual.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) filled++;
      }
    }
    require(
        rawVoid == 0 || filled * 5 <= rawVoid,
        "Terrain still fills raw-void points outside native rooms "
            + id
            + ": "
            + filled
            + "/"
            + rawVoid);
    if (id.equals("minecraft:ancient_city")) {
      var bounds =
          net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingBoxes(
                  start.getPieces().stream().map(piece -> piece.getBoundingBox()).toList())
              .orElseThrow();
      int open = 0;
      int tested = 0;
      for (int dx = -32; dx <= 32; dx += 4)
        for (int dz = -32; dz <= 32; dz += 4) {
          BlockPos point =
              new BlockPos(
                  bounds.getCenter().getX() + dx,
                  bounds.minY() + 14,
                  bounds.getCenter().getZ() + dz);
          if (start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().isInside(point)))
            continue;
          level.getChunk(point.getX() >> 4, point.getZ() >> 4);
          tested++;
          if (level.getBlockState(point).isAir()) open++;
        }
      require(
          tested > 8 && open * 5 >= tested * 4,
          "Ancient city lacks shared air between templates: " + open + "/" + tested);
      var cavern =
          new dev.aeroscapes.worldgen.CityCavern(
              start.getPieces().stream().map(piece -> piece.getBoundingBox()).toList());
      int roofs = 0;
      int roofSamples = 0;
      for (var piece : start.getPieces()) {
        if (piece
                instanceof
                net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool
            && pool.getElement()
                instanceof net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement)
          continue;
        var center = piece.getBoundingBox().getCenter();
        int roof = (int) Math.ceil(cavern.ceiling(center.getX(), center.getZ()));
        var lowRoof = level.getBlockState(new BlockPos(center.getX(), roof + 2, center.getZ()));
        var highRoof = level.getBlockState(new BlockPos(center.getX(), roof + 6, center.getZ()));
        if (!lowRoof.isAir()
            && lowRoof.getFluidState().isEmpty()
            && !highRoof.isAir()
            && highRoof.getFluidState().isEmpty()) roofs++;
        roofSamples++;
      }
      require(
          roofs * 2 >= roofSamples,
          "City cavern is open to the void above: " + roofs + "/" + roofSamples);
      LogUtils.getLogger()
          .info(
              "Vertical city shared cavern air={}/{} retainedRoof={}/{}",
              open,
              tested,
              roofs,
              roofSamples);
    }
    LogUtils.getLogger()
        .info(
            "Vertical native assembly {} pieces={} exposedNaturalVoid={} unwantedFilled={} interRoomProbes={}",
            id,
            start.getPieces().size(),
            rawVoid,
            filled,
            insideOtherRooms);
  }

  static StructureStart replacementStart(
      ServerLevel level, Structure structure, int chunkX, int chunkZ) {
    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    var state = level.getChunkSource().getGeneratorState();
    long deadline = System.nanoTime() + 60_000_000_000L;
    for (var placement : state.getPlacementsForStructure(registry.wrapAsHolder(structure))) {
      if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
      int centerX = Math.floorDiv(chunkX, spread.spacing());
      int centerZ = Math.floorDiv(chunkZ, spread.spacing());
      for (int radius = 0; radius <= 16; radius++) {
        for (int regionX = centerX - radius; regionX <= centerX + radius; regionX++) {
          for (int regionZ = centerZ - radius; regionZ <= centerZ + radius; regionZ++) {
            if (Math.max(Math.abs(regionX - centerX), Math.abs(regionZ - centerZ)) != radius)
              continue;
            require(System.nanoTime() < deadline, "Natural replacement search exceeded 60 seconds");
            ChunkPos chunk =
                spread.getPotentialStructureChunk(
                    level.getSeed(), regionX * spread.spacing(), regionZ * spread.spacing());
            var generator = level.getChunkSource().getGenerator();
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
            if (!spread.isStructureChunk(state, chunk.x, chunk.z)
                || structure.findValidGenerationPoint(context).isEmpty()) continue;
            StructureStart candidate =
                level.getChunk(chunk.x, chunk.z).getStartForStructure(structure);
            if (candidate != null && candidate.isValid()) {
              LogUtils.getLogger()
                  .info(
                      "Vertical replacement natural start {} {}",
                      registry.getKey(structure),
                      chunk);
              return candidate;
            }
          }
        }
      }
    }
    throw new IllegalStateException("No naturally selected replacement start");
  }

  private static void naturalMonument(
      ServerLevel level, NoiseBasedChunkGenerator generator, IslandTerrain terrain) {
    Structure structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.withDefaultNamespace("monument"));
    var holder =
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(structure);
    for (var placement :
        level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder)) {
      if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
      for (int ring = 0; ring <= 64; ring++) {
        for (int regionX = -ring; regionX <= ring; regionX++) {
          for (int regionZ = -ring; regionZ <= ring; regionZ++) {
            if (Math.max(Math.abs(regionX), Math.abs(regionZ)) != ring) continue;
            ChunkPos chunk =
                spread.getPotentialStructureChunk(
                    level.getSeed(), regionX * spread.spacing(), regionZ * spread.spacing());
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            IslandLayout.Basin basin = terrain.basin(x, z);
            if (basin == null || basin.radius(x, z) > 0.7) continue;
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
            require(start != null && start.isValid(), "Missing selected wet monument");
            if (start == null) throw new IllegalStateException("Missing monument");
            var bounds = start.getBoundingBox();
            for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++)
              for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++)
                level.getChunk(cx, cz);
            int gold = 0;
            int sponges = 0;
            int water = 0;
            for (BlockPos point :
                BlockPos.betweenClosed(
                    bounds.minX(),
                    bounds.minY(),
                    bounds.minZ(),
                    bounds.maxX(),
                    bounds.maxY(),
                    bounds.maxZ())) {
              var state = level.getBlockState(point);
              if (state.is(Blocks.GOLD_BLOCK)) gold++;
              if (state.is(Blocks.WET_SPONGE)) sponges++;
              if (state.is(Blocks.WATER)) water++;
            }
            LogUtils.getLogger()
                .info(
                    "Vertical monument contents at {} gold={} sponges={} water={}",
                    chunk,
                    gold,
                    sponges,
                    water);
            require(gold == 8 && water > 1000, "Incomplete ocean monument contents");
            int footers = 0;
            // Expanded native bounds start four blocks below the building's room base.
            int footingY = bounds.minY() + 3;
            for (int footingX = bounds.minX(); footingX <= bounds.maxX(); footingX++)
              for (int footingZ = bounds.minZ(); footingZ <= bounds.maxZ(); footingZ++) {
                BlockPos.MutableBlockPos footing =
                    new BlockPos.MutableBlockPos(footingX, footingY, footingZ);
                if (!level.getBlockState(footing).is(Blocks.PRISMARINE_BRICKS)) continue;
                footers++;
                int depth = 0;
                while (depth < 24 && level.getBlockState(footing).is(Blocks.PRISMARINE_BRICKS)) {
                  footing.move(net.minecraft.core.Direction.DOWN);
                  depth++;
                }
                var support = level.getBlockState(footing);
                require(
                    !support.isAir()
                        && support.getFluidState().isEmpty()
                        && net.minecraft.world.level.block.Block.isFaceFull(
                            support.getCollisionShape(level, footing),
                            net.minecraft.core.Direction.UP),
                    "Monument footing hangs above its bed at " + footing);
              }
            LogUtils.getLogger().info("Vertical supported monument footing columns={}", footers);
            LogUtils.getLogger()
                .info(
                    "Vertical natural monument {} gold={} sponges={} water={}",
                    chunk,
                    gold,
                    sponges,
                    water);
            // Native room graphs can contain no sponge room; verify a positive sponge
            // monument elsewhere rather than requiring one in every valid monument.
            if (sponges == 0) continue;
            return;
          }
        }
      }
    }
    throw new IllegalStateException("No naturally reachable basin monument in bounded search");
  }

  private static void tick(ServerTickEvent.Post event) {
    if (pendingLevel == null || --remainingTicks > 0) return;
    require(pendingLevel.getBlockState(waterControl).is(Blocks.WATER), "Basin drained after ticks");
    pendingLevel = null;
    LogUtils.getLogger().info("Vertical generation regression passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
