package dev.aeroscapes.mixin.trees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.trees.TreeTransactions;
import dev.pandasystems.fallingtrees.entity.TreeEntity;
import ht.treechop.common.chop.ChopUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TreeEntity.class, remap = false)
public abstract class PandaTreeSnapshotMixin {
  @WrapOperation(
      method = "setData",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The falling model renders original wood, including cuts.
  private BlockState aeroscapes$wood(
      Level level, BlockPos position, Operation<BlockState> original) {
    return TreeTransactions.managedFall()
        ? ChopUtil.getLogState(level, position)
        : original.call(level, position);
  }
}
