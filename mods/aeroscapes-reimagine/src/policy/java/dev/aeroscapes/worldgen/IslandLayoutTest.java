package dev.aeroscapes.worldgen;

/** Geometry contracts independent of Minecraft's sampling and decoration. */
public final class IslandLayoutTest {
  private IslandLayoutTest() {}

  public static void main(String[] arguments) {
    verifyCaveHeights();
    verifyBasinSampling();
    verifyLavaIslands();
    for (double cap : new double[] {-1, -0.5, -0.36, -0.01, 0}) {
      var edge = new IslandLayout.VerticalShape(cap, 80, 0, 12);
      require(edge.terrainDensity(40, 10) <= 0, "Detail crosses the bounded island outline");
    }
    var cliff = new IslandLayout.VerticalShape(0.01, 80, 0, 12);
    require(
        cliff.terrainDensity(40, -0.1) < 0,
        "Negative 3D detail cannot erode the vertically extruded island wall");
    IslandLayout first = new IslandLayout(1);
    IslandLayout second = new IslandLayout(42);
    int basins = 0;
    int different = 0;
    for (int x = -8192; x <= 8192; x += 1024) {
      for (int z = -8192; z <= 8192; z += 1024) {
        IslandLayout.Basin basin = first.basin(x, z);
        require(
            java.util.Objects.equals(basin, new IslandLayout(1).basin(x, z)),
            "Basin layout changes between identical seeds");
        if (!java.util.Objects.equals(basin, second.basin(x, z))) different++;
        if (basin == null) continue;
        basins++;
        int centerX = basin.centerX();
        int centerZ = basin.centerZ();
        int floor = (int) basin.floor(centerX, centerZ);
        require(basin.water(centerX, basin.waterline(), centerZ), "Missing basin water surface");
        require(
            !basin.water(centerX, basin.waterline() + 1, centerZ), "Water exceeds local surface");
        require(!basin.water(centerX, floor, centerZ), "Water replaces the basin floor");
        require(basin.density(centerX, floor - 12, centerZ) > 0, "Basin lacks a thick floor");
        double minimumFloor = Double.POSITIVE_INFINITY;
        double maximumFloor = Double.NEGATIVE_INFINITY;
        for (int dx = -96; dx <= 96; dx += 8)
          for (int dz = -96; dz <= 96; dz += 8) {
            double height = basin.floor(centerX + dx, centerZ + dz);
            minimumFloor = Math.min(minimumFloor, height);
            maximumFloor = Math.max(maximumFloor, height);
          }
        require(maximumFloor - minimumFloor > 8, "Ocean floor reverted to a flat platform");
        for (int angle = 0; angle < 360; angle += 5) {
          double radians = Math.toRadians(angle);
          boolean closed = false;
          for (double distance = 0.65; distance <= 1.5; distance += 0.01) {
            int rimX = centerX + (int) (Math.cos(radians) * basin.radiusX() * distance);
            int rimZ = centerZ + (int) (Math.sin(radians) * basin.radiusZ() * distance);
            if (basin.density(rimX, basin.waterline(), rimZ) > 0) {
              closed = true;
              break;
            }
          }
          require(closed, "Water escapes through an open basin boundary");
        }
      }
    }
    require(basins > 0 && different > 0, "Layout lacks seeded variation");
    int unevenSmall = 0;
    int smallIslands = 0;
    for (int cellX = -4096; cellX < 4096; cellX += 256)
      for (int cellZ = -4096; cellZ < 4096; cellZ += 256) {
        var island = first.smallIsland(cellX, cellZ, 2);
        if (island == null) continue;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int dx = -16; dx <= 16; dx += 4)
          for (int dz = -16; dz <= 16; dz += 4) {
            var shape = first.smallShape(island.centerX() + dx, island.centerZ() + dz, 2);
            if (shape.cap() < 0.3) continue;
            minimum = Math.min(minimum, shape.top());
            maximum = Math.max(maximum, shape.top());
          }
        if (maximum > minimum) {
          smallIslands++;
          if (maximum - minimum > 3) unevenSmall++;
        }
      }
    require(
        smallIslands > 10 && unevenSmall * 5 >= smallIslands * 4,
        "Small islands reverted to flat tops");
    require(smallIslands < 180, "Small-island density reverted to the former crowded grid");
    for (int x = -1024; x <= 1024; x += 4) {
      for (int z = -1024; z <= 1024; z += 4) {
        require(
            first.small(x, -128, z) < 0 && first.large(x, -128, z) < 0,
            "Island clips the lower build boundary");
        require(first.small(x, 304, z) < 0, "Small island raises ordinary upper anchors");
      }
    }
    System.out.println(
        "IslandLayout: seeded layouts, uneven small islands/seabeds, closed basins and height bounds passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void verifyCaveHeights() {
    var hosts =
        new IslandCaveHosts(
            new net.minecraft.world.level.ChunkPos(0, 0),
            java.util.List.of(
                new IslandCaveHosts.Range(-90, -60),
                new IslandCaveHosts.Range(80, 120),
                new IslandCaveHosts.Range(200, 250)));
    var random = net.minecraft.util.RandomSource.create(1);
    for (var range : hosts.ranges()) {
      java.util.Set<Integer> selected = new java.util.HashSet<>();
      for (int sample = 0; sample < 4096; sample++) {
        int y = range.height(random);
        require(
            y >= range.minimum() && y <= range.maximum(), "Cave height escaped its island layer");
        selected.add(y);
      }
      require(
          selected.size() == range.maximum() - range.minimum() + 1,
          "Cave anchors starve part of a layer");
    }
  }

