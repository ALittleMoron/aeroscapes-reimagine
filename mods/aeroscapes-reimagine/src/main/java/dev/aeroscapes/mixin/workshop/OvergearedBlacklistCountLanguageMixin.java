package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.interfaceart.OvergearedScreenText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = "net.stirdrem.overgeared.client.OvergearedConfigScreen$BlacklistEntry",
    remap = false)
abstract class OvergearedBlacklistCountLanguageMixin {
  @ModifyArg(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I",
              ordinal = 1),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Injected into the provider's item-count draw call.
  private static String aeroscapes$translateCount(String caption) {
    return OvergearedScreenText.display(caption);
  }
}
