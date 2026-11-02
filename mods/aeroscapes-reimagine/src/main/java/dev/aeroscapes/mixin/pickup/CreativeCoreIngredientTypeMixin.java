package dev.aeroscapes.mixin.pickup;

import dev.aeroscapes.pickup.CreativeCoreLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import team.creative.creativecore.common.config.gui.FullItemDialogGuiLayer;

@Mixin(value = FullItemDialogGuiLayer.class, remap = false)
public abstract class CreativeCoreIngredientTypeMixin {
  @ModifyArg(
      method = "lambda$create$1",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Translate the display name, retaining registry keys.
  private static String aeroscapes$translateIngredientType(String text) {
    return CreativeCoreLanguage.translate(text);
  }
}
