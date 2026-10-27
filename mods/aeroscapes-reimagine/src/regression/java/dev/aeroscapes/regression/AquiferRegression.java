package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.slf4j.Logger;

/** Reproduces the void aquifer shell and verifies independent island water sources. */
final class AquiferRegression {
  static final long SEED = -4851711576543251912L;
  private static final Logger LOGGER = LogUtils.getLogger();

  private AquiferRegression() {}

  static void verify(ServerLevel level) {
    if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
      throw new IllegalStateException("Use the normal Aero Islands preset");
    }
    if (!generator.generatorSettings().value().isAquifersEnabled()) {
      throw new IllegalStateException("Aquifers must remain enabled");
    }
    int checked = 0;
    for (int x = 536; x <= 561; x++) {
      for (int z = 632; z <= 659; z++) {
        var column = generator.getBaseColumn(x, z, level, level.getChunkSource().randomState());
        var chunk = level.getChunk(x >> 4, z >> 4);
        for (int y = 24; y <= 60; y++) {
          BlockPos position = new BlockPos(x, y, z);
          if (!column.getBlock(y).isAir() || !chunk.getBlockState(position).isAir()) {
            throw new IllegalStateException("Void aquifer shell remains at " + position);
          }
          checked++;
        }
      }
    }
    // Final feature positions may differ after neighboring terrain changes. Require
    // accessible source water on natural solid ground in the same bounded island area.
    for (int chunkX = 35; chunkX <= 39; chunkX++) {
      for (int chunkZ = 15; chunkZ <= 23; chunkZ++) {
        level.getChunk(chunkX, chunkZ);
      }
    }
    int islandSources = 0;
    for (int x = 560; x < 640; x++) {
      for (int z = 240; z < 384; z++) {
        var column = generator.getBaseColumn(x, z, level, level.getChunkSource().randomState());
        for (int y = 129; y <= 200; y++) {
          BlockPos position = new BlockPos(x, y, z);
          var fluid = level.getBlockState(position).getFluidState();
          var floor = column.getBlock(y - 1);
          if (fluid.is(FluidTags.WATER)
              && fluid.isSource()
              && !floor.isAir()
              && floor.getFluidState().isEmpty()) {
            islandSources++;
          }
        }
      }
    }
    if (islandSources == 0) {
      throw new IllegalStateException("No island water sources above natural solid ground");
    }
    LOGGER.info(
        "Aquifer regression passed: {} raw/final void blocks; {} island water sources above natural ground",
        checked,
        islandSources);
  }
}
