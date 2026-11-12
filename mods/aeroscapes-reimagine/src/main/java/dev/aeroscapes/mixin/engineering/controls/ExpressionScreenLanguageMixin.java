package dev.aeroscapes.mixin.engineering.controls;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import dev.aeroscapes.engineering.GaugeExpressionTranslations;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "net.liukrast.eg.content.logistics.board.ExpressionPanelScreen", remap = false)
public abstract class ExpressionScreenLanguageMixin {
  @ModifyConstant(method = "renderWindow", constant = @Constant(stringValue = "variables:"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the Mixin injector.
  private String aeroscapes$variablesLabel(String source) {
    return ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }

  @ModifyExpressionValue(
      method = "evaluateExpression",
      at = @At(value = "INVOKE", target = "Ljava/lang/Exception;getMessage()Ljava/lang/String;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the Mixin injector.
  private String aeroscapes$expressionError(String source) {
    return GaugeExpressionTranslations.translate(source, I18n::get);
  }
}
