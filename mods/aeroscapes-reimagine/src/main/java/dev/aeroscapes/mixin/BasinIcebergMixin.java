package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.BasinWaterline;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.IcebergFeature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IcebergFeature.class)
public abstract class BasinIcebergMixin {
  @ModifyExpressionValue(
      method = "place",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Pinned native iceberg origin rebasing.
  private int aeroscapesReimagine$waterline(
      int nativeSeaLevel,
      @Local(argsOnly = true) FeaturePlaceContext<BlockStateConfiguration> context) {
    return BasinWaterline.at(context.level(), context.origin(), nativeSeaLevel);
  }
}
