package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.PrecipitationPolicy;
import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pigcart.particlerain.config.ParticleData;
import pigcart.particlerain.particle.CustomParticle;

@Mixin(value = CustomParticle.class, remap = false)
public abstract class PrecipitationParticleMixin implements ParticleSubLevelKickable {
  @Unique private boolean aeroscapes$airbornePrecipitation;

  @Inject(method = "<init>", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Mixin records the completed particle's scope before add.
  private void aeroscapes$selectPrecipitation(
      ClientLevel level, double x, double y, double z, ParticleData data, CallbackInfo callback) {
    aeroscapes$airbornePrecipitation =
        PrecipitationPolicy.isAirbornePrecipitation(
            data.id,
            data.spawnPos == ParticleData.SpawnPos.SKY
                && data.particleStyle == ParticleData.ParticleStyle.CUSTOM);
  }

  // PR owns precipitation collision through its raycast, including Sable's transformed clip.
  @Override
  public boolean sable$shouldCareAboutIntersectingSubLevels() {
    return !aeroscapes$airbornePrecipitation;
  }

  @Override
  public boolean sable$shouldCollideWithTrackingSubLevel() {
    return !aeroscapes$airbornePrecipitation;
  }

  @Override
  public boolean sable$shouldKickFromTracking() {
    return true;
  }
}
