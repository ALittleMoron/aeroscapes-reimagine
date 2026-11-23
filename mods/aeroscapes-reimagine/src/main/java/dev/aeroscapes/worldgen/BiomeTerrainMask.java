package dev.aeroscapes.worldgen;

import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

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
  private static final int NO_SURFACE = Integer.MIN_VALUE;
  private final TerrainNode terrain;
  private final int minY;
  private final int maxY;
  private final int cellWidth;
  private final int cellHeight;
  private final SmallIslandFootprints footprints;
  private final AtomicReferenceArray<Node> nodes = new AtomicReferenceArray<>(16384);

  public BiomeTerrainMask(TerrainNode terrain, int minY, int maxY, int cellWidth, int cellHeight) {
    this.terrain = terrain;
    this.minY = minY;
    this.maxY = maxY;
    this.cellWidth = cellWidth;
    this.cellHeight = cellHeight;
    this.footprints = new SmallIslandFootprints(this::occupied, cellWidth, cellHeight);
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

  boolean nearExposedShelf(int x, int y, int z) {
    int gridX = Math.floorDiv(x, cellWidth);
    int gridY = Math.floorDiv(y, cellHeight);
    int gridZ = Math.floorDiv(z, cellWidth);
    int top = surfaceNode(gridX, gridY, gridZ);
    return top != NO_SURFACE
        && occupied(gridX, top - 1, gridZ)
        && !occupied(gridX, top + 1, gridZ)
        && !occupied(gridX, top + 2, gridZ);
  }

  @Nullable
  BlockPos smallIslandSurface(int x, int y, int z) {
    int gridX = Math.floorDiv(x, cellWidth);
    int gridY = Math.floorDiv(y, cellHeight);
    int gridZ = Math.floorDiv(z, cellWidth);
    int surface = surfaceNode(gridX, gridY, gridZ);
    if (surface != NO_SURFACE) {
      return occupied(gridX, surface + 1, gridZ) ? null : footprints.anchor(gridX, surface, gridZ);
    }
    // Terrain interpolation and biome zoom can both place the visible edge between occupied nodes.
    for (int radius = 1; radius <= 2; radius++) {
      BlockPos anchor = null;
      for (int offsetX = -radius; offsetX <= radius; offsetX++) {
        for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
          if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) continue;
          int neighborX = gridX + offsetX;
          int neighborZ = gridZ + offsetZ;
          int neighborSurface = surfaceNode(neighborX, gridY, neighborZ);
          if (neighborSurface == NO_SURFACE) continue;
          if (occupied(neighborX, neighborSurface + 1, neighborZ)) return null;
          BlockPos next = footprints.anchor(neighborX, neighborSurface, neighborZ);
          if (next == null || (anchor != null && !anchor.equals(next))) return null;
          anchor = next;
        }
      }
      if (anchor != null) return anchor;
    }
    return null;
  }

  private int surfaceNode(int gridX, int gridY, int gridZ) {
    int surface = gridY + 1;
    while (surface >= gridY - 1 && !occupied(gridX, surface, gridZ)) surface--;
    return surface >= gridY - 1 ? surface : NO_SURFACE;
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
