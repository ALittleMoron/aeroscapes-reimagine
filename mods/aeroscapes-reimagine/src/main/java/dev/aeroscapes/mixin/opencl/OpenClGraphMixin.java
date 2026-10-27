package dev.aeroscapes.mixin.opencl;

import dev.aeroscapes.worldgen.opencl.IslandOpenClGraph;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "com.ishland.c2me.opts.accel.opencl.common.compiler.OpenCLCGen", remap = false)
public abstract class OpenClGraphMixin {
  @ModifyVariable(method = "compile", at = @At("HEAD"), argsOnly = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Mixin validates the native compiler's router input.
  private static NoiseRouter aeroscapesReimagine$boundedGraph(NoiseRouter router) {
    IslandOpenClGraph.validate(router);
    return router;
  }
}
