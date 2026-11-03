package dev.aeroscapes.mixin.movement;

import dev.aeroscapes.movement.MovementLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "tictim.paraglider.client.screen.BargainScreen$BargainButton", remap = false)
public abstract class ParagliderPreviewLanguageMixin {
  @ModifyArg(
      method = "renderTooltip0",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Empty recipe previews use a native literal tooltip.
  private String aeroscapes$translatePreview(String text) {
    return MovementLanguage.preview(text);
  }
}
