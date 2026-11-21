package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import net.minecraft.world.level.biome.Climate;

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
    checkIslandSurfaces();
    checkClimateTies();
    System.out.println("BiomeTerrainMask: spatial, bounds, cache and concurrent checks passed");
  }

  private static void checkIslandSurfaces() {
    var islands =
        new BiomeTerrainMask(
            (x, y, z) ->
                x >= -20 && x <= 20 && z >= -12 && z <= 12 && y >= 16 && y <= 40
                    || x >= -20 && x <= 20 && z >= -12 && z <= 12 && y >= 72 && y <= 88
                    || x >= 32 && x <= 112 && z >= -12 && z <= 12 && y >= 16 && y <= 40,
            -128,
            384,
            4,
            8);
    var lower = islands.smallIslandSurface(-20, 40, 0);
    require(lower != null && lower.getY() == 40, "Resolve the exposed lower island");
    require(
        lower.equals(islands.smallIslandSurface(20, 44, 8)),
        "Connected surface uses one climate anchor across chunk boundaries");
    require(
        lower.equals(islands.smallIslandSurface(24, 44, 8)),
        "An interpolated edge inherits its adjacent island climate");
    var upper = islands.smallIslandSurface(0, 88, 0);
    require(upper != null && upper.getY() == 88, "Stacked islands have separate climate anchors");
    require(islands.smallIslandSurface(0, 16, 0) == null, "Deep interior keeps its habitat");
    require(islands.smallIslandSurface(40, 40, 0) == null, "Wide island keeps native climate");
    require(islands.smallIslandSurface(100, 40, 0) == null, "Wide island across cache cells");

    var adjacent =
        new BiomeTerrainMask(
            (x, y, z) -> (x == -4 || x == 4) && y == 64 && z == 0, -128, 384, 4, 8);
    require(adjacent.smallIslandSurface(0, 64, 0) == null, "Distinct islands keep separate edges");
    var diagonal = new BiomeTerrainMask((x, y, z) -> x == 4 && y == 64 && z == 4, -128, 384, 4, 8);
    var corner = diagonal.smallIslandSurface(4, 64, 4);
    require(
        corner != null && corner.equals(diagonal.smallIslandSurface(0, 64, 0)),
        "Land inside an interpolated diagonal edge inherits the nearby anchor");
    require(
        corner.equals(diagonal.smallIslandSurface(-4, 64, -4)),
        "The two-node terrain halo covers biome zoom at the outside corner");
  }

  private static void checkClimateTies() {
    var parameters =
        new Climate.ParameterList<>(
            List.of(
                Pair.of(Climate.parameters(-1, 0, 0, 0, 0, 0, 0), "cold"),
                Pair.of(Climate.parameters(1, 0, 0, 0, 0, 0, 0), "warm")));
    var midpoint = Climate.target(0, 0, 0, 0, 0, 0);
    parameters.findValue(Climate.target(-1, 0, 0, 0, 0, 0));
    var coldHistory =
        SmallIslandClimate.withoutHistory(
            parameters, 0, 0, 0, () -> parameters.findValue(midpoint));
    require("cold".equals(parameters.findValue(midpoint)), "Retain the neighboring cold search");
    parameters.findValue(Climate.target(1, 0, 0, 0, 0, 0));
    var warmHistory =
        SmallIslandClimate.withoutHistory(
            parameters, 0, 0, 0, () -> parameters.findValue(midpoint));
    require("warm".equals(parameters.findValue(midpoint)), "Retain the neighboring warm search");
    require(coldHistory.equals(warmHistory), "Island anchor ties resolve independent of history");
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
