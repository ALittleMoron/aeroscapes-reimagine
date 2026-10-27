package dev.aeroscapes.mixin.opencl;

import com.ishland.c2me.opts.dfc.common.ast.misc.CacheLikeNode;
import dev.aeroscapes.worldgen.opencl.IslandOpenClGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(
    targets = "com.ishland.c2me.opts.accel.opencl.common.compiler.OpenCLCGen$ContextImpl",
    remap = false)
public abstract class OpenClFlatCacheMixin {
  @ModifyVariable(method = "registerFlatCache", at = @At("HEAD"), argsOnly = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin validates before native cache registration.
  private CacheLikeNode aeroscapesReimagine$boundedFlatCache(CacheLikeNode node) {
    IslandOpenClGraph.validateFlatCache(node);
    return node;
  }
}
