package dev.aeroscapes.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Immutable support before the first carver write, owned by one CARVERS invocation. */
public final class CarvingSupport {
  private final ChunkPos chunk;
  private final int minimumY;
  private final int height;
  private final long[] solid;

  public CarvingSupport(ChunkAccess source) {
    chunk = source.getPos();
    minimumY = source.getMinBuildHeight();
    height = source.getHeight();
    solid = new long[(256 * height + Long.SIZE - 1) / Long.SIZE];
    var point = new BlockPos.MutableBlockPos();
    for (int z = 0; z < 16; z++)
      for (int x = 0; x < 16; x++)
        for (int y = minimumY; y < minimumY + height; y++) {
          point.set(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z);
          if (NativeTerrainVolume.solid(source.getBlockState(point))) {
            int index = index(x, y, z);
            solid[index >>> 6] |= 1L << (index & 63);
          }
        }
  }

  private int index(int x, int y, int z) {
    return ((z & 15) * 16 + (x & 15)) * height + y - minimumY;
  }

  public boolean solid(int x, int y, int z) {
    if (x >> 4 != chunk.x || z >> 4 != chunk.z)
      throw new IllegalArgumentException("Carving support queried outside its target chunk");
    if (y < minimumY || y >= minimumY + height) return false;
    int index = index(x, y, z);
    return (solid[index >>> 6] & (1L << (index & 63))) != 0;
  }
}
