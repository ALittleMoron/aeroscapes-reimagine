package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.LittleSableGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.creative.littletiles.common.block.mc.BlockTile;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState", remap = false)
public abstract class LittleSableShapeContextMixin {
  @Inject(
      method =
          "isSolid(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
      at = @At("HEAD"),
      cancellable = true,
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // LT geometry belongs to this BE, not the state cache.
  private static void aeroscapes$solidAtTile(
      BlockGetter level,
      BlockPos position,
      BlockState state,
      CallbackInfoReturnable<Boolean> result) {
    if (state.getBlock() instanceof BlockTile)
      result.setReturnValue(!LittleSableGeometry.collision(level, position, state).isEmpty());
  }

  @Inject(
      method =
          "isFullBlock(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
      at = @At("HEAD"),
      cancellable = true,
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // LT side metadata also marks filled NOCLIP faces.
  private static void aeroscapes$fullAtTile(
      BlockGetter level,
      BlockPos position,
      BlockState state,
      CallbackInfoReturnable<Boolean> result) {
    if (state.getBlock() instanceof BlockTile) {
      var collision = LittleSableGeometry.collision(level, position, state);
      var occupied = LittleSableGeometry.occupied(collision);
      result.setReturnValue(Block.isShapeFullBlock(occupied));
    }
  }
}
