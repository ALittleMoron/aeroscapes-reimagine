package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.createmod.catnip.config.ui.ConfigScreenList$LabeledEntry", remap = false)
public abstract class CatnipConfigLabelMixin {
  @ModifyArg(
      method = "<init>(Ljava/lang/String;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/createmod/catnip/gui/element/TextStencilElement;<init>(Lnet/minecraft/client/gui/Font;Ljava/lang/String;)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The display string never replaces the native path.
  private String aeroscapes$translateLabel(String source) {
    return ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }

  @ModifyExpressionValue(
      method = "render",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnet/createmod/catnip/config/ui/ConfigScreenList$LabeledEntry;unit:Ljava/lang/String;"),
      require = 3)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep metadata native; measure and draw the same text.
  private String aeroscapes$translateDisplayedUnit(String source) {
    return source == null
        ? null
        : ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }
}
