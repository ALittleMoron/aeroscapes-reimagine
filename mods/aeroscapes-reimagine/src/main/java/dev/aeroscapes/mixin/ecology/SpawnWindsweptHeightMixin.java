package dev.aeroscapes.mixin.ecology;

import dev.aeroscapes.ecology.ScopedSeaLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.ninni.spawn.server.level.feature.WindsweptGrassFeature", remap = false)
public abstract class SpawnWindsweptHeightMixin {
  @ModifyConstant(method = "place", constant = @Constant(intValue = 66))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native grass starts more than three blocks above sea level.
  private int aeroscapes$localMinimum(int original) {
    int sea = ScopedSeaLevel.resolve(Integer.MIN_VALUE);
    return sea == Integer.MIN_VALUE ? original : sea + 3;
  }
}
