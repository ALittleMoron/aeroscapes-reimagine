package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.ecology.BirdHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.hybridlabs.hapi.entity.base.flying.BaseFlyingAnimal$Companion", remap = false)
public abstract class HybridBirdSkyMixin {
  @ModifyExpressionValue(
      method = "canBirdSpawn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/LevelAccessor;canSeeSky(Lnet/minecraft/core/BlockPos;)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native light and solid landing requirements remain in place.
  private boolean aeroscapes$lowerExterior(
      boolean original,
      @Local(argsOnly = true) LevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return original || BirdHabitat.exterior(level, position);
  }
}
