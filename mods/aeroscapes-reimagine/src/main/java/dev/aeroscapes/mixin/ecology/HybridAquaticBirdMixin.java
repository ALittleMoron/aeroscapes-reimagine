package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.ecology.BirdHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets = "dev.hybridlabs.hapi.entity.base.flying.BaseAquaticBirdEntity$Companion",
    remap = false)
public abstract class HybridAquaticBirdMixin {
  @ModifyReturnValue(method = "canAquaticBirdSpawn", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // NO_RESTRICTIONS otherwise permits unsupported void air.
  private boolean aeroscapes$supportedHabitat(
      boolean original,
      @Local(argsOnly = true) LevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return original && BirdHabitat.supportedAquatic(level, position);
  }
}
