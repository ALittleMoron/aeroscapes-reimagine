package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.LanguageCorrections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ClientLanguage.class)
public abstract class LanguageCorrectionMixin {
  @ModifyArg(
      method = "loadFrom",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/resources/language/ClientLanguage;<init>(Ljava/util/Map;ZLjava/util/Map;)V"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned language-map injection.
  private static Map<String, String> aeroscapes$correctRussianProviderStrings(
      Map<String, String> translations, @Local(argsOnly = true) List<String> languages) {
    return LanguageCorrections.apply(translations, languages);
  }
}
