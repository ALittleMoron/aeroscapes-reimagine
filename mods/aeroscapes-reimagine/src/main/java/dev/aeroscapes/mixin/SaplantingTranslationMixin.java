package dev.aeroscapes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Saplanting 1.3.1's save tooltip uses a plural prefix absent from its dictionary. */
@Pseudo
@Mixin(targets = "cool.muyucloud.saplanting.util.Translation", remap = false)
public abstract class SaplantingTranslationMixin {
  @ModifyVariable(method = "translate", at = @At("HEAD"), argsOnly = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned native translation hook.
  private static String aeroscapesReimagine$openFileKey(String key) {
    return key.equals("commands.saplanting.file.open") ? "command.saplanting.file.open" : key;
  }
}
