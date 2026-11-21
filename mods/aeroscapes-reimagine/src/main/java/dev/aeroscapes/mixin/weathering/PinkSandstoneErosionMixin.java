package dev.aeroscapes.mixin.weathering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.ordana.immersive_weathering.data.block_growths.growths.ConfigurableBlockGrowth;
import com.ordana.immersive_weathering.reg.ModBlocks;
import dev.aeroscapes.weathering.PinkSandstoneErosion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ConfigurableBlockGrowth.class, remap = false)
public abstract class PinkSandstoneErosionMixin {
  @WrapOperation(
      method = "tryGrowing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/state/BlockState;canSurvive(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Loose sand can otherwise spawn in mid-air.
  private boolean aeroscapes$supportedSand(
      BlockState deposit,
      LevelReader level,
      BlockPos position,
      Operation<Boolean> original,
      @Local(argsOnly = true) BlockState source) {
    if (PinkSandstoneErosion.randomTicks(source)
        && deposit.is(ModBlocks.RED_SAND_LAYER_BLOCK.get())) {
      BlockPos support = position.below();
      if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) return false;
    }
    return original.call(deposit, level, position);
  }
}
