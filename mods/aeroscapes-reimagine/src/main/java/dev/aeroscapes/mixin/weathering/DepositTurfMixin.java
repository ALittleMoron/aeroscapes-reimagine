package dev.aeroscapes.mixin.weathering;

import com.ordana.immersive_weathering.blocks.LeafPileBlock;
import com.ordana.immersive_weathering.blocks.SandLayerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpreadingSnowyDirtBlock.class)
public abstract class DepositTurfMixin {
  @Inject(method = "canBeGrass", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // A full deposit can block light without killing turf.
  private static void aeroscapes$preserveTurf(
      BlockState state,
      LevelReader level,
      BlockPos position,
      CallbackInfoReturnable<Boolean> result) {
    Block cover = level.getBlockState(position.above()).getBlock();
    if (cover instanceof LeafPileBlock || cover instanceof SandLayerBlock)
      result.setReturnValue(true);
  }
}
