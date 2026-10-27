package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.worldgen.IslandMonumentStructure;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(OceanMonumentPieces.MonumentBuilding.class)
public abstract class MonumentBuildingWaterMixin {
  @ModifyExpressionValue(
      method = "postProcess",
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked at the pinned native Mixin target above.
  private int aeroscapesReimagine$localSeaLevel(int nativeSeaLevel) {
    return IslandMonumentStructure.seaLevel(nativeSeaLevel);
  }
}
