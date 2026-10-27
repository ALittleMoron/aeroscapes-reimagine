package dev.aeroscapes.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.RandomState;

/** Resolves local ocean sea level without changing other columns or dimensions. */
public final class BasinWaterline {
  private BasinWaterline() {}

  public static int at(WorldGenLevel level, BlockPos position, int nativeSeaLevel) {
    return at(
        level.getLevel().getChunkSource().randomState(),
        position.getX(),
        position.getZ(),
        nativeSeaLevel);
  }

  public static int at(RandomState random, int x, int z, int nativeSeaLevel) {
    IslandTerrain terrain = IslandTerrain.from(random);
    if (terrain == null) return nativeSeaLevel;
    IslandLayout.Basin basin = terrain.basin(x, z);
    return basin != null && basin.water(x, basin.waterline(), z)
        ? basin.waterline()
        : nativeSeaLevel;
  }
}
