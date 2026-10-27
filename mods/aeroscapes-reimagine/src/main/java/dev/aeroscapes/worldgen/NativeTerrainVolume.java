package dev.aeroscapes.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Bounded native noise interpolation for natural structure support and containers. */
public final class NativeTerrainVolume {
  private final BoundingBox bounds;
  private final BlockState[] blocks;
  private final boolean nativeColumns;
  private final int sampleMinimum;
  private final int sampleMaximum;

  public NativeTerrainVolume(
      NoiseBasedChunkGenerator generator, RandomState random, BoundingBox bounds) {
    this(generator, random, bounds, clippedHeight(generator, bounds), false);
  }

  /** Original full-height native states, including aquifers, for a pyramid footprint. */
  public static NativeTerrainVolume nativeColumns(
      NoiseBasedChunkGenerator generator,
      RandomState random,
      LevelHeightAccessor height,
      BoundingBox footprint) {
    var bounds =
        new BoundingBox(
            footprint.minX(),
            height.getMinBuildHeight(),
            footprint.minZ(),
            footprint.maxX(),
            height.getMaxBuildHeight() - 1,
            footprint.maxZ());
    return new NativeTerrainVolume(generator, random, bounds, height, true);
  }

  private NativeTerrainVolume(
      NoiseBasedChunkGenerator generator,
      RandomState random,
      BoundingBox bounds,
      LevelHeightAccessor sampleHeight,
      boolean nativeColumns) {
    if (nativeColumns) {
      // Height comes from the validated world dimension; the footprint bounds allocation.
      if (bounds.getXSpan() > 21 || bounds.getZSpan() > 21)
        throw new IllegalArgumentException("Native columns must fit within 21 by 21 columns");
    } else if (bounds.getXSpan() > 70 || bounds.getYSpan() > 30 || bounds.getZSpan() > 70) {
      throw new IllegalArgumentException(
          "Native terrain samples must fit within 70 by 30 by 70 blocks");
    }
    this.nativeColumns = nativeColumns;
    this.bounds = bounds;
    blocks = new BlockState[bounds.getXSpan() * bounds.getYSpan() * bounds.getZSpan()];
    NoiseGeneratorSettings settings =
        nativeColumns
            ? generator.generatorSettings().value()
            : solidSettings(generator.generatorSettings().value());
    int cellWidth = settings.noiseSettings().getCellWidth();
    int cellHeight = settings.noiseSettings().getCellHeight();
    NoiseSettings noiseSettings = settings.noiseSettings().clampToHeightAccessor(sampleHeight);
    sampleMinimum = noiseSettings.minY();
    sampleMaximum = sampleMinimum + noiseSettings.height();
    int originX = Math.floorDiv(bounds.minX(), cellWidth) * cellWidth;
    int originZ = Math.floorDiv(bounds.minZ(), cellWidth) * cellWidth;
    int cellCount =
        Math.max(
                Math.floorDiv(bounds.maxX() - originX, cellWidth),
                Math.floorDiv(bounds.maxZ() - originZ, cellWidth))
            + 1;
    NoiseChunk noise =
        new NoiseChunk(
            cellCount,
            random,
            originX,
            originZ,
            noiseSettings,
            DensityFunctions.BeardifierMarker.INSTANCE,
            settings,
            generator.globalFluidPicker.get(),
            Blender.empty());
    noise.initializeForFirstCellX();
    try {
      // Same cell and block order as NoiseBasedChunkGenerator.doFill. Reuse the
      // public interpolator across the region instead of rebuilding it per column.
      for (int cellX = 0; cellX < cellCount; cellX++) {
        noise.advanceCellX(cellX);
        for (int cellZ = 0; cellZ < cellCount; cellZ++) {
          for (int cellY = noiseSettings.height() / cellHeight - 1; cellY >= 0; cellY--) {
            noise.selectCellYZ(cellY, cellZ);
            for (int localY = cellHeight - 1; localY >= 0; localY--) {
              int y = noiseSettings.minY() + cellY * cellHeight + localY;
              noise.updateForY(y, (double) localY / cellHeight);
              for (int localX = 0; localX < cellWidth; localX++) {
                int x = originX + cellX * cellWidth + localX;
                noise.updateForX(x, (double) localX / cellWidth);
                for (int localZ = 0; localZ < cellWidth; localZ++) {
                  int z = originZ + cellZ * cellWidth + localZ;
                  noise.updateForZ(z, (double) localZ / cellWidth);
                  if (inside(x, y, z)) {
                    BlockState state = noise.getInterpolatedState();
                    blocks[index(x, y, z)] = state == null ? settings.defaultBlock() : state;
                  }
                }
              }
            }
          }
        }
        noise.swapSlices();
      }
    } finally {
      noise.stopInterpolation();
    }
  }

  private static LevelHeightAccessor clippedHeight(
      NoiseBasedChunkGenerator generator, BoundingBox bounds) {
    int cellHeight = generator.generatorSettings().value().noiseSettings().getCellHeight();
    int minimumY = Math.floorDiv(bounds.minY(), cellHeight) * cellHeight;
    int maximumY = (Math.floorDiv(bounds.maxY(), cellHeight) + 1) * cellHeight;
    return LevelHeightAccessor.create(minimumY, maximumY - minimumY);
  }

  public static NoiseBasedChunkGenerator solidSampler(NoiseBasedChunkGenerator generator) {
    return new NoiseBasedChunkGenerator(
        generator.getBiomeSource(),
        Holder.direct(solidSettings(generator.generatorSettings().value())));
  }

  @SuppressWarnings("deprecation") // Copy the pinned settings record's required legacy mob flag.
  private static NoiseGeneratorSettings solidSettings(NoiseGeneratorSettings settings) {
    return new NoiseGeneratorSettings(
        settings.noiseSettings(),
        settings.defaultBlock(),
        settings.defaultFluid(),
        settings.noiseRouter(),
        settings.surfaceRule(),
        settings.spawnTarget(),
        settings.seaLevel(),
        settings.disableMobGeneration(),
        false,
        settings.oreVeinsEnabled(),
        settings.useLegacyRandomSource());
  }

  private boolean inside(int x, int y, int z) {
    return x >= bounds.minX()
        && x <= bounds.maxX()
        && y >= bounds.minY()
        && y <= bounds.maxY()
        && z >= bounds.minZ()
        && z <= bounds.maxZ();
  }

  private int index(int x, int y, int z) {
    return ((x - bounds.minX()) * bounds.getYSpan() + y - bounds.minY()) * bounds.getZSpan()
        + z
        - bounds.minZ();
  }

  public BlockState block(int x, int y, int z) {
    if (!inside(x, y, z) || nativeColumns && (y < sampleMinimum || y >= sampleMaximum))
      return Blocks.AIR.defaultBlockState();
    BlockState state = blocks[index(x, y, z)];
    if (state == null)
      throw new IllegalStateException("Native noise left an unsampled container block");
    return state;
  }

  boolean solid(int x, int y, int z) {
    return solid(block(x, y, z));
  }

  static boolean solid(BlockState state) {
    return state.getFluidState().isEmpty()
        && Block.isFaceFull(
            state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP);
  }
}
