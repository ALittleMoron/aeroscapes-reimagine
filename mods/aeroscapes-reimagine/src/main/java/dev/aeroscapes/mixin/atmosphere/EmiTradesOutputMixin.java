package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.qualityoflife.TradeTooltipStack;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "moe.prwk.emitrades.util.EmiIngredients", remap = false)
public abstract class EmiTradesOutputMixin {
  @Inject(method = "asStack", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the exact provider hook.
  private static void aeroscapes$preserveItemIdentity(
      EmiIngredient ingredient, CallbackInfoReturnable<EmiStack> callback) {
    callback.setReturnValue(new TradeTooltipStack(ingredient));
  }
}
