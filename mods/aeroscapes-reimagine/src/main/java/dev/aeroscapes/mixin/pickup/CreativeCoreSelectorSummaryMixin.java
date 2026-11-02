package dev.aeroscapes.mixin.pickup;

import dev.aeroscapes.pickup.CreativeCoreLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(
    targets = {
      "team.creative.creativecore.common.util.player.PlayerSelector$PlayerSelectorLevel",
      "team.creative.creativecore.common.util.player.PlayerSelector$PlayerSelectorCommandSelector"
    },
    remap = false)
public abstract class CreativeCoreSelectorSummaryMixin {
  @ModifyArg(
      method = "info",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep permission values and selector syntax as arguments.
  private String aeroscapes$translateSelectorSummary(String text) {
    return CreativeCoreLanguage.translate(text);
  }
}
