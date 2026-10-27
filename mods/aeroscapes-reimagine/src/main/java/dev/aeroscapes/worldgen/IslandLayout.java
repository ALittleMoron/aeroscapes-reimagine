package dev.aeroscapes.worldgen;

/** Seeded, bounded island geometry shared by terrain and local-water placement. */
public record IslandLayout(long seed) {
  private static double random(long value) {
    long mixed = value;
    mixed = (mixed ^ mixed >>> 30) * 0xbf58476d1ce4e5b9L;
    mixed = (mixed ^ mixed >>> 27) * 0x94d049bb133111ebL;
    mixed ^= mixed >>> 31;
    return (mixed >>> 11) * 0x1.0p-53;
  }

  private long cell(int x, int z, int scale, long salt) {
    return seed
        ^ (long) Math.floorDiv(x, scale) * 341873128712L
        ^ (long) Math.floorDiv(z, scale) * 132897987541L
        ^ salt;
  }

  public double small(int x, int y, int z) {
    double density = -1;
    for (int layer = 0; layer < 4; layer++)
      density = Math.max(density, smallShape(x, z, layer).density(y));
    return density;
  }

  public VerticalShape smallShape(int x, int z, int layer) {
    long key = cell(x, z, 128, layer * 42595009L);
    if (random(key) >= 0.255) return VerticalShape.EMPTY;
    double centerX = Math.floorDiv(x, 128) * 128.0 + 48 + random(key + 1) * 32;
    double centerZ = Math.floorDiv(z, 128) * 128.0 + 48 + random(key + 2) * 32;
    double radius = 12 + random(key + 4) * 18;
    double dx = x - centerX + 7 * noise(seed, x, z, 23, 11);
    double dz = z - centerZ + 7 * noise(seed, x, z, 23, 12);
    double horizontal = Math.hypot(dx / radius, dz / (radius * (0.75 + random(key + 9) * 0.35)));
    double edge = 1 - horizontal + 0.2 * noise(seed, x, z, 11, 13);
    if (edge < -0.25) return VerticalShape.EMPTY;
    double top =
        -72
            + layer * 94
            + random(key + 3) * 30
            - 15
            + 9 * noise(seed, x, z, 29, 14)
            + 4 * noise(seed, x, z, 9, 15);
    double bottom =
        Math.max(
            -121,
            top
                - radius * (0.45 + 0.7 * Math.sqrt(Math.max(0, edge)))
                + 7 * noise(seed, x, z, 17, 16));
    return new VerticalShape(edge * 2, top, bottom, 9);
  }

  /** Smooth seeded gradient noise; evaluated only while constructing an X/Z column. */
  public static double noise(long seed, double x, double z, double scale, long salt) {
    double gridX = x / scale;
    double gridZ = z / scale;
    int cellX = (int) Math.floor(gridX);
    int cellZ = (int) Math.floor(gridZ);
    double dx = gridX - cellX;
    double dz = gridZ - cellZ;
    double weightX = fade(dx);
    double weightZ = fade(dz);
    double low =
        gradient(seed, cellX, cellZ, salt, dx, dz) * (1 - weightX)
            + gradient(seed, cellX + 1, cellZ, salt, dx - 1, dz) * weightX;
    double high =
        gradient(seed, cellX, cellZ + 1, salt, dx, dz - 1) * (1 - weightX)
            + gradient(seed, cellX + 1, cellZ + 1, salt, dx - 1, dz - 1) * weightX;
    return Math.max(-1, Math.min(1, (low * (1 - weightZ) + high * weightZ) * 1.4));
  }

  private static double fade(double value) {
    return value * value * value * (value * (value * 6 - 15) + 10);
  }

  private static double gradient(long seed, int x, int z, long salt, double dx, double dz) {
    int direction =
        (int) (random(seed ^ (long) x * 341873128712L ^ (long) z * 132897987541L ^ salt) * 8);
    return switch (direction) {
      case 0 -> dx;
      case 1 -> -dx;
      case 2 -> dz;
      case 3 -> -dz;
      case 4 -> (dx + dz) * 0.7071067811865476;
      case 5 -> (dx - dz) * 0.7071067811865476;
      case 6 -> (-dx + dz) * 0.7071067811865476;
      default -> (-dx - dz) * 0.7071067811865476;
    };
  }

