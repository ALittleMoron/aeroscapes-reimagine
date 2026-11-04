package dev.aeroscapes.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/** Finds separate natural island tops without treating carved cave floors as surfaces. */
public final class ExposedIslandSurfaces {
  @FunctionalInterface
  public interface Density {
    double at(int x, int y, int z);
  }

  private ExposedIslandSurfaces() {}

  public static List<Integer> below(
      int minY, int topY, IntPredicate solid, IntPredicate insideNaturalBody) {
    List<Integer> result = new ArrayList<>();
    boolean previousSolid = solid.test(minY);
    for (int y = minY + 1; y < topY; y++) {
      boolean currentSolid = solid.test(y);
      if (previousSolid && !currentSolid && !insideNaturalBody.test(y)) result.add(y);
      previousSolid = currentSolid;
    }
    return List.copyOf(result);
  }

  /** Match the owned generator's 4 by 8 by 4 interpolation before carvers. */
  public static double interpolated(int x, int y, int z, Density density) {
    int left = Math.floorDiv(x, 4) * 4;
    int bottom = Math.floorDiv(y, 8) * 8;
    int front = Math.floorDiv(z, 4) * 4;
    double result = 0;
    for (int dx = 0; dx <= 1; dx++)
      for (int dy = 0; dy <= 1; dy++)
        for (int dz = 0; dz <= 1; dz++) {
          double weightX = dx == 0 ? 1 - (x - left) / 4.0 : (x - left) / 4.0;
          double weightY = dy == 0 ? 1 - (y - bottom) / 8.0 : (y - bottom) / 8.0;
          double weightZ = dz == 0 ? 1 - (z - front) / 4.0 : (z - front) / 4.0;
          result +=
              weightX
                  * weightY
                  * weightZ
                  * density.at(left + dx * 4, bottom + dy * 8, front + dz * 4);
        }
    return result;
  }
}
