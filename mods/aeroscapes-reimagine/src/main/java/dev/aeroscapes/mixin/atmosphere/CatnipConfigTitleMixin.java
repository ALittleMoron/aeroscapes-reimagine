package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.createmod.catnip.config.ui.SubMenuConfigScreen", remap = false)
public abstract class CatnipConfigTitleMixin {
  @ModifyExpressionValue(
      method = "renderWindow",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnet/createmod/catnip/config/ui/SubMenuConfigScreen;title:Ljava/lang/String;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Display-only read; preserve the stored native title.
  private String aeroscapes$translateTitle(String source) {
    return ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }

  @ModifyExpressionValue(
      method = "renderWindow",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/neoforged/fml/config/ModConfig$Type;toString()Ljava/lang/String;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The native enum and selected config type stay intact.
  private String aeroscapes$translateType(String source) {
    return ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }
}
