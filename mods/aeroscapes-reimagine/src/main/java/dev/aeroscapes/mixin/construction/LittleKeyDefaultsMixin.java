package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.ConstructionKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "team.creative.littletiles.client.LittleTilesClient", remap = false)
public abstract class LittleKeyDefaultsMixin {
  @ModifyArgs(
      method = "registerKeys",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lteam/creative/littletiles/client/LittleTilesClient$LittleKeyMapping;<init>(Ljava/lang/String;Lnet/neoforged/neoforge/client/settings/IKeyConflictContext;ILjava/lang/String;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native registration callback before saved options load.
  private static void aeroscapes$ordinaryDefaults(Args arguments) {
    arguments.set(2, ConstructionKeys.defaultKey(arguments.get(0), arguments.get(2)));
  }

  @ModifyArgs(
      method = "registerKeys",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lteam/creative/littletiles/client/LittleTilesClient$LittleKeyMapping;<init>(Ljava/lang/String;Lnet/neoforged/neoforge/client/settings/IKeyConflictContext;Lnet/neoforged/neoforge/client/settings/KeyModifier;ILjava/lang/String;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Preserve native Shift, Ctrl and conflict contexts.
  private static void aeroscapes$modifiedDefaults(Args arguments) {
    arguments.set(3, ConstructionKeys.defaultKey(arguments.get(0), arguments.get(3)));
  }
}
