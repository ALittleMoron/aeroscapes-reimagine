package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.natamus.collective_common_neoforge.config.DuskConfigEntry", remap = false)
public abstract class DuskEntryLanguageMixin {
  @ModifyReturnValue(method = "getDisplayName()Ljava/lang/String;", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned display injection.
  private String aeroscapes$displayName(String source) {
    return ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }

  @ModifyReturnValue(
      method = "getOptionLabel(Ljava/lang/String;)Ljava/lang/String;",
      at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned display injection.
  private String aeroscapes$optionLabel(String source) {
    return ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }
}
