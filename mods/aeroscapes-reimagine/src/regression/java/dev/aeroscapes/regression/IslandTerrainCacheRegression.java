package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.aeroscapes.worldgen.BasinAquifer;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.RandomState;

/** Checks density arithmetic and cache isolation against the pre-optimization scalar formula. */
final class IslandTerrainCacheRegression {
  private IslandTerrainCacheRegression() {}

  static void verify(RandomState random) {
    IslandTerrain terrain = IslandTerrain.from(random);
    if (terrain == null) throw new IllegalStateException("Missing owned island terrain");
    verifyDeferredBound();
    verifySamples(terrain, 0);
    try (var workers = Executors.newFixedThreadPool(4)) {
      var checks = new ArrayList<CompletableFuture<Void>>();
      for (int worker = 0; worker < 4; worker++) {
        int offset = (worker + 1) * 16;
        checks.add(CompletableFuture.runAsync(() -> verifySamples(terrain, offset), workers));
      }
      CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).join();
    }
    LogUtils.getLogger().info("Island terrain scalar/concurrent cache parity passed");
  }

  private static void verifyDeferredBound() {
    var form = new DeferredForm(0.5);
    var terrain =
        new IslandTerrain(
            DensityFunctions.constant(-1),
            DensityFunctions.constant(-1),
            DensityFunctions.constant(0),
            form,
            DensityFunctions.constant(1));
    // Construction must work before a forward registry reference can provide its bound.
    form.initialized = true;
    for (int x = -512; x <= 512; x += 16) {
      for (int z = -512; z <= 512; z += 16) {
        if (!terrain.column(x, z).hasDetail()) continue;
        var nextForm = new DeferredForm(0.1);
        var mapped =
            (IslandTerrain) terrain.mapAll(density -> density == form ? nextForm : density);
        nextForm.initialized = true;
        int changed = 0;
        for (int y = -128; y < 384; y += 8) {
          var position = new DensityFunction.SinglePointContext(x, y, z);
          double original = terrain.compute(position);
          double remapped = mapped.compute(position);
          if (original != remapped) changed++;
        }
        if (form.boundRequests != 1 || nextForm.boundRequests != 1) {
          throw new IllegalStateException("A detailed column repeats invariant bound resolution");
        }
        if (changed == 0) throw new IllegalStateException("Mapped density fixture missed detail");
        for (int y = -128; y < 384; y += 8) {
          var position = new DensityFunction.SinglePointContext(x, y, z);
          if (Double.doubleToLongBits(mapped.compute(position))
              != Double.doubleToLongBits(reference(mapped, position))) {
            throw new IllegalStateException("Mapping reused a previous density bound");
          }
        }
        return;
      }
    }
    throw new IllegalStateException("Deferred-bound fixture missed a detailed column");
  }

  private static void verifySamples(IslandTerrain terrain, int offset) {
    // Each worker owns its aquifer, as NoiseChunk does. Only immutable terrain is shared.
    var aquifer = new BasinAquifer(new ProbeAquifer(), terrain);
    for (int sample = 0; sample < 64; sample++) {
      int x = sample * 137 % 8192 - 4096 + offset;
      int z = sample * 367 % 8192 - 4096 - offset;
      for (int y = -128; y < 384; y += 8) {
        var position = new DensityFunction.SinglePointContext(x, y, z);
        double expected = reference(terrain, position);
        double actual = terrain.compute(position);
        if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(actual)) {
          throw new IllegalStateException("Terrain cache changed density at " + position);
        }
        var fresh = new BasinAquifer(new ProbeAquifer(), terrain);
        BlockState expectedBlock = fresh.computeSubstance(position, expected);
        if (aquifer.computeSubstance(position, actual) != expectedBlock
            || aquifer.shouldScheduleFluidUpdate() != fresh.shouldScheduleFluidUpdate()) {
          throw new IllegalStateException("Concurrent terrain changed aquifer at " + position);
        }
      }
    }
  }

  private static double reference(IslandTerrain terrain, DensityFunction.FunctionContext context) {
    int y = context.blockY();
    double retained = y >= -64 && y < 304 ? terrain.original().compute(context) : -1;
    if (y < 32) retained = Math.max(retained, terrain.lower().compute(context));
    var column = terrain.column(context.blockX(), context.blockZ());
    double added = column.islands(y);
    if (column.basin() != null && column.radial() < 1.22) {
      added = Math.max(added, column.basinShape().density(y));
    }
    if (column.hasDetail() && added > -terrain.form().maxValue()) {
      double detail = terrain.form().compute(context);
      added = column.islands(y, detail);
      if (column.basin() != null)
        added = Math.max(added, column.basinShape().sealedDensity(y, detail));
      boolean liner =
          column.basin() != null
              && column.radial() < 1.08
              && y >= column.floor() - 12
              && y <= column.floor() + 5;
      if (!liner) added = Math.min(added, terrain.caves().compute(context));
    }
    if (y < -116) added = Math.min(added, (y + 125) / 12.0);
    return Math.max(retained, added);
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

  private static final class DeferredForm implements DensityFunction.SimpleFunction {
    private final double value;
    private boolean initialized;
    private int boundRequests;

    private DeferredForm(double value) {
      this.value = value;
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
      return value;
    }

    @Override
    public double minValue() {
      return -maxValue();
    }

    @Override
    public double maxValue() {
      if (!initialized) throw new IllegalStateException("Forward density reference is not bound");
      boundRequests++;
      return value;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
      return KeyDispatchDataCodec.of(MapCodec.unit(this));
    }
  }
}
