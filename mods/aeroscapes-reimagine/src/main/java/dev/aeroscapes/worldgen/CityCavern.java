package dev.aeroscapes.worldgen;

import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Connected noisy chambers limited to the city's twelve-block terrain halo. */
public final class CityCavern {
  private final BoundingBox bounds;
  private final List<BoundingBox> parts;
  private final Column[] columns = new Column[256];

  public CityCavern(List<BoundingBox> parts) {
    this.parts = List.copyOf(parts);
    bounds = BoundingBox.encapsulatingBoxes(parts).orElseThrow();
  }

  public double contribution(int x, int y, int z) {
    Column column = column(x, z);
    double opening = Math.min((y - column.bottom()) / 2, (column.ceiling() - y) / 4);
    return opening > 0 ? -Math.min(6, opening * 6) : 0;
  }

  public double ceiling(int x, int z) {
    return column(x, z).ceiling();
  }

  public double bottom(int x, int z) {
    return column(x, z).bottom();
  }

  /** Include template AIR as well as the shared cavern when protecting basin floors and rims. */
  public boolean separatesBasin(IslandLayout.Basin basin, int x, int z) {
    if (basin.radius(x, z) >= 1.22) return true;
    Column cavern = column(x, z);
    boolean open = cavern.ceiling() > cavern.bottom();
    double low = open ? cavern.bottom() : Double.POSITIVE_INFINITY;
    double high = open ? cavern.ceiling() : Double.NEGATIVE_INFINITY;
    for (BoundingBox part : parts) {
      if (x < part.minX() || x > part.maxX() || z < part.minZ() || z > part.maxZ()) continue;
      low = Math.min(low, part.minY());
      high = Math.max(high, part.maxY());
    }
    if (high < low) return true;
    double floor = basin.floor(x, z);
    return high + 6 < floor - 12 || low - 6 > Math.max(floor, basin.waterline());
  }

  private Column column(int x, int z) {
    int index = (x & 15) | ((z & 15) << 4);
    Column column = columns[index];
    if (column == null || column.x() != x || column.z() != z) {
      double ceiling = -2048;
      double bottom = 2048;
      long seed = ChunkPos.asLong(bounds.minX(), bounds.minZ());
      double warpX = 4 * IslandLayout.noise(seed, x, z, 43, 101);
      double warpZ = 4 * IslandLayout.noise(seed, x, z, 43, 102);
      double roofNoise =
          2.5 * IslandLayout.noise(seed, x, z, 39, 103)
              + 0.75 * IslandLayout.noise(seed, x, z, 13, 104);
      for (BoundingBox part : parts) {
        double dx = (x - part.getCenter().getX() + warpX) / (part.getXSpan() * 0.5 + 8);
        double dz = (z - part.getCenter().getZ() + warpZ) / (part.getZSpan() * 0.5 + 8);
        double radial = dx * dx + dz * dz;
        if (radial >= 1) continue;
        ceiling =
            Math.max(
                ceiling,
                bounds.minY() + 6 + (bounds.getYSpan() + 1) * Math.sqrt(1 - radial) + roofNoise);
      }

      bottom = Math.min(bottom, bounds.minY() + 1 + 1.5 * IslandLayout.noise(seed, x, z, 29, 105));
      column = new Column(x, z, bottom, ceiling);
      columns[index] = column;
    }
    return column;
  }

  private record Column(int x, int z, double bottom, double ceiling) {}
}
