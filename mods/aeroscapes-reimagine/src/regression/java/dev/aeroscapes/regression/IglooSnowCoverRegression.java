package dev.aeroscapes.regression;

import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;

/** Replays the reported boreal-taiga placement and checks snow outside native igloo templates. */
final class IglooSnowCoverRegression {
  private static final long BOREAL_TAIGA_SEED = -5760707733669810382L;
  private static final long SNOWY_FIR_FOREST_SEED = -4997371027048422072L;
  private static final ResourceLocation IGLOO = ResourceLocation.parse("formationsoverworld:igloo");

  private IglooSnowCoverRegression() {}

  static void verify(ServerLevel level) {
    var igloo =
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(IGLOO).orElseThrow();
    require(StructureUtils.getAllowedStructureKeys(level).contains(IGLOO), "Compass omits igloo");
    if (level.getSeed() == BOREAL_TAIGA_SEED || level.getSeed() == SNOWY_FIR_FOREST_SEED) {
      boolean boreal = level.getSeed() == BOREAL_TAIGA_SEED;
      var site =
          boreal
              ? new BoundingBox(-882, 133, -423, -871, 138, -412)
              : new BoundingBox(2139, -62, 2030, 2150, -57, 2041);
      load(level, site.inflatedBy(6));
      var biome = level.getBiome(site.getCenter().atY(site.minY() + 1));
      require(
          biome
              .unwrapKey()
              .orElseThrow()
              .location()
              .equals(
                  ResourceLocation.fromNamespaceAndPath(
                      "natures_spirit", boreal ? "boreal_taiga" : "snowy_fir_forest")),
          "Location no longer has the witnessed biome");
      var start =
          level
              .getChunk(boreal ? -56 : 134, boreal ? -27 : 126)
              .getStartForStructure(igloo.value());
      require(start == null || !start.isValid(), "Igloo still generated in sparsely snowy taiga");
      LogUtils.getLogger()
          .info(
              "Igloo snow regression complete: negative seed={} former_box={}",
              level.getSeed(),
              site);
      return;
    }

    if (level.getSeed() == 42) {
      var barrenSlopes = level.getChunk(183, 366).getStartForStructure(igloo.value());
      require(
          barrenSlopes == null || !barrenSlopes.isValid(),
          "Igloo generated on weathered snowy slopes with no substantial solid snow cover");
    }
    var generator = level.getChunkSource().getGenerator();
    var near = new ChunkPos(0, 0);
    if (level.getSeed() == 42) near = new ChunkPos(527, -600);
    else if (level.getSeed() == 1) near = new ChunkPos(60, -134);
    else if (level.getSeed() == 123456789) near = new ChunkPos(-26126 >> 4, 30618 >> 4);
    else if (level.getSeed() == 20261030) near = new ChunkPos(18002 >> 4, -4734 >> 4);
    var search =
        NaturalStructureWitness.find(level, IGLOO, near).getChunkPos().getMiddleBlockPosition(0);
    var found = generator.findNearestMapStructure(level, HolderSet.direct(igloo), search, 6, false);
    require(found != null && found.getSecond().equals(igloo), "Native locate found no igloo");
    var start = level.getChunk(found.getFirst()).getStartForStructure(igloo.value());
    require(start != null && start.isValid(), "Native locate has no FULL igloo start");
    var root = (PoolElementStructurePiece) start.getPieces().getFirst();
    var bounds = start.getBoundingBox();
    int grade = root.getBoundingBox().minY() + root.getGroundLevelDelta();
    load(level, bounds.inflatedBy(6));
    int decoration = 0;
    int columns = 0;
    for (int x = bounds.minX() - 6; x <= bounds.maxX() + 6; x++) {
      for (int z = bounds.minZ() - 6; z <= bounds.maxZ() + 6; z++) {
        if (x >= bounds.minX() && x <= bounds.maxX() && z >= bounds.minZ() && z <= bounds.maxZ())
          continue;
        columns++;
        for (int y = grade + 6; y >= grade - 6; y--) {
          var state = level.getBlockState(new BlockPos(x, y, z));
          if (state.isAir()) continue;
          if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) decoration++;
          break;
        }
      }
    }
    require(
        decoration * 3 >= columns,
        "Snow cover around igloo is sparse: " + decoration + "/" + columns + " at " + bounds);
    LogUtils.getLogger()
        .info(
            "Igloo snow regression complete: positive seed={} locate={} bounds={}"
                + " snow_columns={}/{}",
            level.getSeed(),
            found.getFirst(),
            bounds,
            decoration,
            columns);
  }

  private static void load(ServerLevel level, BoundingBox bounds) {
    for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
      for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) level.getChunk(x, z);
  }

  private static void require(boolean condition, String reason) {
    if (!condition) throw new IllegalStateException(reason);
  }
}
