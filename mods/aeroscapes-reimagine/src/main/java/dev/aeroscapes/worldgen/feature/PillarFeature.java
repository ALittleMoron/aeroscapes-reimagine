// Aero Islands 1.1.1 by Isabel is a behavior reference; Java was reimplemented.
// GPL-3.0-or-later for 0.1.0; comparison provenance: metadata/worldgen_adoption.json.
// Published-artifact provenance and modifications: metadata/worldgen_adoption.json.
package dev.aeroscapes.worldgen.feature;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

public final class PillarFeature extends Feature<PillarFeatureConfig> {
  public PillarFeature(Codec<PillarFeatureConfig> codec) {
    super(codec);
  }

  @Override
  public boolean place(FeaturePlaceContext<PillarFeatureConfig> context) {
    RandomSource random = context.random();
    PillarParameters parameters = sample(context.config(), random);
    LongSet pillarPositions =
        collectPositions(
            context.level(), context.origin(), parameters, context.config().maxGroundDrop());
    fillPillar(context.level(), random, pillarPositions, context.config().checkedBlockPlacement());
    // Even zero-length vines consume the published per-direction random draws.
    placeVines(context.level(), random, pillarPositions, parameters.vineLength());
    return true;
  }

  private static PillarParameters sample(PillarFeatureConfig config, RandomSource random) {
    int height = config.height().sample(random);
    int vineLength = config.vineLength().sample(random);
    int heightOffset = config.heightOffset().sample(random);
    int radius = config.radius().sample(random);
    double frequency = (double) config.noiseFrequency().sample(random);
    double minRadiusScale = (double) config.minRadiusScale().sample(random);
    DistanceTestType distanceTestType =
        config.distanceTestType().getRandomValue(random).orElseThrow();
    ImprovedNoise noise = new ImprovedNoise(random);
    return new PillarParameters(
        height,
        vineLength,
        heightOffset,
        radius,
        frequency,
        minRadiusScale,
        distanceTestType,
        noise);
  }

  private static LongSet collectPositions(
      WorldGenLevel level,
      BlockPos origin,
      PillarParameters parameters,
      Optional<Integer> maxGroundDrop) {
    int height = parameters.height();
    int heightOffset = parameters.heightOffset();
    int radius = parameters.radius();
    double frequency = parameters.frequency();
    double minRadiusScale = parameters.minRadiusScale();
    ImprovedNoise noise = parameters.noise();
    DistanceTester distanceTester = parameters.distanceTestType().getDistanceTester();
    MutableBlockPos mutableBlockPos = new MutableBlockPos();
    LongSet pillarPositions = new LongOpenHashSet();
    for (int xOffset = -radius; xOffset <= radius; xOffset++) {
      for (int zOffset = -radius; zOffset <= radius; zOffset++) {
        mutableBlockPos.setWithOffset(origin, xOffset, 0, zOffset);
        if (distanceTester.withinDistance(origin, mutableBlockPos, (double) radius)) {
          int surface =
              heightOffset == 0 || maxGroundDrop.isPresent()
                  ? level.getHeight(
                      Types.OCEAN_FLOOR_WG, mutableBlockPos.getX(), mutableBlockPos.getZ())
                  : 5;
          // A footprint crossing an island edge must not use a distant island as its base.
          // Keep the unrestricted published behavior for configurations without this option.
          if (maxGroundDrop.isPresent()
              && (surface <= level.getMinBuildHeight()
                  || Math.abs((long) surface - origin.getY()) > maxGroundDrop.orElseThrow()))
            continue;
          int columnHeight = heightOffset == 0 ? surface : 5;
          int maxY = origin.getY() + height + heightOffset;

          for (int worldY = columnHeight - 5; worldY <= maxY; worldY++) {
            mutableBlockPos.set(origin.getX() + xOffset, worldY, origin.getZ() + zOffset);
            double pillarNoise =
                (noise.noise(
                            (double) mutableBlockPos.getX() * frequency,
                            (double) mutableBlockPos.getY() * frequency,
                            (double) mutableBlockPos.getZ() * frequency)
                        + 1.0)
                    * 0.5;
            double localRadius =
                Mth.clampedLerp((double) radius * minRadiusScale, (double) radius, pillarNoise);
            // Compatibility: division precedes subtraction in the published lower taper.
            double taperedRadius =
                localRadius
                    * (worldY > maxY - height
                        ? 1.0
                        : (double) worldY
                            - (double) (maxY - height - 16)
                                / (double) (maxY - height - (maxY - height - 16)));
            if (taperedRadius > 1.0
                && distanceTester.withinDistance(
                    origin.atY(worldY), mutableBlockPos, taperedRadius)) {
              pillarPositions.add(mutableBlockPos.asLong());
            } else if (taperedRadius <= 1.0) {
              break;
            }
          }
        }
      }
    }
    return pillarPositions;
  }

  private static void fillPillar(
      WorldGenLevel level,
      RandomSource random,
      LongSet pillarPositions,
      CheckedBlockPlacement placement) {
    MutableBlockPos mutableBlockPos = new MutableBlockPos();
    // Complete each material pass before the next predicate observes the world.
    for (Pair<BlockPredicate, BlockStateProvider> blockPlacement : placement.blockPlacement()) {
      pillarPositions.forEach(
          pos -> {
            mutableBlockPos.set(pos);
            if (blockPlacement.getFirst().test(level, mutableBlockPos)) {
              level.setBlock(
                  mutableBlockPos, blockPlacement.getSecond().getState(random, mutableBlockPos), 2);
            }
          });
    }
  }

  private static void placeVines(
      WorldGenLevel level, RandomSource random, LongSet pillarPositions, int vineLength) {
    MutableBlockPos mutableBlockPos = new MutableBlockPos();
    pillarPositions.forEach(
        pos -> {
          mutableBlockPos.set(pos);

          for (Direction direction : Plane.HORIZONTAL) {
            if (random.nextDouble() < 0.1) {
              mutableBlockPos.set(pos).move(direction);

              for (int i = 0; i < vineLength; i++) {
                if (level.getBlockState(mutableBlockPos).isAir()
                    && !pillarPositions.contains(mutableBlockPos.asLong())) {
                  level.setBlock(
                      mutableBlockPos,
                      Blocks.VINE
                          .defaultBlockState()
                          .setValue(VineBlock.getPropertyForFace(direction.getOpposite()), true),
                      2);
                }

                mutableBlockPos.move(Direction.DOWN);
              }
            }
          }
        });
  }

  private record PillarParameters(
      int height,
      int vineLength,
      int heightOffset,
      int radius,
      double frequency,
      double minRadiusScale,
      DistanceTestType distanceTestType,
      ImprovedNoise noise) {}
}
