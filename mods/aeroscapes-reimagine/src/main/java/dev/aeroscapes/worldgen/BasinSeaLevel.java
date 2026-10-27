package dev.aeroscapes.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;

/** Local sea level only inside the owned basins and their immediate natural shores. */
public final class BasinSeaLevel {
  private BasinSeaLevel() {}

  public static int water(int nativeSea, LevelAccessor level, BlockPos position) {
    IslandLayout.Basin basin = basin(level, position);
    return basin != null && basin.water(position.getX(), position.getY(), position.getZ())
        ? basin.waterline()
        : nativeSea;
  }

  public static int shore(int nativeSea, LevelAccessor level, BlockPos position) {
    IslandLayout.Basin basin = basin(level, position);
    if (basin == null) return nativeSea;
    double floor = basin.floor(position.getX(), position.getZ());
    return basin.radius(position.getX(), position.getZ()) < 1.1
            && floor >= basin.waterline() - 2
            && position.getY() >= floor - 2
            && position.getY() <= floor + 3
        ? basin.waterline()
        : nativeSea;
  }

  private static IslandLayout.Basin basin(LevelAccessor level, BlockPos position) {
    if (!(level instanceof ServerLevelAccessor server)) return null;
    IslandTerrain terrain = IslandTerrain.from(server.getLevel().getChunkSource().randomState());
    return terrain == null ? null : terrain.basin(position.getX(), position.getZ());
  }
}
