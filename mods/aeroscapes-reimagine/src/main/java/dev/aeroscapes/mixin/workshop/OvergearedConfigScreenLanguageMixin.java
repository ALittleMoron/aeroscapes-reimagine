package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.interfaceart.OvergearedScreenText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = {
      "net.stirdrem.overgeared.client.OvergearedConfigScreen",
      "net.stirdrem.overgeared.client.BlacklistEditorScreen"
    },
    remap = false)
abstract class OvergearedConfigScreenLanguageMixin {
  @ModifyArg(
      method = {"<init>", "init"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Injected into the provider's screen setup.
  private static String aeroscapes$translateWidgetLabel(String label) {
    return OvergearedScreenText.display(label);
  }
}
