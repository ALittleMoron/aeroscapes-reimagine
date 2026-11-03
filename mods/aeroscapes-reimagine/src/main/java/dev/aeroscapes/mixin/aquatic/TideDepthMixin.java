package dev.aeroscapes.mixin.aquatic;

import com.li64.tide.data.fishing.FishingContext;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.aquatic.AquaticWater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = {
      "com.li64.tide.data.fishing.conditions.types.AboveCondition",
      "com.li64.tide.data.fishing.conditions.types.BelowCondition",
      "com.li64.tide.data.fishing.conditions.types.DepthRangeCondition"
    },
    remap = false)
public abstract class TideDepthMixin {
  @ModifyExpressionValue(
      method = "test",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Keep each native bound and comparison unchanged.
  private int aeroscapes$physicalDepth(int nativeY, FishingContext context) {
    if (!"water".equals(context.medium())) return nativeY;
    return context.hook() == null
        ? AquaticWater.spawnCoordinate(context.level(), context.blockPos())
        : AquaticWater.catchCoordinate(context.level(), context.blockPos());
  }
}
