package dev.aeroscapes.regression;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/** Executable regression cases for a fresh, disposable dedicated-server world. */
@Mod(value = "aeroscapes_regression", dist = Dist.DEDICATED_SERVER)
public final class WorldgenRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final long FIXTURE_SEED = -8315743817914385108L;

  private record Site(String structure, int chunkX, int chunkZ) {}

  private record Column(int x, int z) {}

  private static final List<Site> NEGATIVE_SITES =
      List.of(
          new Site("nova_structures:wild_ruin", -20, -27),
          new Site("nova_structures:bunker", -16, -15),
          new Site("nova_structures:wild_ruin", 7, 5),
          new Site("nova_structures:bunker", 31, 17),
          new Site("nova_structures:mangrove_witch_hut", -55, -14),
          new Site("philipsruins:pumpkin_ruins", -41, 3),
          new Site("nova_structures:wild_ruin", -51, 6),
          new Site("nova_structures:illager_camp", -42, 7),
          new Site("nova_structures:bunker", -44, 15),
          new Site("nova_structures:wild_ruin", -19, -46),
          new Site("minecraft:trail_ruins", -23, -34),
          new Site("nova_structures:village_swamp", 1, -45),
          new Site("nova_structures:wild_ruin", 60, -22),
          new Site("nova_structures:bunker", 32, -18),
          new Site("nova_structures:wild_ruin", 31, 33),
          new Site("nova_structures:well_savana", -72, -59),
          new Site("minecraft:trail_ruins", -77, 7),
          new Site("nova_structures:remnant_bridge_remnant", -66, -80),
          new Site("nova_structures:wild_ruin", -81, -74),
          new Site("nova_structures:wild_ruin", 1, -75),
          new Site("nova_structures:wild_ruin", -103, -47),
          new Site("nova_structures:wild_ruin", -101, -22),
          new Site("nova_structures:badlands_miner_outpost", -106, -13),
          new Site("nova_structures:ruin_town", -116, 12),
          new Site("nova_structures:wild_ruin", -127, 31),
          new Site("nova_structures:bunker", -138, 24),
          new Site("nova_structures:wild_ruin", -160, 30),
          new Site("nova_structures:village_jungle", -187, -53),
          new Site("nova_structures:illager_camp", -134, -86),
          new Site("nova_structures:village_jungle", -155, -84),
          new Site("nova_structures:wild_ruin", -131, -73),
          new Site("nova_structures:firewatch_tower_savanna", -173, -90),
          new Site("minecraft:trail_ruins", -152, -98),
          new Site("philipsruins:pumpkin_ruins", -99, -98),
          new Site("nova_structures:village_birch", -102, -97));
  private static final List<Column> COLUMN_SITES =
      List.of(
          new Column(-72, -504),
          new Column(-56, -504),
          new Column(-88, -488),
          new Column(-320, -176),
          new Column(-417, -97),
          new Column(-337, -96),
          new Column(-448, -801),
          new Column(-369, -800),
          new Column(-352, -800),
          new Column(-16, -576),
          new Column(-737, -33),
          new Column(-816, -1313),
          new Column(-689, 112));

  public WorldgenRegression() {
    NeoForge.EVENT_BUS.addListener(WorldgenRegression::verify);
    VanillaRegression.register();
  }

  private static void verify(ServerStartedEvent event) {
    ServerLevel level = event.getServer().overworld();
    require(event.getServer().isDedicatedServer(), "Use only a disposable dedicated server");
    String resourceSnapshot = System.getProperty("aeroscapes.regression.resourceSnapshot");
    if (resourceSnapshot != null) {
      ResourceSnapshotRegression.verify(level, resourceSnapshot);
      return;
    }
    String templateRepairs = System.getProperty("aeroscapes.regression.templateRepairs");
    if (templateRepairs != null) {
      RuntimeTemplateRepairsRegression.verify(level, templateRepairs);
      return;
    }
    String sableLighting = System.getProperty("aeroscapes.regression.sableLighting");
    if (sableLighting != null) {
      SableLightingRegression.start(sableLighting);
      return;
    }
    if (Boolean.getBoolean("aeroscapes.regression.featurePreparation")) {
      event
          .getServer()
          .tell(
              new TickTask(
                  event.getServer().getTickCount(),
                  () -> FeaturePreparationRegression.verify(level)));
      return;
    }
    if (Boolean.getBoolean("aeroscapes.regression.localPacing")) {
      LocalChunkPacingRegression.start(level);
      return;
    }
    if (System.getProperty("aeroscapes.regression.stageSnapshotFile") != null) {
      if (Boolean.getBoolean("aeroscapes.regression.performance.opencl"))
        OpenClPerformanceRegression.start(level, () -> StageSnapshotRegression.verify(level));
      else
        event
            .getServer()
            .tell(
                new TickTask(
                    event.getServer().getTickCount(), () -> StageSnapshotRegression.verify(level)));
      return;
    }
    if (Boolean.getBoolean("aeroscapes.regression.opencl.forceBuildFailure")) {
      OpenClBuildFailureRegression.verify(level);
      return;
    }
    if (System.getProperty("aeroscapes.regression.noiseSnapshot") != null) {
      if (Boolean.getBoolean("aeroscapes.regression.performance.opencl"))
        OpenClPerformanceRegression.start(level, () -> NoiseSnapshotRegression.verify(level));
      else NoiseSnapshotRegression.verify(level);
      return;
    }
    if (Boolean.getBoolean("aeroscapes.regression.performance")) {
      if (Boolean.getBoolean("aeroscapes.regression.performance.opencl"))
        OpenClPerformanceRegression.start(level);
      else ChunkPerformanceRegression.start(level);
      return;
    }
    if (Boolean.getBoolean("aeroscapes.regression.c2me")) {
      C2meIntegrationRegression.verify(level);
      return;
    }
    if (Boolean.getBoolean("aeroscapes.regression.polish")) {
      if (Boolean.getBoolean("aeroscapes.regression.performance.opencl"))
        OpenClPerformanceRegression.start(level, () -> TerrainPolishRegression.verify(level));
      else TerrainPolishRegression.verify(level);
      return;
    }
    if (Boolean.getBoolean("aeroscapes.regression.vertical")) {
      VerticalGenerationRegression.begin(level);
      return;
    }
    if (Boolean.getBoolean("aeroscapes.regression.features")) {
      FeaturePlacementRegression.verify(level);
      return;
    }
    String core = System.getProperty("aeroscapes.regression.core");
    if (core != null) {
      CoreWorldgenRegression.verify(level, core.equals("baseline"));
      return;
    }
    EndBiomeRegression.verify(level);
    WorldgenDataRegression.verify(level);
    if (level.getSeed() == 1) {
      VanillaRegression.begin(level);
      return;
    }
    if (level.getSeed() == AquiferRegression.SEED) {
      AquiferRegression.verify(level);
      LocateRegression.verify(level);
      return;
    }
    require(level.getSeed() == FIXTURE_SEED, "Use fixture seed " + FIXTURE_SEED);
    require(
        level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
        "Use the normal Aero Islands preset");
    NoiseBasedChunkGenerator generator =
        (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
    for (Site site : NEGATIVE_SITES) {
      require(
          structure(level, site.structure())
              .findValidGenerationPoint(context(level, generator, site, false))
              .isEmpty(),
          "Accepted bottom-clipped site " + site);
    }
    for (Column column : COLUMN_SITES) {
      var chunk = level.getChunk(column.x() >> 4, column.z() >> 4);
      BlockPos bottom = new BlockPos(column.x(), level.getMinBuildHeight(), column.z());
      require(
          chunk.getBlockState(bottom).isAir(), "Terrain still reaches world bottom at " + bottom);
    }
    naturalStart(level, generator, new Site("terralith:underground/oak_cabin", -1610, 2492));
    naturalStart(level, generator, new Site("terralith:underground/giant_bee_hive", 309, -1473));
    naturalStart(level, generator, new Site("terralith:underground/sunken_tower", -592, 69));
    raisedSurfaceRoot(level, generator);
    LOGGER.info(
        "Worldgen regression passed: {} clipped starts, {} column floors, natural underground starts",
        NEGATIVE_SITES.size(),
        COLUMN_SITES.size());
  }

  private static void raisedSurfaceRoot(ServerLevel level, NoiseBasedChunkGenerator generator) {
    // Isolate the real coconut-village root, whose native start offset is +1.
    // Child placement is covered separately by the complete assembly bounds checks.
    ResourceLocation file =
        ResourceLocation.parse("natures_spirit:worldgen/structure/village_coconut.json");
    try (Reader reader =
        level.getServer().getResourceManager().getResourceOrThrow(file).openAsReader()) {
      var definition = JsonParser.parseReader(reader).getAsJsonObject();
      definition.getAsJsonObject("delegate").addProperty("size", 0);
      Structure rootOnly =
          Structure.DIRECT_CODEC
              .parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), definition)
              .getOrThrow();
      require(
          rootOnly
              .findValidGenerationPoint(
                  context(
                      level,
                      generator,
                      new Site("natures_spirit:village_coconut", -15, -11),
                      false))
              .isPresent(),
          "Lost native surface root with a one-block start offset");
      // A lower island must not count as support for a root suspended above it.
      definition
          .getAsJsonObject("delegate")
          .getAsJsonObject("start_height")
          .addProperty("absolute", 16);
      Structure suspendedRoot =
          Structure.DIRECT_CODEC
              .parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), definition)
              .getOrThrow();
      require(
          suspendedRoot
              .findValidGenerationPoint(
                  context(
                      level,
                      generator,
                      new Site("natures_spirit:village_coconut", -15, -11),
                      false))
              .isEmpty(),
          "Accepted a suspended surface root above a lower island");
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read raised-root regression fixture", exception);
    }
  }

  private static Structure structure(ServerLevel level, String id) {
    Structure structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.parse(id));
    if (structure == null) {
      throw new IllegalStateException("Missing regression structure " + id);
    }
    return structure;
  }

  private static Structure.GenerationContext context(
      ServerLevel level, NoiseBasedChunkGenerator generator, Site site, boolean enforceBiome) {
    Structure structure = structure(level, site.structure());
    return new Structure.GenerationContext(
        level.registryAccess(),
        generator,
        generator.getBiomeSource(),
        level.getChunkSource().randomState(),
        level.getStructureManager(),
        level.getSeed(),
        new ChunkPos(site.chunkX(), site.chunkZ()),
        level,
        enforceBiome ? structure.biomes()::contains : ignored -> true);
  }

  private static void naturalStart(
      ServerLevel level, NoiseBasedChunkGenerator generator, Site site) {
    Structure structure = structure(level, site.structure());
    var expected = structure.findValidGenerationPoint(context(level, generator, site, true));
    require(expected.isPresent(), "Lost eligible underground site " + site);
    var start = level.getChunk(site.chunkX(), site.chunkZ()).getStartForStructure(structure);
    require(start != null && start.isValid(), "No naturally selected start " + site);
    if (start == null) {
      throw new IllegalStateException("Missing start after validation");
    }
    var expectedBounds = expected.orElseThrow().getPiecesBuilder().getBoundingBox();
    require(
        start.getBoundingBox().equals(expectedBounds),
        "Assembly changed between query and placement " + site);
    for (var piece : start.getPieces()) {
      BoundingBox bounds = piece.getBoundingBox();
      BlockPos center = bounds.getCenter();
      for (BlockPos sample :
          List.of(
              new BlockPos(bounds.minX() - 2, center.getY(), center.getZ()),
              new BlockPos(bounds.maxX() + 2, center.getY(), center.getZ()),
              new BlockPos(center.getX(), bounds.minY() - 2, center.getZ()),
              new BlockPos(center.getX(), bounds.maxY() + 2, center.getZ()),
              new BlockPos(center.getX(), center.getY(), bounds.minZ() - 2),
              new BlockPos(center.getX(), center.getY(), bounds.maxZ() + 2))) {
        BlockState state = level.getBlockState(sample);
        require(
            state.getFluidState().isEmpty()
                && Block.isFaceFull(state.getCollisionShape(level, sample), Direction.UP),
            "Underground room has exposed shell at " + sample + " for " + site);
      }
    }
    LOGGER.info("Natural regression start {} at {}", site.structure(), start.getBoundingBox());
  }

  private static void require(boolean accepted, String failure) {
    if (!accepted) {
      throw new IllegalStateException(failure);
    }
  }
}
