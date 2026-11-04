package dev.aeroscapes.worldgen;

import java.util.function.Supplier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Select a physical basin before native rigid jigsaw assembly and its height clamps. */
public final class SeabedProjection {
  private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

  private SeabedProjection() {}

  static <T> T call(Supplier<T> operation) {
    Boolean previous = ACTIVE.get();
    ACTIVE.set(true);
    try {
      return operation.get();
    } finally {
      if (previous == null) ACTIVE.remove();
      else ACTIVE.set(previous);
    }
  }

  public static int height(
      ChunkGenerator generator,
      RandomState random,
      LevelHeightAccessor height,
      int x,
      int z,
      int original) {
    return Boolean.TRUE.equals(ACTIVE.get())
        ? floor(generator, random, height, x, z, original)
        : original;
  }

  static int floor(
      ChunkGenerator generator,
      RandomState random,
      LevelHeightAccessor height,
      int x,
      int z,
      int fallback) {
    IslandTerrain terrain = IslandTerrain.from(random);
    if (terrain == null) return fallback;
    var basin = terrain.basin(x, z);
    if (basin == null || !basin.water(x, basin.waterline(), z)) return fallback;
    var column = generator.getBaseColumn(x, z, height, random);
    int y = basin.waterline();
    if (!column.getBlock(y).getFluidState().is(FluidTags.WATER)) return fallback;
    while (y > height.getMinBuildHeight() && column.getBlock(y).getFluidState().is(FluidTags.WATER))
      y--;
    return NativeTerrainVolume.solid(column.getBlock(y)) ? y + 1 : fallback;
  }
}
