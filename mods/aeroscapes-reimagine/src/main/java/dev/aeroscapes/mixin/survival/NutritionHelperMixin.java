package dev.aeroscapes.mixin.survival;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.dannyandson.nutritionalbalance.gui.NutrientGUIHelper", remap = false)
public abstract class NutritionHelperMixin {
  @Inject(
      method = "init(Lcom/dannyandson/nutritionalbalance/gui/INutrientGUIScreen;III)V",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$replaceStaticPanel(CallbackInfo callback) {
    callback.cancel();
  }
}
