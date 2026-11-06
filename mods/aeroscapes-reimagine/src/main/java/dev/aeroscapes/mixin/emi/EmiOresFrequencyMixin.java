package dev.aeroscapes.mixin.emi;

import cc.abbie.emi_ores.compat.emi.recipe.AbstractPlacedFeatureEmiRecipe;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractPlacedFeatureEmiRecipe.class, remap = false)
public abstract class EmiOresFrequencyMixin {
  @ModifyExpressionValue(
      method = {"anchorText", "anchorTextLongInner"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxBuildHeight()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // BelowTop uses the inclusive top block.
  private static int aeroscapesReimagine$inclusiveTop(int exclusiveTop) {
    return exclusiveTop - 1;
  }

  @Inject(method = "getVeinFreqComponent", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // A rarity filter still gates counted attempts.
  private static void aeroscapesReimagine$combinedFrequency(
      int minimum, int maximum, int rarity, CallbackInfoReturnable<Component> callback) {
    if (minimum >= 0 && maximum >= 0 && rarity > 1)
      callback.setReturnValue(
          Component.translatable(
              "aeroscapes_reimagine.ore_information.combined", minimum, maximum, rarity));
  }
}
