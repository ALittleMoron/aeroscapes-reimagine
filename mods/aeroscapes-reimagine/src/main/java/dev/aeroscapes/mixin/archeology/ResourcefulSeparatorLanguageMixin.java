package dev.aeroscapes.mixin.archeology;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
    targets = "com.teamresourceful.resourcefulconfig.client.components.options.OptionsListWidget",
    remap = false)
public abstract class ResourcefulSeparatorLanguageMixin {
  @Redirect(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Separators bypass TranslatableValue in 3.0.11.
  private MutableComponent aeroscapes$separator(String source) {
    String key = ConfigLiteralTranslations.key(source);
    return Language.getInstance().has(key)
        ? Component.translatable(key)
        : Component.literal(source);
  }
}
