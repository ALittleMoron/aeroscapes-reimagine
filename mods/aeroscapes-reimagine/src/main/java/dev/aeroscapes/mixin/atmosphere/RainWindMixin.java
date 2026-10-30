package dev.aeroscapes.mixin.atmosphere;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import pigcart.particlerain.ParticleRain;

@Mixin(value = ParticleRain.class, remap = false)
public abstract class RainWindMixin {
  @ModifyConstant(
      method = "getWind(DDD)Lorg/joml/Vector3f;",
      constant = @Constant(floatValue = 0F, ordinal = 0),
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The disabled altitude branch must retain native wind.
  private static float aeroscapes$unscaledWind(float original) {
    return 1F;
  }
}
