package dev.aeroscapes.aquatic;

import dev.aeroscapes.worldgen.BasinSeaLevel;
import dev.aeroscapes.worldgen.IslandBiomeSources;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;

/** Separates animal position below the surface from the water depth under a fishing line. */
public final class AquaticWater {
  private AquaticWater() {}

  public static boolean ownedSource(BiomeSource source) {
    return IslandBiomeSources.find(source) != null;
  }

  public static int seaLevel(int nativeSea, ServerLevelAccessor level, BlockPos position) {
    return BasinSeaLevel.shore(BasinSeaLevel.water(nativeSea, level, position), level, position);
  }

  public static int catchCoordinate(ServerLevel level, BlockPos position) {
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    if (terrain == null) return position.getY();
    var basin = terrain.basin(position.getX(), position.getZ());
    if (basin == null
        || !basin.water(position.getX(), basin.waterline(), position.getZ())
        || Math.abs(position.getY() - basin.waterline()) > 2) return position.getY();
    BlockPos cursor = position;
    if (!level.getFluidState(cursor).is(FluidTags.WATER)) cursor = cursor.below();
    int depth = 0;
    while (cursor.getY() >= level.getMinBuildHeight()
        && depth < 256
        && level.getFluidState(cursor).is(FluidTags.WATER)) {
      depth++;
      cursor = cursor.below();
    }
    return depth == 0 ? position.getY() : 63 - depth;
  }

  public static int spawnCoordinate(ServerLevel level, BlockPos position) {
    int nativeSea = level.getSeaLevel();
    int localSea = BasinSeaLevel.water(nativeSea, level, position);
    return localSea == nativeSea ? position.getY() : 63 + position.getY() - localSea;
  }

  public static int floor(WorldGenLevel level, int x, int z, int fallback) {
    IslandTerrain terrain = IslandTerrain.from(level.getLevel().getChunkSource().randomState());
    if (terrain == null) return fallback;
    var basin = terrain.basin(x, z);
    if (basin == null || !basin.water(x, basin.waterline(), z)) return fallback;
    BlockPos cursor = new BlockPos(x, basin.waterline(), z);
    if (!level.getFluidState(cursor).is(FluidTags.WATER)) return fallback;
    while (cursor.getY() > basin.floor(x, z) - 2 && level.getFluidState(cursor).is(FluidTags.WATER))
      cursor = cursor.below();
    return level.getBlockState(cursor).isFaceSturdy(level, cursor, Direction.UP)
        ? cursor.getY() + 1
        : fallback;
  }
}
