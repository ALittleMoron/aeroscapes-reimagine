package dev.aeroscapes.worldgen;

import java.util.List;

public final class ExposedIslandSurfacesTest {
  private ExposedIslandSurfacesTest() {}

  public static void main(String[] arguments) {
    var surfaces =
        ExposedIslandSurfaces.below(
            -64,
            61,
            y -> (y >= -40 && y <= -21 && (y < -30 || y > -27)) || (y >= 40 && y <= 60),
            y -> (y >= -40 && y <= -21) || (y >= 40 && y <= 60));
    require(surfaces.equals(List.of(-20)), "Find the lower island without a carved cave floor");
    require(
        ExposedIslandSurfaces.below(
                -64,
                61,
                y -> (y >= -40 && y <= -21 && (y < -25 || y > -22)) || (y >= 40 && y <= 60),
                y -> (y >= -40 && y <= -21) || (y >= 40 && y <= 60))
            .equals(List.of(-20)),
        "A cave beneath a thin roof must not become an exposed island surface");
    require(
        ExposedIslandSurfaces.below(
                -64,
                61,
                y -> (y >= -40 && y <= -21) || (y >= -16 && y <= -10) || (y >= 40 && y <= 60),
                y -> (y >= -40 && y <= -21) || (y >= -16 && y <= -10) || (y >= 40 && y <= 60))
            .equals(List.of(-20, -9)),
        "A nearby separate island must not hide an exterior surface");
    require(
        ExposedIslandSurfaces.below(-64, 64, y -> false, y -> false).isEmpty(),
        "Void has no lower surface");
    require(
        ExposedIslandSurfaces.below(-64, 11, y -> y >= 0 && y <= 10, y -> false).isEmpty(),
        "The native top surface must not be duplicated");
    System.out.println("Exposed island surfaces: lower, cave, void and native top checks passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
