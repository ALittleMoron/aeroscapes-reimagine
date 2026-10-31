package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = "com.natamus.collective_common_neoforge.config.screen.DuskConfigTextScreen",
    remap = false)
public abstract class DuskTextLanguageMixin {
  @ModifyArg(
      method = "init()V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/FormattedText;of(Ljava/lang/String;)Lnet/minecraft/network/chat/FormattedText;"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned display injection.
  private String aeroscapes$description(String source) {
    return ConfigLiteralTranslations.translateCommentBlock(
        source, Language.getInstance()::getOrDefault);
  }
}
