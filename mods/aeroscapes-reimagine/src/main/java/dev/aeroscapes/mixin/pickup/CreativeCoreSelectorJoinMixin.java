package dev.aeroscapes.mixin.pickup;

import dev.aeroscapes.pickup.CreativeCoreLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(
    targets = {
      "team.creative.creativecore.common.util.player.PlayerSelector$PlayerSelectorAnd",
      "team.creative.creativecore.common.util.player.PlayerSelector$PlayerSelectorOr"
    },
    remap = false)
public abstract class CreativeCoreSelectorJoinMixin {
  @ModifyArg(
      method = "info",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/MutableComponent;append(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Translate conjunctions without changing selector trees.
  private String aeroscapes$translateSelectorJoin(String text) {
    return CreativeCoreLanguage.translate(text);
  }
}
