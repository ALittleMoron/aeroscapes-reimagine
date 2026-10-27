// Aero Islands 1.1.1 by Isabel is a behavior reference; Java was reimplemented.
// GPL-3.0-or-later for 0.1.0; comparison provenance: metadata/worldgen_adoption.json.
// Published-artifact provenance and modifications: metadata/worldgen_adoption.json.
package dev.aeroscapes.worldgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ClampedNormalFloat;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.feature.DripstoneUtils;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DripstoneClusterConfiguration;

public final class BasaltClusterFeature extends Feature<DripstoneClusterConfiguration> {
  public BasaltClusterFeature(Codec<DripstoneClusterConfiguration> codec) {
    super(codec);
  }

  @Override
  public boolean place(FeaturePlaceContext<DripstoneClusterConfiguration> context) {
    WorldGenLevel level = context.level();
    BlockPos origin = context.origin();
    DripstoneClusterConfiguration config = context.config();
    RandomSource random = context.random();
    if (!canStartCluster(level, origin)) {
      return false;
    }

    int height = config.height.sample(random);
    float wetness = config.wetness.sample(random);
    float density = config.density.sample(random);
    int xRadius = config.radius.sample(random);
    int zRadius = config.radius.sample(random);

    for (int xOffset = -xRadius; xOffset <= xRadius; xOffset++) {
      for (int zOffset = -zRadius; zOffset <= zRadius; zOffset++) {
        double columnChance = columnChance(xRadius, zRadius, xOffset, zOffset, config);
        BlockPos columnOrigin = origin.offset(xOffset, 0, zOffset);
        placeColumn(
            level,
            random,
            columnOrigin,
            xOffset,
            zOffset,
            wetness,
            columnChance,
            height,
            density,
            config);
      }
    }

    return true;
  }

  private static void placeColumn(
      WorldGenLevel level,
      RandomSource random,
      BlockPos pos,
      int x,
      int z,
      float wetness,
      double chance,
      int height,
      float density,
      DripstoneClusterConfiguration config) {
    Optional<Column> scan =
        Column.scan(
            level,
            pos,
            config.floorToCeilingSearchRange,
            DripstoneUtils::isEmptyOrWater,
            DripstoneUtils::isNeitherEmptyNorWater);
    if (scan.isEmpty()) return;

    OptionalInt ceiling = scan.get().getCeiling();
    OptionalInt initialFloor = scan.get().getFloor();
    if (ceiling.isEmpty() && initialFloor.isEmpty()) return;

    boolean placePool = random.nextFloat() < wetness;
    Column column;
    if (placePool
        && initialFloor.isPresent()
        && canPlacePool(level, pos.atY(initialFloor.getAsInt()))) {
      int poolY = initialFloor.getAsInt();
      column = scan.get().withFloor(OptionalInt.of(poolY - 1));
      level.setBlock(pos.atY(poolY), Blocks.WATER.defaultBlockState(), 2);
    } else {
      column = scan.get();
    }

    OptionalInt floor = column.getFloor();
    boolean placeStalactite = random.nextDouble() < chance;
    int stalactiteHeight;
    if (ceiling.isPresent() && placeStalactite && !isLava(level, pos.atY(ceiling.getAsInt()))) {
      int ceilingLayer = config.dripstoneBlockLayerThickness.sample(random);
      replaceBasaltLayer(level, pos.atY(ceiling.getAsInt()), ceilingLayer, Direction.UP);
      int availableHeight;
      if (floor.isPresent()) {
        availableHeight = Math.min(height, ceiling.getAsInt() - floor.getAsInt());
      } else {
        availableHeight = height;
      }

      stalactiteHeight = sampleDripstoneHeight(random, x, z, density, availableHeight, config);
    } else {
      stalactiteHeight = 0;
    }

    boolean placeStalagmite = random.nextDouble() < chance;
    int stalagmiteHeight;
    if (floor.isPresent() && placeStalagmite && !isLava(level, pos.atY(floor.getAsInt()))) {
      int floorLayer = config.dripstoneBlockLayerThickness.sample(random);
      replaceBasaltLayer(level, pos.atY(floor.getAsInt()), floorLayer, Direction.DOWN);
      if (ceiling.isPresent()) {
        stalagmiteHeight =
            Math.max(
                0,
                stalactiteHeight
                    + Mth.randomBetweenInclusive(
                        random,
                        -config.maxStalagmiteStalactiteHeightDiff,
                        config.maxStalagmiteStalactiteHeightDiff));
      } else {
        stalagmiteHeight = sampleDripstoneHeight(random, x, z, density, height, config);
      }
    } else {
      stalagmiteHeight = 0;
    }

    int finalStalactiteHeight;
    int finalStalagmiteHeight;
    if (ceiling.isPresent()
        && floor.isPresent()
        && ceiling.getAsInt() - stalactiteHeight <= floor.getAsInt() + stalagmiteHeight) {
      int floorY = floor.getAsInt();
      int ceilingY = ceiling.getAsInt();
      int overlapBottom = Math.max(ceilingY - stalactiteHeight, floorY + 1);
      int overlapTop = Math.min(floorY + stalagmiteHeight, ceilingY - 1);
      int splitY = Mth.randomBetweenInclusive(random, overlapBottom, overlapTop + 1);
      int splitBelowY = splitY - 1;
      finalStalactiteHeight = ceilingY - splitY;
      finalStalagmiteHeight = splitBelowY - floorY;
    } else {
      finalStalactiteHeight = stalactiteHeight;
      finalStalagmiteHeight = stalagmiteHeight;
    }

    boolean merge =
        random.nextBoolean()
            && finalStalactiteHeight > 0
            && finalStalagmiteHeight > 0
            && column.getHeight().isPresent()
            && finalStalactiteHeight + finalStalagmiteHeight == column.getHeight().getAsInt();
    if (ceiling.isPresent()) {
      placePointedDripstone(
          level, pos.atY(ceiling.getAsInt() - 1), Direction.DOWN, finalStalactiteHeight, merge);
    }

    if (floor.isPresent()) {
      placePointedDripstone(
          level, pos.atY(floor.getAsInt() + 1), Direction.UP, finalStalagmiteHeight, merge);
    }
  }

