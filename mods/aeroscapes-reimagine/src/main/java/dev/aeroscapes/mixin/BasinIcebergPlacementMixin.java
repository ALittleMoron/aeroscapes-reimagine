package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.BasinWaterline;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.IcebergFeature;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InSquarePlacement.class)
public abstract class BasinIcebergPlacementMixin {
  @ModifyReturnValue(method = "getPositions", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Rebase after native X/Z draws, before biome filtering.
  private Stream<BlockPos> aeroscapesReimagine$waterline(
      Stream<BlockPos> positions, @Local(argsOnly = true) PlacementContext context) {
    if (context.topFeature().isEmpty()
        || !(context.topFeature().orElseThrow().feature().value().feature()
            instanceof IcebergFeature)) return positions;
    return positions.map(
        position -> position.atY(BasinWaterline.at(context.getLevel(), position, position.getY())));
  }
}
