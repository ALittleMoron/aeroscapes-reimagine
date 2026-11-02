package dev.aeroscapes.worldgen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Local basin water, leaving the existing global fluid picker and aquifers intact. */
public final class BasinAquifer implements Aquifer {
  private final Aquifer delegate;
  private final IslandTerrain terrain;
  // NoiseChunk owns this aquifer. Retain its columns locally instead of competing
  // with other chunks for IslandTerrain's shared, direct-mapped cache per voxel.
  private final IslandTerrain.Column[] columns = new IslandTerrain.Column[256];
  private boolean delegated;

  public BasinAquifer(Aquifer delegate, IslandTerrain terrain) {
    this.delegate = delegate;
    this.terrain = terrain;
  }

  @Override
  public BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
    delegated = false;
    IslandTerrain.Column column = column(context.blockX(), context.blockZ());
    int y = context.blockY();
    if (column.lavaIsland() != null
        && column.lavaRadial() < 1.22
        && y >= column.lavaShape().bottom() - 4
        && y <= column.lavaIsland().waterline() + 16) {
      if (column.lava(y) && density <= 0) return Blocks.LAVA.defaultBlockState();
      if (column.lavaRadial() < 1.05 && y >= column.lavaFloor() - 12 && y <= column.lavaFloor())
        return density > 0 ? null : Blocks.STONE.defaultBlockState();
    }
    if (column.water(context.blockY()))
      return density > 0 ? null : Blocks.WATER.defaultBlockState();
    if (column.basin() != null
        && column.radial() < 1.05
        && context.blockY() <= column.floor()
        && context.blockY() >= column.floor() - 12)
      return density > 0 ? null : Blocks.STONE.defaultBlockState();
    delegated = true;
    return delegate.computeSubstance(context, density);
  }

  private IslandTerrain.Column column(int x, int z) {
    int index = (x & 15) | ((z & 15) << 4);
    IslandTerrain.Column cached = columns[index];
    if (cached == null || cached.x() != x || cached.z() != z) {
      cached = terrain.column(x, z);
      columns[index] = cached;
    }
    return cached;
  }

  @Override
  public boolean shouldScheduleFluidUpdate() {
    return delegated && delegate.shouldScheduleFluidUpdate();
  }
}