  /** X/Z geometry is constant through a vertical noise column. */
  public record VerticalShape(double cap, double top, double bottom, double scale) {
    public static final VerticalShape EMPTY = new VerticalShape(-1, 0, 0, 1);

    public double density(int y) {
      if (this == EMPTY) return -1;
      return Math.min(cap, Math.min((top - y) / scale, (y - bottom) / scale));
    }

    public double terrainDensity(int y, double detail) {
      if (this == EMPTY) return -1;
      // Negative 3D detail must reach the side itself: fading it to zero there
      // leaves a vertically extruded solid collar. Positive detail stays bounded.
      return Math.min(cap, density(y) + detail);
    }

    public double sealedDensity(int y, double detail) {
      // Positive 3D noise must not extend a body's footprint beyond its bounded outline.
      // Fade through the inner edge, before any layout-cell or EMPTY cutoff is reached.
      double weight = Math.max(0, Math.min(1, cap / 0.4));
      return density(y) + detail * weight * weight * (3 - 2 * weight);
    }
  }

  public LargeIsland largeIsland(int x, int z) {
    long key = cell(x, z, 1536, 0x576da367de7L);
    if (random(key) >= 0.153) return null;
    double anchor = -48 + Math.floor(random(key + 5) * 4) * 80;
    return new LargeIsland(
        Math.floorDiv(x, 1536) * 1536.0 + 704 + random(key + 1) * 128,
        Math.floorDiv(z, 1536) * 1536.0 + 704 + random(key + 2) * 128,
        320 + random(key + 3) * 192,
        320 + random(key + 4) * 192,
        anchor,
        random(key + 6) < 0.6 ? Math.min(160 + random(key + 7) * 80, 354 - anchor) : 0,
        Math.min(72 + random(key + 8) * 20, anchor + 112),
        key);
  }

  public double large(int x, int y, int z) {
    LargeIsland island = largeIsland(x, z);
    return island == null ? -1 : island.density(x, y, z);
  }

  public record LargeIsland(
      double centerX,
      double centerZ,
      double radiusX,
      double radiusZ,
      double anchor,
      double peakHeight,
      double depth,
      long key) {
    public double radius(int x, int z) {
      double dx = x - centerX + 48 * noise(key, x, z, 180, 31);
      double dz = z - centerZ + 48 * noise(key, x, z, 180, 32);
      double closest = 8;
      for (int lobe = 0; lobe < 5; lobe++) {
        double offsetX = (lobe - 2) * radiusX * 0.27;
        double offsetZ = (random(key + 40 + lobe) - 0.5) * radiusZ * 0.9;
        closest =
            Math.min(
                closest,
                Math.hypot(
                    (dx - offsetX) / (radiusX * 0.43),
                    (dz - offsetZ) / (radiusZ * (0.36 + random(key + 50 + lobe) * 0.16))));
      }
      return closest + 0.12 * noise(key, x, z, 57, 33);
    }

    public double peak(int x, int z) {
      if (peakHeight == 0) return 0;
      double dx = (x - centerX - radiusX * 0.15) / (radiusX * 0.7);
      double dz = (z - centerZ + radiusZ * 0.1) / (radiusZ * 0.75);
      double region = Math.max(0, 1 - dx * dx - dz * dz);
      double ridge = 1 - Math.min(1, Math.abs(noise(key, x, z, 118, 61) * 2));
      double ridge2 = 1 - Math.min(1, Math.abs(noise(key, x, z, 67, 62) * 2));
      return peakHeight * region * region * (0.25 + 0.55 * ridge * ridge + 0.2 * ridge2);
    }

    public double surface(int x, int z) {
      return Math.min(
          357,
          anchor
              + 27 * noise(key, x, z, 210, 71)
              + 14 * noise(key, x, z, 77, 72)
              + 5 * noise(key, x, z, 23, 73)
              + peak(x, z));
    }

    public VerticalShape shape(int x, int z) {
      double horizontal = radius(x, z);
      if (horizontal > 1.12) return VerticalShape.EMPTY;
      double bottom =
          Math.max(
              -121,
              anchor
                  - depth * (0.45 + 0.55 * Math.sqrt(Math.max(0, 1 - horizontal)))
                  + 19 * noise(key, x, z, 97, 74)
                  + 8 * noise(key, x, z, 31, 75));
      return new VerticalShape((1 - horizontal) * 3, surface(x, z), bottom, 16);
    }

    public double density(int x, int y, int z) {
      return shape(x, z).density(y);
    }
  }

