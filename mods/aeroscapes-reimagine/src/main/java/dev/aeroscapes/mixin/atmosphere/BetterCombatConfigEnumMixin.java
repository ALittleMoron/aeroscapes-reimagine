package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "me.shedaniel.autoconfig.gui.DefaultGuiProviders", remap = false)
abstract class BetterCombatConfigEnumMixin {
  @WrapOperation(
      method = "lambda$static$0",
      at = @At(value = "INVOKE", target = "Ljava/lang/Enum;toString()Ljava/lang/String;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The native enum name provider covers the dropdown and its
  // values.
  private static String aeroscapes$combatModeLabel(Enum<?> value, Operation<String> original) {
    if (value.getDeclaringClass().getName().equals("net.bettercombat.config.TriStateAuto")) {
      return "aeroscapes_reimagine.config.bettercombat.first_person." + value.name();
    }
    return original.call(value);
  }
}
