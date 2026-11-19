package dev.aeroscapes.mixin.magic;

import dev.aeroscapes.magic.client.AbilityClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla fog, the lightmap and shader uniforms query the viewing player's night vision. */
@Mixin(LivingEntity.class)
public abstract class DwarfNightVisionMixin {
  @Unique private MobEffectInstance aeroscapes$visualNightVision;

  @Unique
  private boolean aeroscapes$dwarfSight(Holder<MobEffect> effect) {
    return effect == MobEffects.NIGHT_VISION
        && (Object) this instanceof LocalPlayer player
        && player.isAlive()
        && !player.isSpectator()
        && AbilityClient.snapshot().dwarfNightVision();
  }

  @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$seeInDark(
      Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> result) {
    if (aeroscapes$dwarfSight(effect)) result.setReturnValue(true);
  }

  @Inject(method = "getEffect", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$fullStrength(
      Holder<MobEffect> effect, CallbackInfoReturnable<MobEffectInstance> result) {
    if (aeroscapes$dwarfSight(effect)) {
      // Keep the actual potion and its remaining duration in vanilla's effect map.
      if (aeroscapes$visualNightVision == null)
        aeroscapes$visualNightVision =
            new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION);
      result.setReturnValue(aeroscapes$visualNightVision);
    }
  }
}
