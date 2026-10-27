package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.BasinWaterline;
import net.minecraft.world.level.levelgen.feature.BlueIceFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlueIceFeature.class)
public abstract class BasinBlueIceMixin {
  @ModifyExpressionValue(
      method = "place",
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Pinned native blue-ice sea-level guard.
  private int aeroscapesReimagine$waterline(
      int nativeSeaLevel,
      @Local(argsOnly = true) FeaturePlaceContext<NoneFeatureConfiguration> context) {
    return BasinWaterline.at(context.level(), context.origin(), nativeSeaLevel);
  }
}
