package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ConfigurationScreen.ConfigurationSectionScreen.class, remap = false)
public abstract class NativeConfigurationLanguageMixin {
  @ModifyReturnValue(method = "getComment", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native tooltip translations retain their priority.
  private String aeroscapes$translateFallbackComment(String comment) {
    return comment == null
        ? null
        : ConfigLiteralTranslations.translateCommentBlock(
            comment, Language.getInstance()::getOrDefault);
  }

  @ModifyExpressionValue(
      method = "lambda$createEnumValue$12",
      at = @At(value = "INVOKE", target = "Ljava/lang/Enum;name()Ljava/lang/String;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The TranslatableEnum branch remains native.
  private static String aeroscapes$translateEnumLabel(String name) {
    return ConfigLiteralTranslations.translate(name, Language.getInstance()::getOrDefault);
  }
}
