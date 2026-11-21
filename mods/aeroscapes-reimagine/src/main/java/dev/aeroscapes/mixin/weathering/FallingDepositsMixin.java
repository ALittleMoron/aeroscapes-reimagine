package dev.aeroscapes.mixin.weathering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ordana.immersive_weathering.entities.FallingLayerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FallingLayerEntity.class, remap = false)
public abstract class FallingDepositsMixin extends FallingBlockEntity {
  protected FallingDepositsMixin(EntityType<? extends FallingBlockEntity> type, Level level) {
    super(type, level);
  }

  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/state/BlockState;canBeReplaced(Lnet/minecraft/world/item/context/BlockPlaceContext;)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Landing can add layers to the same kind of deposit.
  private boolean aeroscapes$protectLanding(
      BlockState target, BlockPlaceContext context, Operation<Boolean> original) {
    return target.isAir()
        || target.is(getBlockState().getBlock()) && original.call(target, context);
  }

  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
              ordinal = 1))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Excess layers use a second placement above the landing.
  private boolean aeroscapes$protectOverflow(
      Level level, BlockPos position, BlockState deposit, int flags, Operation<Boolean> original) {
    return level.getBlockState(position).isAir() && original.call(level, position, deposit, flags);
  }
}
