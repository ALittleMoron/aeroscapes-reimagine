package dev.aeroscapes.mixin.reforge;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.aeroscapes.equipment.GliderReforge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class GliderSteeringReforgeMixin {
  @ModifyReturnValue(method = "getFrictionInfluencedSpeed", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$gliderSteering(float inputSpeed) {
    if (!((Object) this instanceof Player player)) return inputSpeed;
    return GliderReforge.steering(player, inputSpeed);
  }

  @Inject(method = "travel", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$rememberGlideMomentum(
      Vec3 movement,
      CallbackInfo callback,
      @Share("aeroscapes$glideVelocity") LocalRef<Vec3> velocity) {
    if (movement.horizontalDistanceSqr() > 0
        && (Object) this instanceof Player player
        && GliderReforge.profile(player).equals("glider_legendary_catch_with_turn_momentum")) {
      velocity.set(player.getDeltaMovement());
    }
  }

  @Inject(method = "travel", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$keepGlideMomentum(
      Vec3 movement,
      CallbackInfo callback,
      @Share("aeroscapes$glideVelocity") LocalRef<Vec3> velocity) {
    if (velocity.get() != null && (Object) this instanceof Player player) {
      GliderReforge.retainMomentum(player, velocity.get(), movement);
    }
  }
}
