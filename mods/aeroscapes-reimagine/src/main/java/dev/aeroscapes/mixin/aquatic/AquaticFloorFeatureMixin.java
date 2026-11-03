package dev.aeroscapes.mixin.aquatic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.aquatic.AquaticWater;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = {
      "dev.hybridlabs.aquatic.world.gen.feature.kelp.BullKelpFeature",
      "dev.hybridlabs.aquatic.world.gen.feature.kelp.SargassumFeature",
      "dev.hybridlabs.aquatic.world.gen.feature.kelp.DelesseriaFeature",
      "dev.hybridlabs.aquatic.world.gen.feature.algae.RedAlgaePatchFeature",
      "dev.hybridlabs.aquatic.world.gen.feature.algae.SeaLettuceFeature"
    },
    remap = false)
public abstract class AquaticFloorFeatureMixin {
  @WrapOperation(
      method = "place",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/WorldGenLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // These five providers resample after native placement.
  private int aeroscapes$seabed(
      WorldGenLevel level, Heightmap.Types type, int x, int z, Operation<Integer> original) {
    return AquaticWater.floor(level, x, z, original.call(level, type, x, z));
  }
}
