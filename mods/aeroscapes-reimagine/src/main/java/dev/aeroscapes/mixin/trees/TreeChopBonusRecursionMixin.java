package dev.aeroscapes.mixin.trees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.trees.TreeTransactions;
import ht.treechop.api.ChopEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "ht.treechop.compat.NoChopRecursion$EventHandler", remap = false)
abstract class TreeChopBonusRecursionMixin {
  @WrapOperation(
      method = "onChop",
      at =
          @At(
              value = "INVOKE",
              target = "Lht/treechop/api/ChopEvent$StartChopEvent;setCanceled(Z)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Leave every other cancellation authoritative.
  private static void aeroscapes$allowOneBonus(
      ChopEvent.StartChopEvent event, boolean canceled, Operation<Void> original) {
    if (!TreeTransactions.consumeBonusPermission(event)) original.call(event, canceled);
  }
}
