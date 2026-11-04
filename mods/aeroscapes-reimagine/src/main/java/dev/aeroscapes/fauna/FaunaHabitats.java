package dev.aeroscapes.fauna;

import dev.aeroscapes.ecology.BirdHabitat;
import dev.aeroscapes.worldgen.BasinSeaLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;

/** Preserve the providers' depth and ground rules around the actual island habitat. */
public final class FaunaHabitats {
  private FaunaHabitats() {}

  public static int waterline(int nativeSea, LevelAccessor level, BlockPos position) {
    return BasinSeaLevel.shore(BasinSeaLevel.water(nativeSea, level, position), level, position);
  }

  public static int surface(int nativeSea, LevelAccessor level, BlockPos position) {
    if (!BirdHabitat.owned(level)) return nativeSea;
    var cursor = position.mutable();
    for (int depth = 1;
        depth <= 128 && position.getY() - depth >= level.getMinBuildHeight();
        depth++) {
      cursor.setY(position.getY() - depth);
      var state = level.getBlockState(cursor);
      if (state.isFaceSturdy(level, cursor, Direction.UP)
          || state.is(BlockTags.LEAVES)
          || state.getFluidState().is(FluidTags.WATER)) return cursor.getY() + 1;
    }
    // Dragonfly's native upper bound is surface+16 and its placement admits air.
    // A missing island floor must therefore stay outside the buildable band.
    return level.getMinBuildHeight() - 17;
  }
}
