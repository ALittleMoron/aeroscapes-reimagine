package dev.aeroscapes.regression.opencl.mixin;

import com.ishland.c2me.opts.accel.opencl.common.compiler.GeneratedCLSource;
import com.ishland.c2me.opts.accel.opencl.common.compiler.OpenCLCGen;
import dev.aeroscapes.regression.OpenClBuildFailureRegression;
import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exercises the native failed-compilation path without depending on a particular driver bug. */
@Mixin(targets = "com.ishland.c2me.opts.accel.opencl.common.gen.OpenCLDevice", remap = false)
public abstract class OpenClBuildFailureMixin {
  @Inject(method = "compileProgramAsync", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Dedicated regression fixture only.
  private void aeroscapesRegression$failBuild(
      String description,
      GeneratedCLSource source,
      CallbackInfoReturnable<CompletableFuture<EnumMap<OpenCLCGen.ProgramType, Long>>> callback) {
    if (Boolean.getBoolean("aeroscapes.regression.opencl.forceBuildFailure")) {
      OpenClBuildFailureRegression.recordFailure();
      callback.setReturnValue(
          CompletableFuture.failedFuture(
              new IllegalStateException("Expected forced OpenCL build failure")));
    }
  }
}
