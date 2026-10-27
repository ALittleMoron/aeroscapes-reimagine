package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.BasinWaterline;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.BlueIceFeature;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HeightRangePlacement.class)
public abstract class BasinBlueIcePlacementMixin {
  @ModifyExpressionValue(
      method = "getPositions",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/heightproviders/HeightProvider;sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Translate the pinned 30..61 draw relative to sea=63.
  private int aeroscapesReimagine$waterline(
      int nativeHeight,
      @Local(argsOnly = true) PlacementContext context,
      @Local(argsOnly = true) BlockPos position) {
    if (context.topFeature().isEmpty()
        || !(context.topFeature().orElseThrow().feature().value().feature()
            instanceof BlueIceFeature)) return nativeHeight;
    int waterline = BasinWaterline.at(context.getLevel(), position, Integer.MIN_VALUE);
    return waterline == Integer.MIN_VALUE ? nativeHeight : nativeHeight + waterline - 63;
  }
}
