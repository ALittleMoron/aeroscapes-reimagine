package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.slf4j.Logger;

/** Checks the loaded chert transition's height coverage and feature-local random consumption. */
public final class StoneTransitionRegression {
  private static final Logger LOGGER = LogUtils.getLogger();

  private StoneTransitionRegression() {}

  public static void verify(ServerLevel level) {
    var feature =
        level
            .registryAccess()
            .registryOrThrow(Registries.PLACED_FEATURE)
            .getOrThrow(
                ResourceKey.create(
                    Registries.PLACED_FEATURE,
                    ResourceLocation.parse("natures_spirit:ores/ore_stone_transition")));
    var ranges =
        feature.placement().stream()
            .filter(HeightRangePlacement.class::isInstance)
            .map(HeightRangePlacement.class::cast)
            .toList();
    require(ranges.size() == 1, "Chert transition must have one height placement");
    var context =
        new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.of(feature));
    for (long seed : new long[] {1, -8315743817914385108L, -4851711576543251912L}) {
      samples(ranges.getFirst(), context, seed);
    }
    LOGGER.info(
        "Stone transition regression passed: all 34 heights [-8,25], deterministic native draws");
  }

  private static void samples(HeightRangePlacement range, PlacementContext context, long seed) {
    var actual = new WorldgenRandom(new XoroshiroRandomSource(seed));
    var expected = new WorldgenRandom(new XoroshiroRandomSource(seed));
    int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
    long decorationSeed = actual.setDecorationSeed(seed, 16, -32);
    expected.setDecorationSeed(seed, 16, -32);
    actual.setFeatureSeed(decorationSeed, 1, step);
    expected.setFeatureSeed(decorationSeed, 1, step);
    Set<Integer> heights = new HashSet<>();
    for (int sample = 0; sample < 4096; sample++) {
      var positions = range.getPositions(context, actual, BlockPos.ZERO).toList();
      require(positions.size() == 1, "Chert transition height placement discarded a candidate");
      int height = positions.getFirst().getY();
      require(
          height == -8 + expected.nextInt(34),
          "Chert transition lost its uniform [-8,25] height or native random draw");
      heights.add(height);
    }
    require(heights.size() == 34, "Chert transition no longer covers every authored height");
    require(
        actual.nextLong() == expected.nextLong(), "Chert transition consumed extra random draws");
  }

  private static void require(boolean accepted, String failure) {
    if (!accepted) {
      throw new IllegalStateException(failure);
    }
  }
}
