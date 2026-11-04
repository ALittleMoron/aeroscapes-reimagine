package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.aquatic.AquaticWater;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "eu.midnightdust.motschen.rocks.world.feature.UnderwaterFeature", remap = false)
public abstract class DebrisUnderwaterFloorMixin {
  @WrapOperation(
      method = "place",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/WorldGenLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The global ocean floor may be on a higher island.
  private int aeroscapes$basinFloor(
      WorldGenLevel level, Heightmap.Types type, int x, int z, Operation<Integer> original) {
    return AquaticWater.floor(level, x, z, original.call(level, type, x, z));
  }
}
