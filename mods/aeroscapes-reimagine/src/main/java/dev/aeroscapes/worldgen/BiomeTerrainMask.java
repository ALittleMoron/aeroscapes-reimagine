package dev.aeroscapes.worldgen;

import java.util.concurrent.atomic.AtomicReferenceArray;

/** A small terrain buffer on the generator's sampling lattice, without loading chunks. */
public final class BiomeTerrainMask {
  @FunctionalInterface
  public interface TerrainNode {
    boolean occupied(int x, int y, int z);
  }

  private record Node(int x, int yGroup, int z, long known, long occupied) {
    boolean matches(int gridX, int groupY, int gridZ) {
      return x == gridX && yGroup == groupY && z == gridZ;
    }
  }

  private static final int[] OFFSETS = {0, -1, 1, -2, 2};
  private final TerrainNode terrain;
  private final int minY;
  private final int maxY;
  private final int cellWidth;
  private final int cellHeight;
  private final AtomicReferenceArray<Node> nodes = new AtomicReferenceArray<>(16384);

  public BiomeTerrainMask(TerrainNode terrain, int minY, int maxY, int cellWidth, int cellHeight) {
    this.terrain = terrain;
    this.minY = minY;
    this.maxY = maxY;
    this.cellWidth = cellWidth;
    this.cellHeight = cellHeight;
  }

  public boolean nearTerrain(int x, int y, int z) {
    int gridX = Math.floorDiv(x, cellWidth);
    int gridY = Math.floorDiv(y, cellHeight);
    int gridZ = Math.floorDiv(z, cellWidth);
    for (int offsetX : OFFSETS) {
      for (int offsetZ : OFFSETS) {
        for (int offsetY = -1; offsetY <= 2; offsetY++) {
          if (occupied(gridX + offsetX, gridY + offsetY, gridZ + offsetZ)) return true;
        }
      }
    }
    return false;
  }

  private boolean occupied(int gridX, int gridY, int gridZ) {
    int y = gridY * cellHeight;
    if (y < minY || y >= maxY) return false;
    // Keep a column's vertical samples together instead of evicting one another.
    int groupY = Math.floorDiv(gridY, Long.SIZE);
    long bit = 1L << (gridY & (Long.SIZE - 1));
    long hash =
        (long) gridX * 0x9E3779B97F4A7C15L
            ^ (long) groupY * 0xC2B2AE3D27D4EB4FL
            ^ (long) gridZ * 0x165667B19E3779F9L;
    hash = (hash ^ (hash >>> 33)) * 0xFF51AFD7ED558CCDL;
    int index = (int) (hash ^ (hash >>> 33)) & (nodes.length() - 1);
    Node cached = nodes.get(index);
    if (cached != null && cached.matches(gridX, groupY, gridZ) && (cached.known() & bit) != 0)
      return (cached.occupied() & bit) != 0;
    boolean occupied = terrain.occupied(gridX * cellWidth, y, gridZ * cellWidth);
    while (true) {
      long known = bit;
      long filled = occupied ? bit : 0;
      if (cached != null && cached.matches(gridX, groupY, gridZ)) {
        if ((cached.known() & bit) != 0) return (cached.occupied() & bit) != 0;
        known |= cached.known();
        filled |= cached.occupied();
      }
      if (nodes.compareAndSet(index, cached, new Node(gridX, groupY, gridZ, known, filled)))
        return occupied;
      cached = nodes.get(index);
      if (cached != null && !cached.matches(gridX, groupY, gridZ)) return occupied;
    }
  }
}
