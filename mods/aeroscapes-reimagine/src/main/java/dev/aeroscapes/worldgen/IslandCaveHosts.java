package dev.aeroscapes.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

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

  public static Range interior(int start, int end) {
    int thickness = end - start;
    if (thickness < 12) return null;
    int margin = thickness >= 24 ? 8 : 4;
    return new Range(start + margin, end - margin);
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
        // Thin natural bodies still have room for a player tunnel. The carver's per-block
        // underside and fluid-liner guards remain authoritative where its radius is larger.
        Range interior = interior(start, y);
        if (interior != null) ranges.add(interior);
        start = Integer.MIN_VALUE;
      }
    }
    return new IslandCaveHosts(source, List.copyOf(ranges));
  }

  public static IslandCaveHosts sample(
      IslandTerrain terrain,
      ChunkPos source,
      NoiseBasedChunkGenerator generator,
      RandomState state,
      LevelHeightAccessor height) {
    int x = source.getMiddleBlockX();
    int z = source.getMiddleBlockZ();
    var nativeColumn = generator.getBaseColumn(x, z, height, state);
    var column = terrain.column(x, z);
    List<Range> ranges = new ArrayList<>();
    int start = Integer.MIN_VALUE;
    for (int y = height.getMinBuildHeight(); y <= height.getMaxBuildHeight(); y++) {
      boolean solid =
          y < height.getMaxBuildHeight()
              && NativeTerrainVolume.solid(nativeColumn.getBlock(y))
              && !column.liner(y)
              && !(column.lavaIsland() != null
                  && column
                      .lavaShape()
                      .containsHabitat(
                          y,
                          column.lavaIsland().waterline(),
                          IslandBiomeSource.VOLCANIC_HABITAT_MARGIN));
      if (solid && start == Integer.MIN_VALUE) start = y;
      if (!solid && start != Integer.MIN_VALUE) {
        Range interior = interior(start, y);
        if (interior != null) ranges.add(interior);
        start = Integer.MIN_VALUE;
      }
    }
    return new IslandCaveHosts(source, List.copyOf(ranges));
  }
}
