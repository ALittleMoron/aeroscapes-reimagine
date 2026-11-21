package dev.aeroscapes.mixin.weathering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ordana.immersive_weathering.data.block_growths.growths.builtin.LeavesGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LeavesGrowth.class, remap = false)
public abstract class LeafDepositsMixin {
  @Inject(method = "tryPlacingAt", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Natural leaf fall may stack only matching leaves.
  private void aeroscapes$protectOccupiedGround(
      ServerLevel level, Block pile, BlockPos position, int maximumLayers, CallbackInfo callback) {
    BlockState target = level.getBlockState(position);
    if (!target.isAir() && !target.is(pile)) callback.cancel();
  }

  @WrapOperation(
      method = "decayLeavesPile",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // A removed waterlogged leaf may leave a water source.
  private static boolean aeroscapes$onlyReplaceAir(
      ServerLevel level,
      BlockPos position,
      BlockState pile,
      int flags,
      Operation<Boolean> original) {
    return level.getBlockState(position).isAir() && original.call(level, position, pile, flags);
  }
}
