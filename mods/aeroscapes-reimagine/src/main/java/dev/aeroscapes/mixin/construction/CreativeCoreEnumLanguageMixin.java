package dev.aeroscapes.mixin.construction;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = "team.creative.creativecore.common.config.converation.ConfigTypeConveration$7",
    remap = false)
public abstract class CreativeCoreEnumLanguageMixin {
  @ModifyExpressionValue(
      method = "lambda$createControls$0",
      at = @At(value = "INVOKE", target = "Ljava/lang/Enum;name()Ljava/lang/String;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Only the display caption changes, never the enum value.
  private static String aeroscapes$translateMaterialTier(
      String name, @Local(argsOnly = true) Object value) {
    return value instanceof Enum<?> entry
        ? ConfigLiteralTranslations.translateEnum(entry, name, Language.getInstance()::getOrDefault)
        : name;
  }
}
