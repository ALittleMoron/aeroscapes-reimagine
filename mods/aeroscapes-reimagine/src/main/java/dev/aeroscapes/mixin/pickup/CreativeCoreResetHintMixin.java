package dev.aeroscapes.mixin.pickup;

import dev.aeroscapes.pickup.CreativeCoreLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import team.creative.creativecore.common.config.gui.GuiConfigControl;

@Mixin(value = GuiConfigControl.class, remap = false)
public abstract class CreativeCoreResetHintMixin {
  @ModifyArg(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lteam/creative/creativecore/common/util/text/TextBuilder;text(Ljava/lang/String;)Lteam/creative/creativecore/common/util/text/TextBuilder;"),
      index = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The native reset tooltip bypasses language resources.
  private String aeroscapes$translateResetHint(String text) {
    return CreativeCoreLanguage.translate(text);
  }
}
