package dev.aeroscapes.mixin.trees;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.trees.TreeTransactions;
import ht.treechop.TreeChop;
import ht.treechop.api.ChopEvent;
import ht.treechop.common.chop.FellDataImpl;
import ht.treechop.common.chop.FellTreeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = FellTreeResult.class, remap = false)
public abstract class TreeChopFellMixin {
  @Shadow @Final private FellDataImpl fellData;
  @Shadow @Final private Level level;

  @WrapMethod(method = "apply")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Prepare costs before native events remove any blocks.
  private void aeroscapes$prepare(
      BlockPos position, ServerPlayer player, ItemStack tool, Operation<Void> original) {
    var originalState = level.getBlockState(position);
    if (TreeTransactions.prepareFinal(fellData.getTree())
        && TreeChop.platform.startFellTreeEvent(player, level, position, fellData)
        && TreeTransactions.animate()) {
      // HT 0.19.3's NeoForge finish helper constructs this event without posting it.
      NeoForge.EVENT_BUS.post(
          new ChopEvent.AfterFellEvent(level, player, position, originalState, fellData));
    }
  }
}
