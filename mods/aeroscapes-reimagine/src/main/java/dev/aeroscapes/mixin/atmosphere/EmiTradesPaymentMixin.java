package dev.aeroscapes.mixin.atmosphere;

import dev.emi.emi.api.stack.EmiIngredient;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "moe.prwk.emitrades.recipes.TradeEntry", remap = false)
public abstract class EmiTradesPaymentMixin {
  @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned constructor hook.
  private static List<EmiIngredient> aeroscapes$preservePaymentSlots(
      List<EmiIngredient> ingredients) {
    // The provider displays two payment slots but accumulates villager-type variants in this list.
    return List.copyOf(ingredients.subList(0, 2));
  }
}
