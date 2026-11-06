package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.MiscTranslations;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.mehvahdjukaar.moonlight.api.events.AfterLanguageLoadEvent", remap = false)
public abstract class MiscTranslationFallbackMixin {
  @Inject(method = "<init>", at = @At("TAIL"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Before listeners generate English/component defaults.
  private void aeroscapesReimagine$beforeGeneratedDefaults(
      Map<String, String> translations, List<String> languages, CallbackInfo callback) {
    MiscTranslations.apply(translations, languages).forEach(translations::putIfAbsent);
  }
}
