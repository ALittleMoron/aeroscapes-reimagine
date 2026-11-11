package dev.aeroscapes.mixin.engineering;

import dev.aeroscapes.engineering.LadleAssemblyOutputs;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.JemiRecipe", remap = false)
public abstract class LadleAssemblyEmiMixin {
  @Shadow private Object recipe;
  @Shadow private List<EmiStack> outputs;
  @Shadow private boolean allowTree;

  @Inject(method = "<init>", at = @At("TAIL"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // JEMI otherwise indexes only Create's primary result.
  private void aeroscapes$ladleVariants(CallbackInfo callback) {
    if (LadleAssemblyOutputs.addAlternates(recipe, outputs)) allowTree = false;
  }
}
