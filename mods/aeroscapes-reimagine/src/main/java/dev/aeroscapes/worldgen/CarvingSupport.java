package dev.aeroscapes.worldgen;

import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Immutable support before the first carver write, owned by one CARVERS invocation. */
public final class CarvingSupport {
  private static final int THICK_LAYER = 32;
  private static final int ROOF_DEPTH = 8;

  private final ChunkPos chunk;
  private final int minimumY;
  private final int height;
  private final long[] solid;
  private final BitSet roofs = new BitSet();

  public CarvingSupport(ChunkAccess source) {
    chunk = source.getPos();
    minimumY = source.getMinBuildHeight();
    height = source.getHeight();
    solid = new long[(256 * height + Long.SIZE - 1) / Long.SIZE];
    var point = new BlockPos.MutableBlockPos();
    for (int z = 0; z < 16; z++)
      for (int x = 0; x < 16; x++) {
        int top = minimumY - 1;
        for (int y = minimumY + height - 1; y >= minimumY; y--) {
          point.set(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z);
          if (NativeTerrainVolume.solid(source.getBlockState(point))) {
            if (top < y) top = y;
            int index = index(x, y, z);
            solid[index >>> 6] |= 1L << (index & 63);
          } else if (top >= minimumY) {
            preserveRoof(x, y + 1, z, top);
            top = minimumY - 1;
          }
        }
        if (top >= minimumY) preserveRoof(x, minimumY, z, top);
      }
  }

  private void preserveRoof(int x, int base, int z, int top) {
    // Thick shelves can keep a walkable roof while cave mouths still enter from their sides.
    if (top - base + 1 >= THICK_LAYER) {
      int first = index(x, top - ROOF_DEPTH + 1, z);
      roofs.set(first, first + ROOF_DEPTH);
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

  public boolean protectsRoof(int x, int y, int z) {
    return roofs.get(index(x, y, z));
  }
}
