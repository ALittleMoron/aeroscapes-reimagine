package dev.aeroscapes.mixin.trees;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.trees.TreeTransactions;
import ht.treechop.api.ChopEvent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(
    targets = "dev.pandasystems.fallingtrees.neoforge.compat.TreeChopCompatImpl$Companion",
    remap = false)
public abstract class PandaTreeChopMixin {
  @WrapMethod(method = "beforeFellEvent")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native skip-upward scanning omits chopped stump logs.
  private void aeroscapes$fall(ChopEvent.BeforeFellEvent event, Operation<Void> original) {
    // The owned final transaction waits for every BeforeFell listener before removing blocks.
    if (!TreeTransactions.managedFall()) original.call(event);
  }
}
