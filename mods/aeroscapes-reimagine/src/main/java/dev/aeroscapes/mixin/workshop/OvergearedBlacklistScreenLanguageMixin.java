package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.interfaceart.OvergearedScreenText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.client.BlacklistEditorScreen", remap = false)
abstract class OvergearedBlacklistScreenLanguageMixin {
  @ModifyArg(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Injected into the provider's blacklist draw calls.
  private static String aeroscapes$translateCaption(String caption) {
    return OvergearedScreenText.display(caption);
  }
}
