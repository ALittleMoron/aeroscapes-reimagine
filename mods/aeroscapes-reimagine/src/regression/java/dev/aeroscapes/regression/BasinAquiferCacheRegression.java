package dev.aeroscapes.regression;

import dev.aeroscapes.worldgen.BasinAquifer;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;

/** Checks repeated and colliding column queries against fresh, uncached aquifers. */
final class BasinAquiferCacheRegression {
  private BasinAquiferCacheRegression() {}

  static void verify(RandomState random) {
    IslandTerrain terrain = IslandTerrain.from(random);
    if (terrain == null) throw new IllegalStateException("Missing owned island terrain");
    var cached = new BasinAquifer(new ProbeAquifer(), terrain);
    int water = 0;
    int stone = 0;
    for (int sample = 0; sample < 128; sample++) {
      int x = sample * 137 % 8192 - 4096;
      int z = sample * 367 % 8192 - 4096;
      for (int offset : new int[] {0, 16, -16, 0}) {
        for (int y = -128; y < 384; y += 31) {
          var position = new DensityFunction.SinglePointContext(x + offset, y, z - offset);
          for (double density : new double[] {-1, 0, 1}) {
            var fresh = new BasinAquifer(new ProbeAquifer(), terrain);
            BlockState expected = fresh.computeSubstance(position, density);
            if (expected == Blocks.WATER.defaultBlockState()) water++;
            if (expected == Blocks.STONE.defaultBlockState()) stone++;
            if (cached.computeSubstance(position, density) != expected
                || cached.shouldScheduleFluidUpdate() != fresh.shouldScheduleFluidUpdate()) {
              throw new IllegalStateException("Aquifer cache changed a block or fluid update");
            }
          }
        }
      }
    }
    if (water == 0 || stone == 0) {
      throw new IllegalStateException("Aquifer cache fixture missed water or basin floor");
    }
    com.mojang.logging.LogUtils.getLogger()
        .info("Aquifer cache regression passed: {} water, {} basin-floor results", water, stone);
  }

  private static final class ProbeAquifer implements Aquifer {
    private boolean scheduled;

    @Override
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
      scheduled = (context.blockY() & 1) == 0;
      return density > 0 ? null : Blocks.LAVA.defaultBlockState();
    }

    @Override
    public boolean shouldScheduleFluidUpdate() {
      return scheduled;
    }
  }
}
