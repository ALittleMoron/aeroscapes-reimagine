package dev.aeroscapes.worldgen;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Per-attempt native solid samples; retain one bit per voxel instead of block-state arrays. */
final class StructureTerrain {
  private final Structure.GenerationContext context;
  private final Map<BlockPos, BitSet> volumes = new HashMap<>();

  StructureTerrain(Structure.GenerationContext context) {
    this.context = context;
  }

  boolean solid(int x, int y, int z) {
    if (context.heightAccessor().isOutsideBuildHeight(y)) return false;
    if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator))
      return NativeTerrainVolume.solid(
          context
              .chunkGenerator()
              .getBaseColumn(x, z, context.heightAccessor(), context.randomState())
              .getBlock(y));
    int originX = Math.floorDiv(x, 16) * 16;
    int originY = Math.floorDiv(y, 24) * 24;
    int originZ = Math.floorDiv(z, 16) * 16;
    BlockPos key = new BlockPos(originX, originY, originZ);
    BitSet samples = volumes.get(key);
    if (samples == null) {
      var volume =
          new NativeTerrainVolume(
              generator,
              context.randomState(),
              new BoundingBox(originX, originY, originZ, originX + 15, originY + 23, originZ + 15));
      samples = new BitSet(16 * 24 * 16);
      for (int localX = 0; localX < 16; localX++)
        for (int localY = 0; localY < 24; localY++) {
          int sampleY = originY + localY;
          if (context.heightAccessor().isOutsideBuildHeight(sampleY)) continue;
          for (int localZ = 0; localZ < 16; localZ++)
            if (NativeTerrainVolume.solid(
                volume.block(originX + localX, sampleY, originZ + localZ)))
              samples.set(index(localX, localY, localZ));
        }
      volumes.put(key, samples);
    }
    return samples.get(index(x - originX, y - originY, z - originZ));
  }

  private static int index(int x, int y, int z) {
    return (x * 24 + y) * 16 + z;
  }
}
