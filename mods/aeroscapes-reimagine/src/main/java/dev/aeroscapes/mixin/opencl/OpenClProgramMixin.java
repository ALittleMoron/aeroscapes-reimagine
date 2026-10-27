package dev.aeroscapes.mixin.opencl;

import com.ishland.c2me.opts.accel.opencl.common.compiler.GeneratedCLSource;
import dev.aeroscapes.worldgen.opencl.IslandOpenCl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "com.ishland.c2me.opts.accel.opencl.common.gen.OpenCLDevice", remap = false)
public abstract class OpenClProgramMixin {
  @ModifyVariable(method = "compileProgramAsync", at = @At("HEAD"), argsOnly = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin supplies the native compiler argument.
  private GeneratedCLSource aeroscapesReimagine$ownedKernel(GeneratedCLSource source) {
    return IslandOpenCl.withKernelHooks(source);
  }
}
