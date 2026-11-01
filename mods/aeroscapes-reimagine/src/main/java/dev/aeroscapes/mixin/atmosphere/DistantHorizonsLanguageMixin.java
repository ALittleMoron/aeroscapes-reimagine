package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import java.util.List;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.gui.GuiHelper_neoforge", remap = false)
public abstract class DistantHorizonsLanguageMixin {
  @ModifyVariable(
      method = {"TextOrTranslatable", "TextOrLiteral"},
      at = @At("HEAD"),
      argsOnly = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native helpers bypass language resources for literals.
  private static String aeroscapes$translateDisplay(String source) {
    Language language = Language.getInstance();
    String translated = ConfigLiteralTranslations.translate(source, language::getOrDefault);
    if (!translated.equals(source)) return translated;
    for (String prefix :
        List.of("§cMinimum length is ", "§cMaximum length is ", "§lChangelog for ")) {
      if (source.startsWith(prefix)) {
        return ConfigLiteralTranslations.translate(prefix, language::getOrDefault)
            + source.substring(prefix.length());
      }
    }
    return source;
  }
}
