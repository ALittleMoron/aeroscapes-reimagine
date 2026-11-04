package dev.aeroscapes.ecology;

import dev.aeroscapes.worldgen.ExposedIslandSurfaces;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;

public final class BirdHabitat {
  private BirdHabitat() {}

  public static boolean owned(LevelAccessor level) {
    return level instanceof ServerLevelAccessor server
        && IslandTerrain.from(server.getLevel().getChunkSource().randomState()) != null;
  }

  public static boolean exterior(LevelAccessor level, BlockPos position) {
    if (!(level instanceof ServerLevelAccessor server)) return false;
    IslandTerrain terrain = IslandTerrain.from(server.getLevel().getChunkSource().randomState());
    return terrain != null
        && ExposedIslandSurfaces.interpolated(
                position.getX(),
                position.getY(),
                position.getZ(),
                (x, y, z) -> terrain.biomeDensity(new SinglePointContext(x, y, z)))
            <= 0
        && level.getBlockState(position.above()).isAir()
        && level.getBlockState(position.above(2)).isAir();
  }

  public static int localSurface(LevelAccessor level, BlockPos position, int nativeHeight) {
    if (!owned(level) || !exterior(level, position)) return nativeHeight;
    BlockPos.MutableBlockPos cursor = position.mutable();
    for (int depth = 1;
        depth <= 128 && position.getY() - depth >= level.getMinBuildHeight();
        depth++) {
      cursor.setY(position.getY() - depth);
      if (!level.getBlockState(cursor).isAir()) return cursor.getY() + 1;
    }
    return nativeHeight;
  }

  public static boolean supportedAquatic(LevelAccessor level, BlockPos position) {
    if (!owned(level)) return true;
    if (level.getFluidState(position).is(FluidTags.WATER)
        || level.getFluidState(position.below()).is(FluidTags.WATER)) return true;
    return exterior(level, position)
        && level
            .getBlockState(position.below())
            .isFaceSturdy(level, position.below(), Direction.UP);
  }
}
