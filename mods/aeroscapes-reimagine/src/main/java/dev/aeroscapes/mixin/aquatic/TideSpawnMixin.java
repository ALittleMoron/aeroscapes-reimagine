package dev.aeroscapes.mixin.aquatic;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.aquatic.AquaticWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.li64.tide.registries.entities.fish.TideFishEntity", remap = false)
public abstract class TideSpawnMixin {
  @ModifyExpressionValue(
      method = "checkWaterFishSpawnRules",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/level/ServerLevelAccessor;getSeaLevel()I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Lava and void predicates retain their native level.
  private static int aeroscapes$basinSurface(
      int nativeSea,
      @Local(argsOnly = true) ServerLevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return AquaticWater.seaLevel(nativeSea, level, position);
  }
}
