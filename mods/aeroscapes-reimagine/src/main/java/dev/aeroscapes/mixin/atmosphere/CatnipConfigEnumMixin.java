package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.createmod.catnip.config.ui.entries.EnumEntry", remap = false)
public abstract class CatnipConfigEnumMixin {
  @ModifyArg(
      method = "onValueChange(Ljava/lang/Enum;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/createmod/catnip/gui/element/TextStencilElement;withText(Ljava/lang/String;)Lnet/createmod/catnip/gui/element/TextStencilElement;"),
      index = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Only the display text changes; the enum stays native.
  private String aeroscapes$translateEnumDisplay(String source) {
    return ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }
}
