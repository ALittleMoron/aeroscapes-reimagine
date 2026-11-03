package dev.aeroscapes.mixin.movement;

import dev.aeroscapes.movement.MovementLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.createmod.catnip.config.ui.ConfigScreenList$LabeledEntry", remap = false)
public abstract class ParagliderForeignConfigurationLanguageMixin {
  @ModifyVariable(method = "<init>(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Scope the state before generic literal lookup.
  private String aeroscapes$translateMovementState(String source) {
    return MovementLanguage.foreignState(CatnipConfigurationAccess.aeroscapes$modId(), source);
  }
}
