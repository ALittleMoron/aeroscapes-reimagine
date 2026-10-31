package dev.aeroscapes.worldgen;

import java.util.concurrent.atomic.AtomicReferenceArray;

/** A small terrain buffer on the generator's sampling lattice, without loading chunks. */
public final class BiomeTerrainMask {
  @FunctionalInterface
  public interface TerrainNode {
    boolean occupied(int x, int y, int z);
  }

  private record Node(int x, int y, int z, boolean occupied) {}

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
    long hash =
        (long) gridX * 0x9E3779B97F4A7C15L
            ^ (long) gridY * 0xC2B2AE3D27D4EB4FL
            ^ (long) gridZ * 0x165667B19E3779F9L;
    hash = (hash ^ (hash >>> 33)) * 0xFF51AFD7ED558CCDL;
    int index = (int) (hash ^ (hash >>> 33)) & (nodes.length() - 1);
    Node cached = nodes.get(index);
    if (cached != null && cached.x() == gridX && cached.y() == gridY && cached.z() == gridZ)
      return cached.occupied();
    boolean occupied = terrain.occupied(gridX * cellWidth, y, gridZ * cellWidth);
    nodes.set(index, new Node(gridX, gridY, gridZ, occupied));
    return occupied;
  }
}