  public Basin basin(int x, int z) {
    long key = cell(x, z, 1024, 0x3b7c92bd981L);
    if (random(key) >= 0.65) return null;
    int centerX = Math.floorDiv(x, 1024) * 1024 + 448 + (int) (random(key + 1) * 128);
    int centerZ = Math.floorDiv(z, 1024) * 1024 + 448 + (int) (random(key + 2) * 128);
    int waterline = 144 + (int) (random(key + 3) * 64);
    double radiusX = 224 + random(key + 4) * 32;
    double radiusZ = 224 + random(key + 5) * 32;
    return new Basin(
        centerX, centerZ, waterline, radiusX, radiusZ, 64 + (int) (random(key + 6) * 24), key);
  }

  public record Basin(
      int centerX,
      int centerZ,
      int waterline,
      double radiusX,
      double radiusZ,
      int depth,
      long key) {
    public double radius(int x, int z) {
      double dx = x - centerX + 45 * noise(key, x, z, 137, 81);
      double dz = z - centerZ + 45 * noise(key, x, z, 137, 82);
      double radial = Math.hypot(dx / radiusX, dz / radiusZ);
      // Bound: radius*(1.22+.26+.08)+45 < 448, the closest layout-cell edge.
      // The coast stays wholly inside its cell without a rectangular clipping mask.
      return radial + 0.26 * noise(key, x, z, 97, 83) + 0.08 * noise(key, x, z, 29, 84);
    }

    public double floor(int x, int z) {
      return floor(x, z, radius(x, z));
    }

    double floor(int x, int z, double radial) {
      if (radial > 1)
        return waterline
            + 12
            - (depth + 35) * Math.min(1, (radial - 1) / 0.22)
            + 9 * noise(key, x, z, 39, 88) * Math.min(1, (radial - 1) / 0.1);
      double slope = Math.max(0, Math.min(1, (radial - 0.35) / 0.65));
      double smooth = slope * slope * (3 - 2 * slope);
      double relief =
          (18 * noise(key, x, z, 150, 85)
                  + 9 * noise(key, x, z, 56, 86)
                  + 3 * noise(key, x, z, 19, 87))
              * Math.max(0, 1 - smooth);
      return waterline - depth + (depth + 12) * smooth + relief;
    }

    public VerticalShape shape(int x, int z) {
      double radial = radius(x, z);
      if (radial >= 1.22) return VerticalShape.EMPTY;
      return shape(x, z, radial, floor(x, z, radial));
    }

    VerticalShape shape(int x, int z, double radial, double floor) {
      if (radial >= 1.22) return VerticalShape.EMPTY;
      double bottom =
          waterline
              - depth
              - 25
              - 35 * Math.sqrt(Math.max(0, 1 - radial / 1.22))
              + 16 * noise(key, x, z, 91, 89)
              + 6 * noise(key, x, z, 27, 90);
      return new VerticalShape((1.22 - radial) * 4, floor, bottom, 12);
    }

    public double density(int x, int y, int z) {
      return shape(x, z).density(y);
    }

    public boolean water(int x, int y, int z) {
      return radius(x, z) < 1 && y > floor(x, z) && y <= waterline;
    }
  }
}
