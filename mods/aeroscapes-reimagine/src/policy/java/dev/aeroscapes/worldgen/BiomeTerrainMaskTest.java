package dev.aeroscapes.worldgen;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
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
    checkOracle();
    checkConcurrentColumns();
    System.out.println("BiomeTerrainMask: spatial, bounds, cache and concurrent checks passed");
  }

  private static void checkOracle() {
    BiomeTerrainMask.TerrainNode terrain = (x, y, z) -> ((x * 31L ^ y * 17L ^ z * 13L) & 127) == 0;
    var mask = new BiomeTerrainMask(terrain, -2048, 2048, 4, 8);
    var emptyWorld = new BiomeTerrainMask((x, y, z) -> false, -2048, 2048, 4, 8);
    int[] heights = {-129, -128, -65, -64, -1, 0, 63, 64, 127, 128};
    for (int gridY : heights) {
      for (int x = -65; x <= 65; x += 5) {
        for (int z = -65; z <= 65; z += 5) {
          int y = gridY * 8 + 3;
          require(
              mask.nearTerrain(x, y, z) == oracle(terrain, x, y, z),
              "Cached samples match the uncached spatial rule across vertical groups");
          require(!emptyWorld.nearTerrain(x, y, z), "Worlds never share cached terrain");
        }
      }
    }
  }

  private static boolean oracle(BiomeTerrainMask.TerrainNode terrain, int x, int y, int z) {
    for (int offsetX = -2; offsetX <= 2; offsetX++) {
      for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
        for (int offsetY = -1; offsetY <= 2; offsetY++) {
          int sampleY = (Math.floorDiv(y, 8) + offsetY) * 8;
          if (sampleY >= -2048
              && sampleY < 2048
              && terrain.occupied(
                  (Math.floorDiv(x, 4) + offsetX) * 4,
                  sampleY,
                  (Math.floorDiv(z, 4) + offsetZ) * 4)) return true;
        }
      }
    }
    return false;
  }

  private static void checkConcurrentColumns() {
    AtomicInteger calls = new AtomicInteger();
    Phaser firstSamples = new Phaser(4);
    var mask =
        new BiomeTerrainMask(
            (x, y, z) -> {
              if (calls.incrementAndGet() <= 4) firstSamples.arriveAndAwaitAdvance();
              return true;
            },
            -128,
            384,
            4,
            8);
    try (var workers = Executors.newFixedThreadPool(4)) {
      CompletableFuture<?>[] queries =
          IntStream.range(0, 4)
              .mapToObj(
                  index ->
                      CompletableFuture.runAsync(
                          () -> require(mask.nearTerrain(0, index * 8, 0), "Concurrent sample"),
                          workers))
              .toArray(CompletableFuture<?>[]::new);
      CompletableFuture.allOf(queries).join();
    }
    int first = calls.get();
    for (int index = 0; index < 4; index++) mask.nearTerrain(0, index * 8, 0);
    require(calls.get() == first, "Concurrent vertical samples remain cached together");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
