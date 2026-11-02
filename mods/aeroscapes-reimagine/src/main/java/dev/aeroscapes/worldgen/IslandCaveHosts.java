package dev.aeroscapes.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;

/** Immutable source-chunk anchors shared by native carver selection and height placement. */
public record IslandCaveHosts(ChunkPos source, List<Range> ranges) {
  public record Range(int minimum, int maximum) {
    public int middle() {
      return (minimum + maximum) / 2;
    }

    public int height(RandomSource random) {
      return minimum + random.nextInt(maximum - minimum + 1);
    }
  }

  public static IslandCaveHosts sample(IslandTerrain terrain, ChunkPos source) {
    int x = source.getMiddleBlockX();
    int z = source.getMiddleBlockZ();
    var column = terrain.column(x, z);
    List<Range> ranges = new ArrayList<>();
    int start = Integer.MIN_VALUE;
    for (int y = -112; y <= 368; y += 4) {
      boolean solid =
          y < 368
              && !column.liner(y)
              && !(column.lavaIsland() != null
                  && column
                      .lavaShape()
                      .containsHabitat(
                          y,
                          column.lavaIsland().waterline(),
                          IslandBiomeSource.VOLCANIC_HABITAT_MARGIN))
              && terrain.biomeDensity(new SinglePointContext(x, y, z)) > 0.15;
      if (solid && start == Integer.MIN_VALUE) start = y;
      if (!solid && start != Integer.MIN_VALUE) {
        // Keep room for a ceiling, floor and native tunnel radius in each separate body.
        if (y - start >= 24) ranges.add(new Range(start + 8, y - 8));
        start = Integer.MIN_VALUE;
      }
    }
    return new IslandCaveHosts(source, List.copyOf(ranges));
  }
}
