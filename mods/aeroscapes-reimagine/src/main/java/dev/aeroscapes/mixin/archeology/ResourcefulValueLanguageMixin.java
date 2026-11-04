package dev.aeroscapes.mixin.archeology;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    targets = "com.teamresourceful.resourcefulconfig.api.types.options.TranslatableValue",
    remap = false)
public abstract class ResourcefulValueLanguageMixin {
  @Shadow
  public abstract String value();

  @Shadow
  public abstract String translation();

  @Inject(method = "toComponent", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Recognized literal labels only; native keys win.
  private void aeroscapes$literalComponent(CallbackInfoReturnable<MutableComponent> callback) {
    String key = ConfigLiteralTranslations.key(value());
    if (translation().isBlank() && Language.getInstance().has(key))
      callback.setReturnValue(Component.translatable(key));
  }

  @Inject(method = "toLocalizedString", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Searching uses the same translated display labels.
  private void aeroscapes$literalString(CallbackInfoReturnable<String> callback) {
    String key = ConfigLiteralTranslations.key(value());
    if (translation().isBlank() && Language.getInstance().has(key))
      callback.setReturnValue(Language.getInstance().getOrDefault(key));
  }
}
