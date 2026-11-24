package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.interfaceart.OvergearedScreenText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(
    targets = {
      "net.stirdrem.overgeared.client.OvergearedConfigScreen$HeaderEntry",
      "net.stirdrem.overgeared.client.OvergearedConfigScreen$BooleanEntry",
      "net.stirdrem.overgeared.client.OvergearedConfigScreen$IntEntry",
      "net.stirdrem.overgeared.client.OvergearedConfigScreen$DoubleEntry",
      "net.stirdrem.overgeared.client.OvergearedConfigScreen$BlacklistEntry",
      "net.stirdrem.overgeared.client.OvergearedConfigScreen$ManualInputEntry"
    },
    remap = false)
abstract class OvergearedConfigEntryLanguageMixin {
  @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Injected into the provider's row constructors.
  private static String aeroscapes$translateEntryLabel(String label) {
    return OvergearedScreenText.display(label);
  }
}
