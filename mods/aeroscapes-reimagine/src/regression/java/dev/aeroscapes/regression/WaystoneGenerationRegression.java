package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import java.util.Random;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.block.WaystoneBlock;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntity;
import net.blay09.mods.waystones.config.WaystonesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/** Natural FULL-chunk placement, independent of the interaction fixture's placed stones. */
public final class WaystoneGenerationRegression {
  private WaystoneGenerationRegression() {}

  public static void verify(ServerLevel level) {
    int spacing = WaystonesConfig.getActive().worldGen.chunksBetweenWildWaystones;
    require(spacing == 25, "Keep native wild-waystone spacing");
    int positive = 0;
    int checked = 0;
    int voidChecked = 0;
    var generator = level.getChunkSource().getGenerator();
    for (int radius = 0; radius <= 160 && (positive < 2 || voidChecked == 0); radius++) {
      for (int chunkX = -radius; chunkX <= radius; chunkX++) {
        for (int chunkZ = -radius; chunkZ <= radius; chunkZ++) {
          if (Math.max(Math.abs(chunkX), Math.abs(chunkZ)) != radius
              || !candidate(level.getSeed(), chunkX, chunkZ, spacing)) continue;
          int x = chunkX * 16;
          int z = chunkZ * 16;
          int height =
              generator.getBaseHeight(
                  x,
                  z,
                  Heightmap.Types.OCEAN_FLOOR_WG,
                  level,
                  level.getChunkSource().randomState());
          boolean voidColumn = height <= level.getMinBuildHeight();
          if ((voidColumn && voidChecked > 0) || (!voidColumn && positive >= 2)) continue;
          require(checked++ < 16, "No natural waystone found in sixteen eligible FULL chunks");
          var chunk = level.getChunk(chunkX, chunkZ);
          int found = 0;
          for (var entity : chunk.getBlockEntities().values()) {
            if (!(entity instanceof WaystoneBlockEntity)
                || entity.getBlockState().getValue(WaystoneBlockBase.ORIGIN)
                    != WaystoneOrigin.WILDERNESS
                || entity.getBlockState().getValue(WaystoneBlock.HALF) != DoubleBlockHalf.LOWER)
              continue;
            BlockPos position = entity.getBlockPos();
            require(
                !level.getBlockState(position.below()).isAir(),
                "Naturally generated waystone has no floor at " + position);
            found++;
            LogUtils.getLogger()
                .info("Natural waystone witness seed={} position={}", level.getSeed(), position);
          }
          if (voidColumn) {
            require(found == 0, "Void-column eligibility must not generate a floating waystone");
            voidChecked++;
          } else {
            positive += found;
          }
        }
      }
    }
    require(positive >= 2 && voidChecked == 1, "Natural land/void witnesses incomplete");
    LogUtils.getLogger()
        .info(
            "Waystone natural generation regression passed: {} stones, {} FULL chunks",
            positive,
            checked);
  }

  private static boolean candidate(long seed, int chunkX, int chunkZ, int spacing) {
    // Select native grid candidates; assertions above inspect actual saved FULL generation.
    int deviation = (int) Math.ceil(spacing / 2f);
    int gridX = chunkX * deviation;
    int gridZ = chunkZ * deviation;
    Random random = new Random(seed * gridX * gridZ);
    return (chunkX + random.nextInt(deviation)) % spacing == 0
        && (chunkZ + random.nextInt(deviation)) % spacing == 0;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
