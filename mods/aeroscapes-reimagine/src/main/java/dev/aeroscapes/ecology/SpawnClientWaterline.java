package dev.aeroscapes.ecology;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class SpawnClientWaterline {
  private SpawnClientWaterline() {}

  public static int sea(int original, Level level, BlockPos position) {
    return level instanceof ClientWaterlineAccess client
        ? client.aeroscapes$waterlines().at(position.getX(), position.getZ(), original)
        : original;
  }

  public static float tintLimit(float original, Level level, BlockPos position) {
    int local = sea(Integer.MIN_VALUE, level, position);
    return local == Integer.MIN_VALUE ? original : local + 4;
  }
}
