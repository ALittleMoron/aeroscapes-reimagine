package dev.aeroscapes.worldgen;

import static dev.aeroscapes.worldgen.IslandFootprint.NO_LAND;

/** Dependency-free policy checks; the runtime contains only these tests and companion classes. */
public final class IslandFootprintTest {
  private IslandFootprintTest() {}

  public static void main(String[] arguments) {
    require(IslandFootprint.accepts((x, z) -> 70), "A broad connected island must qualify");
    require(!IslandFootprint.accepts((x, z) -> NO_LAND), "Empty terrain must not qualify");
    require(
        !IslandFootprint.accepts((x, z) -> x * x + z * z <= 24 * 24 ? 70 : NO_LAND),
        "A small island must not qualify");
    require(
        !IslandFootprint.accepts(
            (x, z) -> Math.abs(x) > 4 || Math.abs(z) > 4 || x == 0 && z == 0 ? 70 : NO_LAND),
        "Disconnected large neighbors must not qualify through a tiny center island");
    require(
        !IslandFootprint.accepts((x, z) -> x != 0 || z != 0 ? 70 : NO_LAND),
        "A hole at the spawn must not qualify");
    require(
        !IslandFootprint.accepts((x, z) -> Math.abs(x) <= 4 ? 70 : NO_LAND),
        "A thin land bridge must not count as a starting island");
    require(
        IslandFootprint.accepts((x, z) -> x >= -24 ? 70 : NO_LAND),
        "A mostly covered, irregular disk with a 64-block span must qualify");
    require(
        IslandFootprint.accepts(
            (x, z) ->
                (Math.abs(x) != 32 || z != 0) && (Math.abs(z) != 32 || x != 0) ? 70 : NO_LAND),
        "Shore indentations at all four axis endpoints must allow span on nearby connected land");
    require(
        !IslandFootprint.accepts((x, z) -> x < 0 ? 70 : 180),
        "Adjacent plateaus at very different elevations must not be one island footprint");
    System.out.println("IslandFootprint: 9 policy checks passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
