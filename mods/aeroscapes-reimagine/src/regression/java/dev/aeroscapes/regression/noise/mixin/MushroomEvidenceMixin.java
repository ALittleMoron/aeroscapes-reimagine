package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Observe the native placement predicate at the residual stage-comparison sites. */
@Mixin(MushroomBlock.class)
public abstract class MushroomEvidenceMixin {
  @WrapMethod(method = "canSurvive")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Regression observes, without changing placement.
  private boolean aeroscapesRegression$placement(
      BlockState state, LevelReader level, BlockPos position, Operation<Boolean> original) {
    boolean result = original.call(state, level, position);
    if (System.getProperty("aeroscapes.regression.stageSnapshotFile") != null
        && position.getY() == -7
        && position.getX() >= 12247
        && position.getX() <= 12248
        && position.getZ() >= 12258
        && position.getZ() <= 12261)
      LogUtils.getLogger()
          .info(
              "Mushroom placement evidence: pos={} result={} raw-light={} soil={}",
              position,
              result,
              level.getRawBrightness(position, 0),
              level.getBlockState(position.below()));
    return result;
  }
}
