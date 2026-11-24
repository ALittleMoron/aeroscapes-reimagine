package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.interfaceart.OvergearedScreenText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.client.ForgingRecipeBookComponent", remap = false)
abstract class OvergearedRecipeBookLanguageMixin {
  @ModifyArg(
      method = "setupGhostRecipe",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Injected into the provider's ghost-recipe warning.
  private static String aeroscapes$translateTierHint(String hint) {
    return OvergearedScreenText.display(hint);
  }
}