  private static void verifyLavaIslands() {
    int seen = 0;
    for (long seed : new long[] {1, 42, 777, -1}) {
      var layout = new IslandLayout(seed);
      for (int x = -4096; x <= 4096; x += 1024) {
        for (int z = -4096; z <= 4096; z += 1024) {
          var lava = layout.lavaIsland(x, z);
          if (lava == null) continue;
          seen++;
          require(
              lava.waterline() < 0
                  && lava.radiusX() >= 100
                  && lava.radiusX() < 172
                  && lava.radiusZ() >= 100
                  && lava.radiusZ() < 172,
              "Lava islands must be low, broad landforms");
          for (int angle = 0; angle < 360; angle += 5) {
            double radians = Math.toRadians(angle);
            boolean closed = false;
            for (double radius = 0.65; radius <= 1.55; radius += 0.01) {
              int rimX = lava.centerX() + (int) (Math.cos(radians) * lava.radiusX() * radius);
              int rimZ = lava.centerZ() + (int) (Math.sin(radians) * lava.radiusZ() * radius);
              require(lava.density(rimX, -128, rimZ) < 0, "Lava island clips minY");
              if (lava.density(rimX, lava.waterline(), rimZ) > 0) closed = true;
            }
            require(closed, "Lava island has an open rim");
          }
        }
      }
    }
    require(seen > 0 && seen < 120, "Lava island layout lost its rare seeded selection");
  }

  private static void verifyBasinSampling() {
    int inside = 0;
    int outside = 0;
    for (long seed : new long[] {1, 42, 0, -1, 777, -8315743817914385108L}) {
      IslandLayout layout = new IslandLayout(seed);
      for (int cellX = -2048; cellX <= 2048; cellX += 1024) {
        for (int cellZ = -2048; cellZ <= 2048; cellZ += 1024) {
          IslandLayout.Basin basin = layout.basin(cellX, cellZ);
          if (basin == null) continue;
          for (int dx = -384; dx <= 384; dx += 13) {
            for (int dz = -384; dz <= 384; dz += 17) {
              int x = basin.centerX() + dx;
              int z = basin.centerZ() + dz;
              double radial = basin.radius(x, z);
              double floor = referenceFloor(basin, x, z);
              require(
                  Double.doubleToLongBits(floor)
                      == Double.doubleToLongBits(basin.floor(x, z, radial)),
                  "Reused basin radius changes its floor");
              var expected = referenceShape(basin, x, z);
              var actual = basin.shape(x, z, radial, floor);
              require(expected.equals(actual), "Reused basin geometry changes its shape");
              require(
                  expected.equals(basin.shape(x, z)), "Scalar basin shape changed during reuse");
              if (radial <= 1) inside++;
              else outside++;
            }
          }
        }
      }
    }
    require(inside > 0 && outside > 0, "Basin fixture missed a side of its coastline");
  }

  // Preserve the independent scalar reference while the production path reuses X/Z work.
  private static double referenceFloor(IslandLayout.Basin basin, int x, int z) {
    double radial = basin.radius(x, z);
    double slope = Math.max(0, Math.min(1, (radial - 0.35) / 0.65));
    double smooth = slope * slope * (3 - 2 * slope);
    double relief =
        (18 * IslandLayout.noise(basin.key(), x, z, 150, 85)
                + 9 * IslandLayout.noise(basin.key(), x, z, 56, 86)
                + 3 * IslandLayout.noise(basin.key(), x, z, 19, 87))
            * Math.max(0, 1 - smooth);
    if (radial > 1)
      return basin.waterline()
          + 12
          - (basin.depth() + 35) * Math.min(1, (radial - 1) / 0.22)
          + 9 * IslandLayout.noise(basin.key(), x, z, 39, 88) * Math.min(1, (radial - 1) / 0.1);
    return basin.waterline() - basin.depth() + (basin.depth() + 12) * smooth + relief;
  }

  private static IslandLayout.VerticalShape referenceShape(IslandLayout.Basin basin, int x, int z) {
    double radial = basin.radius(x, z);
    if (radial >= 1.22) return IslandLayout.VerticalShape.EMPTY;
    double bottom =
        basin.waterline()
            - basin.depth()
            - 25
            - 35 * Math.sqrt(Math.max(0, 1 - radial / 1.22))
            + 16 * IslandLayout.noise(basin.key(), x, z, 91, 89)
            + 6 * IslandLayout.noise(basin.key(), x, z, 27, 90);
    return new IslandLayout.VerticalShape(
        (1.22 - radial) * 4, referenceFloor(basin, x, z), bottom, 12);
  }
}
