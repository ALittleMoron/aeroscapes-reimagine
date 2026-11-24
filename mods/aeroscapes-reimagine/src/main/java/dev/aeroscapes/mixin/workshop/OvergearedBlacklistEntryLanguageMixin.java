package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.interfaceart.OvergearedScreenText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = {
      "net.stirdrem.overgeared.client.OvergearedConfigScreen$BlacklistEntry",
      "net.stirdrem.overgeared.client.BlacklistEditorScreen$BlacklistItemEntry"
    },
    remap = false)
abstract class OvergearedBlacklistEntryLanguageMixin {
  @ModifyArg(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Injected into the provider's blacklist row setup.
  private static String aeroscapes$translateButton(String caption) {
    return OvergearedScreenText.display(caption);
  }
}
