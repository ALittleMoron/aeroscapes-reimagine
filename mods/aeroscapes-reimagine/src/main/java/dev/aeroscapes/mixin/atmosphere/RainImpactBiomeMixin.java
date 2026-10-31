package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class RainImpactBiomeMixin {
  @WrapOperation(
      method = "tickRain",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep Particle Rain's separate weather-audio branch.
  private void aeroscapes$localImpactWeather(
      ClientLevel level,
      ParticleOptions particle,
      double x,
      double y,
      double z,
      double velocityX,
      double velocityY,
      double velocityZ,
      Operation<Void> original,
      @Local(index = 11) BlockPos position) {
    if (level.getBiome(position).value().getPrecipitationAt(position) == Biome.Precipitation.RAIN) {
      original.call(level, particle, x, y, z, velocityX, velocityY, velocityZ);
    }
  }
}
