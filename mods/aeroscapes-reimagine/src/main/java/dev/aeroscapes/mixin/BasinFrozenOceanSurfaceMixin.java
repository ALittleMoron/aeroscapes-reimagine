package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.BasinWaterline;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SurfaceSystem.class)
public abstract class BasinFrozenOceanSurfaceMixin {
  @Unique private RandomState aeroscapesReimagine$random;

  @Inject(method = "<init>", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Retain this surface system's immutable seeded state.
  private void aeroscapesReimagine$state(
      RandomState random,
      BlockState defaultBlock,
      int seaLevel,
      PositionalRandomFactory positional,
      CallbackInfo callback) {
    aeroscapesReimagine$random = random;
  }

  @ModifyExpressionValue(
      method = "frozenOceanExtension",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/world/level/levelgen/SurfaceSystem;seaLevel:I"),
      require = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // All four native ice/snow limits use this column.
  private int aeroscapesReimagine$waterline(
      int nativeSeaLevel,
      @Local(argsOnly = true, ordinal = 1) int x,
      @Local(argsOnly = true, ordinal = 2) int z) {
    return BasinWaterline.at(aeroscapesReimagine$random, x, z, nativeSeaLevel);
  }

  @ModifyArg(
      method = "frozenOceanExtension",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;"),
      index = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Evaluate melting at the actual local water surface.
  private int aeroscapesReimagine$temperatureHeight(
      int nativeHeight,
      @Local(argsOnly = true, ordinal = 1) int x,
      @Local(argsOnly = true, ordinal = 2) int z) {
    return BasinWaterline.at(aeroscapesReimagine$random, x, z, nativeHeight);
  }
}