  private static boolean isLava(LevelReader level, BlockPos pos) {
    return level.getBlockState(pos).is(Blocks.LAVA);
  }

  private static int sampleDripstoneHeight(
      RandomSource random,
      int x,
      int z,
      float chance,
      int height,
      DripstoneClusterConfiguration config) {
    if (random.nextFloat() > chance) {
      return 0;
    } else {
      int centerDistance = Math.abs(x) + Math.abs(z);
      float heightBias =
          (float)
              Mth.clampedMap(
                  (double) centerDistance,
                  0.0,
                  (double) config.maxDistanceFromCenterAffectingHeightBias,
                  (double) height / 2.0,
                  0.0);
      return (int)
          randomBetweenBiased(
              random, 0.0F, (float) height, heightBias, (float) config.heightDeviation);
    }
  }

  private static boolean canPlacePool(WorldGenLevel level, BlockPos pos) {
    BlockState state = level.getBlockState(pos);
    if (state.is(Blocks.WATER)
        || state.is(Blocks.DRIPSTONE_BLOCK)
        || state.is(Blocks.POINTED_DRIPSTONE)) {
      return false;
    }
    if (level.getBlockState(pos.above()).getFluidState().is(FluidTags.WATER)) return false;
    for (Direction direction : Plane.HORIZONTAL) {
      if (!canBeAdjacentToWater(level, pos.relative(direction))) return false;
    }
    return canBeAdjacentToWater(level, pos.below());
  }

  private static boolean canBeAdjacentToWater(LevelAccessor level, BlockPos pos) {
    BlockState state = level.getBlockState(pos);
    return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.getFluidState().is(FluidTags.WATER);
  }

  private static void replaceBasaltLayer(
      WorldGenLevel level, BlockPos pos, int thickness, Direction direction) {
    MutableBlockPos cursor = pos.mutable();

    for (int i = 0; i < thickness; i++) {
      if (!replaceWithBasalt(level, cursor)) {
        return;
      }

      cursor.move(direction);
    }
  }

  private static double columnChance(
      int xRadius, int zRadius, int x, int z, DripstoneClusterConfiguration config) {
    int xEdgeDistance = xRadius - Math.abs(x);
    int zEdgeDistance = zRadius - Math.abs(z);
    int edgeDistance = Math.min(xEdgeDistance, zEdgeDistance);
    return (double)
        Mth.clampedMap(
            (float) edgeDistance,
            0.0F,
            (float) config.maxDistanceFromEdgeAffectingChanceOfDripstoneColumn,
            config.chanceOfDripstoneColumnAtMaxDistanceFromCenter,
            1.0F);
  }

  private static float randomBetweenBiased(
      RandomSource random, float min, float max, float mean, float deviation) {
    return ClampedNormalFloat.sample(random, mean, deviation, min, max);
  }

  private static boolean canSupportDripstone(BlockState state) {
    return state.is(Blocks.BASALT) || state.is(BlockTags.DRIPSTONE_REPLACEABLE);
  }

  private static BlockState pointedDripstoneState(
      Direction direction, DripstoneThickness thickness) {
    return Blocks.POINTED_DRIPSTONE
        .defaultBlockState()
        .setValue(PointedDripstoneBlock.TIP_DIRECTION, direction)
        .setValue(PointedDripstoneBlock.THICKNESS, thickness);
  }

  private static boolean canStartCluster(WorldGenLevel level, BlockPos pos) {
    return level.isStateAtPosition(pos, DripstoneUtils::isEmptyOrWater);
  }

  private static void emitDripstoneColumn(
      Direction direction, int height, boolean merge, Consumer<BlockState> callback) {
    if (height >= 3) {
      callback.accept(pointedDripstoneState(direction, DripstoneThickness.BASE));

      for (int i = 0; i < height - 3; i++) {
        callback.accept(pointedDripstoneState(direction, DripstoneThickness.MIDDLE));
      }
    }

    if (height >= 2) {
      callback.accept(pointedDripstoneState(direction, DripstoneThickness.FRUSTUM));
    }

    if (height >= 1) {
      callback.accept(
          pointedDripstoneState(
              direction, merge ? DripstoneThickness.TIP_MERGE : DripstoneThickness.TIP));
    }
  }

  private static void placePointedDripstone(
      WorldGenLevel level, BlockPos pos, Direction direction, int height, boolean merge) {
    if (canSupportDripstone(level.getBlockState(pos.offset(direction.getOpposite().getNormal())))) {
      MutableBlockPos mutable = pos.mutable();
      emitDripstoneColumn(
          direction,
          height,
          merge,
          state -> {
            if (state.is(Blocks.POINTED_DRIPSTONE)) {
              state = state.setValue(PointedDripstoneBlock.WATERLOGGED, level.isWaterAt(mutable));
            }

            level.setBlock(mutable, state, 2);
            mutable.move(direction);
          });
    }
  }

  private static boolean replaceWithBasalt(WorldGenLevel level, BlockPos pos) {
    BlockState blockState = level.getBlockState(pos);
    if (blockState.is(BlockTags.DRIPSTONE_REPLACEABLE)) {
      level.setBlock(pos, Blocks.BASALT.defaultBlockState(), 2);
      return true;
    } else {
      return false;
    }
  }
}
