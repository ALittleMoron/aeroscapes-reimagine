package dev.aeroscapes.ecology;

import dev.aeroscapes.worldgen.NativeTerrainVolume;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Search-local native column summaries; no full terrain volume survives a tile calculation. */
final class SpawnIslandColumns {
  static final class Tile {
    private final int x;
    private final int z;
    private final int[] tops = new int[256];
    private final boolean[] waterBeds = new boolean[256];

    private Tile(int x, int z, NativeTerrainVolume volume, LevelHeightAccessor height) {
      this.x = x;
      this.z = z;
      for (int dx = 0; dx < 16; dx++)
        for (int dz = 0; dz < 16; dz++) {
          int index = dx * 16 + dz;
          int top = height.getMaxBuildHeight() - 1;
          while (top >= height.getMinBuildHeight() && volume.block(x + dx, top, z + dz).isAir())
            top--;
          tops[index] = top < height.getMinBuildHeight() ? Integer.MIN_VALUE : top;
          if (top < height.getMinBuildHeight()
              || !volume.block(x + dx, top, z + dz).getFluidState().is(FluidTags.WATER)) continue;
          int floor = top;
          while (floor > height.getMinBuildHeight()
              && volume.block(x + dx, floor, z + dz).getFluidState().is(FluidTags.WATER)) floor--;
          var state = volume.block(x + dx, floor, z + dz);
          waterBeds[index] =
              state.getFluidState().isEmpty()
                  && Block.isFaceFull(
                      state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                      Direction.UP);
        }
    }

    int top(int blockX, int blockZ) {
      return tops[(blockX - x) * 16 + blockZ - z];
    }

    boolean waterBed(int blockX, int blockZ) {
      return waterBeds[(blockX - x) * 16 + blockZ - z];
    }
  }

  private final NoiseBasedChunkGenerator generator;
  private final RandomState random;
  private final LevelHeightAccessor height;
  private final LinkedHashMap<Long, Integer> heights = new LinkedHashMap<>(16, 0.75F, true);
  private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>(16, 0.75F, true);

  SpawnIslandColumns(
      NoiseBasedChunkGenerator generator, RandomState random, LevelHeightAccessor height) {
    this.generator = generator;
    this.random = random;
    this.height = height;
  }

  boolean matches(NoiseBasedChunkGenerator noise, RandomState state, LevelHeightAccessor range) {
    return generator == noise
        && random == state
        && height.getMinBuildHeight() == range.getMinBuildHeight()
        && height.getMaxBuildHeight() == range.getMaxBuildHeight();
  }

  int height(int x, int z) {
    long key = ChunkPos.asLong(x, z);
    Integer cached = heights.get(key);
    if (cached != null) return cached;
    int calculated =
        generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, random);
    heights.put(key, calculated);
    // The 8-block probe grid fits within 32 by 32 samples for Dodo's clearance window.
    if (heights.size() > 1024) heights.pollFirstEntry();
    return calculated;
  }

  Tile tile(int x, int z) {
    int left = Math.floorDiv(x, 16) * 16;
    int front = Math.floorDiv(z, 16) * 16;
    long key = ChunkPos.asLong(left >> 4, front >> 4);
    Tile cached = tiles.get(key);
    if (cached != null) return cached;
    var volume =
        NativeTerrainVolume.nativeColumns(
            generator,
            random,
            height,
            new BoundingBox(
                left,
                height.getMinBuildHeight(),
                front,
                left + 15,
                height.getMaxBuildHeight() - 1,
                front + 15));
    Tile calculated = new Tile(left, front, volume, height);
    tiles.put(key, calculated);
    // Radius51 plus decoration clearance spans at most 15 by 15 aligned tiles.
    if (tiles.size() > 256) tiles.pollFirstEntry();
    return calculated;
  }
}
