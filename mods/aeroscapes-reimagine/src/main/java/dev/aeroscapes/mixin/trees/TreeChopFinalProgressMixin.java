package dev.aeroscapes.mixin.trees;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.trees.TreeTransactions;
import ht.treechop.common.chop.Chop;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Chop.class, remap = false)
public abstract class TreeChopFinalProgressMixin {
  @WrapMethod(method = "apply")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Cancelled Panda handoff must retain the last paid cut.
  private void aeroscapes$preserve(
      Level level, Player player, ItemStack tool, boolean felling, Operation<Void> original) {
    if (!TreeTransactions.finalRequested()) original.call(level, player, tool, felling);
  }
}
