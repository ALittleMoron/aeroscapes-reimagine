package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(
    targets = "fuzs.puzzleslib.neoforge.impl.client.config.NeoForgeConfigurationScreen",
    remap = false)
public abstract class PuzzlesConfigurationLanguageMixin {
  @ModifyArg(
      method = {"getConfigTitleComponent", "getConfigSectionComponent"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Translate the final display title only.
  private static String aeroscapes$translateConfigTitle(String source) {
    return ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }

  @ModifyArg(
      method = "getConfigValueComponent",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Translate only the final label, after native casing.
  private static String aeroscapes$translateConfigLabel(String source) {
    return ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }
}
