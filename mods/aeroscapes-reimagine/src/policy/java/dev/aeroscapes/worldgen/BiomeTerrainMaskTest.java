package dev.aeroscapes.worldgen;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/** Checks the spatial contract and cache independence without starting Minecraft. */
public final class BiomeTerrainMaskTest {
  private BiomeTerrainMaskTest() {}

  public static void main(String[] arguments) {
    var empty = new BiomeTerrainMask((x, y, z) -> false, -128, 384, 4, 8);
    require(!empty.nearTerrain(0, 64, 0), "Empty sky has no ordinary biome");
    var point = new BiomeTerrainMask((x, y, z) -> x == 0 && y == 64 && z == 0, -128, 384, 4, 8);
    require(point.nearTerrain(0, 64, 0), "Land retains its biome");
    require(point.nearTerrain(8, 72, -8), "A small buffer surrounds the land");
    require(!point.nearTerrain(12, 64, 0), "The horizontal buffer is bounded");
    require(!point.nearTerrain(0, 88, 0), "The vertical buffer is bounded");
    var negative =
        new BiomeTerrainMask((x, y, z) -> x == -4 && y == -8 && z == -4, -128, 384, 4, 8);
    require(negative.nearTerrain(-4, -4, -4), "Negative coordinates use floor division");
    var bounded = new BiomeTerrainMask((x, y, z) -> y < -128 || y >= 384, -128, 384, 4, 8);
    require(!bounded.nearTerrain(0, -128, 0), "Land below the generator is excluded");
    require(!bounded.nearTerrain(0, 384, 0), "Land above the generator is excluded");
    AtomicInteger calls = new AtomicInteger();
    var cached =
        new BiomeTerrainMask(
            (x, y, z) -> {
              calls.incrementAndGet();
              return true;
            },
            -128,
            384,
            4,
            8);
    cached.nearTerrain(0, 0, 0);
    int first = calls.get();
    cached.nearTerrain(0, 0, 0);
    require(calls.get() == first, "Identical queries reuse cached nodes");
    var shared = new BiomeTerrainMask((x, y, z) -> x == 0 && y == 0 && z == 0, -128, 384, 4, 8);
    IntStream.range(0, 2000)
        .parallel()
        .forEach(
            index -> {
              int x = index % 2 == 0 ? 0 : index * 65536;
              require(
                  shared.nearTerrain(x, 0, 0) == (x == 0),
                  "Cache collisions and concurrent reads retain complete coordinates");
            });
    System.out.println("BiomeTerrainMask: spatial, bounds, cache and concurrent checks passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
