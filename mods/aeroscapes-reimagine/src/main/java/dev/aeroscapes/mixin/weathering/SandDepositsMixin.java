package dev.aeroscapes.mixin.weathering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ordana.immersive_weathering.blocks.LayerBlock;
import com.ordana.immersive_weathering.blocks.SandLayerBlock;
import com.ordana.immersive_weathering.data.block_growths.growths.builtin.SandGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SandGrowth.class, remap = false)
public abstract class SandDepositsMixin {
  @WrapOperation(
      method = "tryGrowing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The loose deposit is separate from the stone coating.
  private boolean aeroscapes$protectLooseSand(
      ServerLevel level, BlockPos position, BlockState replacement, Operation<Boolean> original) {
    if (!(replacement.getBlock() instanceof SandLayerBlock sand))
      return original.call(level, position, replacement);
    BlockState target = level.getBlockState(position);
    if (target.isAir()) return original.call(level, position, replacement);
    if (!target.is(sand) || sand.getLayers(target) >= sand.getMaxLayers()) return false;
    return original.call(
        level, position, target.setValue(LayerBlock.LAYERS_8, sand.getLayers(target) + 1));
  }
}
