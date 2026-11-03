package dev.aeroscapes.mixin.movement;

import dev.aeroscapes.movement.MovementLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "tictim.paraglider.client.screen.StaminaWheelSettingScreen", remap = false)
public abstract class ParagliderWheelLanguageMixin {
  @ModifyArg(
      method = "lambda$init$2",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Localize the label without changing serialized enums.
  private static String aeroscapes$translateExtraWheel(String text) {
    return MovementLanguage.extraWheel(text);
  }
}
