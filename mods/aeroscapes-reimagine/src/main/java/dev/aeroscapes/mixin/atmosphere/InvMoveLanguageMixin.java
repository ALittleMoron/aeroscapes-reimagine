package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "me.pieking1215.invmove.InvMove", remap = false)
public abstract class InvMoveLanguageMixin {
  @ModifyVariable(method = "literalComponent", at = @At("HEAD"), argsOnly = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Display strings retain native configuration values.
  private String aeroscapes$translateLiteral(String text) {
    return ConfigLiteralTranslations.translate(text, Language.getInstance()::getOrDefault);
  }
}
