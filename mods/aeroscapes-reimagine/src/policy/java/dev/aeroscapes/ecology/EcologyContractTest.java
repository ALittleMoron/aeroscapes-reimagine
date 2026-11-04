package dev.aeroscapes.ecology;

import dev.aeroscapes.worldgen.ExposedIslandSurfaces;
import dev.aeroscapes.worldgen.IslandLayout;
import java.util.concurrent.atomic.AtomicInteger;

public final class EcologyContractTest {
  private EcologyContractTest() {}

  public static void main(String[] arguments) throws InterruptedException {
    require(ScopedSeaLevel.resolve(-512) == -512, "Native level outside a provider operation");
    ScopedSeaLevel.call(
        208,
        () -> {
          require(ScopedSeaLevel.resolve(-512) == 208, "Local first-air sea level");
          require(
              ScopedSeaLevel.call(145, () -> ScopedSeaLevel.resolve(-512)) == 145,
              "Nested local operation");
          require(ScopedSeaLevel.resolve(-512) == 208, "Parent scope restored after nesting");
          try {
            ScopedSeaLevel.call(
                180,
                () -> {
                  throw new IllegalArgumentException("fixture");
                });
            throw new AssertionError("Expected fixture exception");
          } catch (IllegalArgumentException expected) {
            require(ScopedSeaLevel.resolve(-512) == 208, "Parent scope restored after failure");
          }
          return null;
        });
    require(ScopedSeaLevel.resolve(-512) == -512, "Native value restored after operation");
    AtomicInteger separate = new AtomicInteger();
    Thread worker = new Thread(() -> separate.set(ScopedSeaLevel.resolve(-512)));
    ScopedSeaLevel.call(
        208,
        () -> {
          worker.start();
          return null;
        });
    worker.join();
    require(separate.get() == -512, "Another generation thread keeps its own native level");
    double actual = ExposedIslandSurfaces.interpolated(-3, -5, -1, (x, y, z) -> 2 * x - y + 3 * z);
    require(
        Math.abs(actual + 4) < 1e-12,
        "Natural lattice interpolation preserves linear fields at negative coordinates");
    BasinWaterlines cache = new BasinWaterlines();
    var basin = new IslandLayout.Basin(-512, -512, 191, 240, 240, 64, 55);
    cache.put(basin);
    require(cache.at(-512, -512, 63) == 192, "First-air sea at negative-coordinate basin");
    require(cache.at(-1000, -1000, 63) == 63, "Same cell outside the basin keeps native tint");
    require(new BasinWaterlines().at(-512, -512, 63) == 63, "Another level has no stale basin");
    for (int index = 0; index < 256; index++)
      cache.put(new IslandLayout.Basin(index * 1024 + 512, 512, 160, 240, 240, 64, index));
    require(cache.at(-512, -512, 63) == 63, "Old unloaded cells have bounded retention");
    System.out.println(
        "Ecology contracts: local scope, nesting, failure, thread isolation and density lattice passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
