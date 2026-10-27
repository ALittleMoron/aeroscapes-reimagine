package dev.aeroscapes.regression.opencl.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.regression.opencl.OpenClBatchEvidence;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Regression-only instrumentation; never present in the shipped pack. */
@Mixin(
    targets = "com.ishland.c2me.opts.accel.opencl.common.gen.CLServerBatchedBiomeNoiseContext",
    remap = false)
public abstract class OpenClBatchEvidenceMixin {
  @Shadow @Final private RandomState noiseConfig;
  @Unique private volatile boolean aeroscapesRegression$countedLaunch;

  @ModifyExpressionValue(
      method = "execute0",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/lwjgl/opencl/CL12;clEnqueueNDRangeKernel(JJILorg/lwjgl/PointerBuffer;Lorg/lwjgl/PointerBuffer;Lorg/lwjgl/PointerBuffer;Lorg/lwjgl/PointerBuffer;Lorg/lwjgl/PointerBuffer;)I",
              ordinal = 4))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // MixinExtras observes the exact noise enqueue status.
  private int aeroscapesRegression$noiseEnqueued(int status) {
    if (status == 0) aeroscapesRegression$countedLaunch = OpenClBatchEvidence.launched(noiseConfig);
    return status;
  }

  @Inject(method = "writeBlocks", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin observes consumed native GPU output.
  private void aeroscapesRegression$blocksConsumed(CallbackInfo callback) {
    if (aeroscapesRegression$countedLaunch) OpenClBatchEvidence.consumed(noiseConfig);
  }
}
