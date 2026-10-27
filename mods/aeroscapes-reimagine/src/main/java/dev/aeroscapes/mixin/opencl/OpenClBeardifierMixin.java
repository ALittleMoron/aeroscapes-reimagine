package dev.aeroscapes.mixin.opencl;

import com.ishland.c2me.opts.dfc.common.ast.misc.BeardifierNode;
import com.ishland.c2me.opts.dfc.common.gen.opencl.OpenCLCGenFunctionContext;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Add the owned cavity at the beardifier node, preserving the parent addition order. */
@Mixin(
    targets =
        "com.ishland.c2me.opts.accel.opencl.common.compiler.emitters.misc.BeardifierNodeOpenCLCEmitter",
    remap = false)
public abstract class OpenClBeardifierMixin {
  @ModifyReturnValue(
      method =
          "doCLGen(Lcom/ishland/c2me/opts/dfc/common/ast/misc/BeardifierNode;Lcom/ishland/c2me/opts/dfc/common/gen/opencl/OpenCLCGenFunctionContext;Ljava/lang/String;)Ljava/lang/String;",
      at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras invokes this code-generation callback.
  private String aeroscapesReimagine$cityCavern(
      String generated, BeardifierNode node, OpenCLCGenFunctionContext context, String storeTo) {
    // Resolve the island offset only after every node has been emitted. A
    // beardifier may precede the island node in a valid graph traversal.
    return generated + "\nAEROSCAPES_CITY_POST(" + storeTo + ", ctx);\n";
  }
}
