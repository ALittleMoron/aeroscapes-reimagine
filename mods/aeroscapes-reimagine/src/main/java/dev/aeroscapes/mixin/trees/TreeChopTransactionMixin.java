package dev.aeroscapes.mixin.trees;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.ToolReforges;
import dev.aeroscapes.trees.TreeTransactions;
import ht.treechop.api.ChopData;
import ht.treechop.api.ChopDataImmutable;
import ht.treechop.common.block.ChoppedLogBlock;
import ht.treechop.common.chop.ChopResult;
import ht.treechop.common.chop.ChopUtil;
import ht.treechop.common.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ChopUtil.class, remap = false)
public abstract class TreeChopTransactionMixin {
  @WrapMethod(method = "chopUnsafe")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // One frame spans the nested native felling event.
  private static boolean aeroscapes$transaction(
      ServerPlayer player,
      ServerLevel level,
      BlockPos position,
      BlockState state,
      ItemStack tool,
      Object trigger,
      int chops,
      boolean leaves,
      Operation<Boolean> original) {
    if (TreeTransactions.onShip(level, position)) return false;
    boolean cut;
    boolean continued;
    TreeTransactions.begin(level, player, position);
    try {
      boolean result = original.call(player, level, position, state, tool, trigger, chops, leaves);
      boolean finalCut = TreeTransactions.finalRequested();
      continued = result && !finalCut;
      cut = finalCut || result;
    } finally {
      TreeTransactions.end();
    }
    if (continued
        && trigger instanceof BlockEvent.BreakEvent event
        && !event.isCanceled()
        && state.getBlock() instanceof ChoppedLogBlock
        && ToolReforges.isSurvivalPlayer(player)
        && player.getMainHandItem() == tool
        && ReforgeProfiles.has(tool, "axe_legendary_feller")
        && level.mayInteract(player, position)
        && player.canInteractWithBlock(position, 0.0)
        && player.getRandom().nextFloat() < 0.25F) {
      BlockState next = level.getBlockState(position);
      if (next.getBlock() instanceof ChoppedLogBlock) {
        TreeTransactions.beginBonus(level, player, event);
        try {
          original.call(player, level, position, next, tool, trigger, 1, leaves);
        } finally {
          TreeTransactions.end();
        }
      }
    }
    return cut;
  }

  @WrapOperation(
      method = "chopUnsafe",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lht/treechop/common/platform/Platform;startChopEvent(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lht/treechop/api/ChopData;Ljava/lang/Object;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Public API event keeps cancellation authoritative.
  private static boolean aeroscapes$capture(
      Platform platform,
      ServerPlayer player,
      ServerLevel level,
      BlockPos position,
      BlockState state,
      ChopData data,
      Object trigger,
      Operation<Boolean> original) {
    boolean accepted = original.call(platform, player, level, position, state, data, trigger);
    if (accepted && TreeTransactions.isBonusChop() && data.getNumChops() > 1) data.setNumChops(1);
    return accepted && TreeTransactions.capture(data);
  }

  @WrapOperation(
      method = "chopUnsafe",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;mineBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Final wear is paid once through Panda's native roll.
  private static void aeroscapes$wear(
      ItemStack stack,
      net.minecraft.world.level.Level level,
      BlockState state,
      BlockPos position,
      net.minecraft.world.entity.player.Player player,
      Operation<Void> original) {
    if (!TreeTransactions.finalRequested()) {
      original.call(stack, level, state, position, player);
      TreeTransactions.recordProgress();
    }
  }

  @WrapOperation(
      method = "chopUnsafe",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lht/treechop/common/platform/Platform;finishChopEvent(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lht/treechop/api/ChopDataImmutable;Lht/treechop/common/chop/ChopResult;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // A refused final chop must not report a completed fell.
  private static void aeroscapes$finish(
      Platform platform,
      ServerPlayer player,
      ServerLevel level,
      BlockPos position,
      BlockState state,
      ChopDataImmutable data,
      ChopResult result,
      Operation<Void> original) {
    if (!TreeTransactions.finalRequested() || TreeTransactions.completed()) {
      original.call(platform, player, level, position, state, data, result);
    }
  }
}
