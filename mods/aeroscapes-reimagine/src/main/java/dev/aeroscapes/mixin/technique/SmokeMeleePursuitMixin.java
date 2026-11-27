package dev.aeroscapes.mixin.technique;

import dev.aeroscapes.equipment.combat.SmokeArrows;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MeleeAttackGoal.class)
abstract class SmokeMeleePursuitMixin {
  @Shadow @Final protected PathfinderMob mob;

  @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$startTracking(CallbackInfoReturnable<Boolean> callback) {
    if (SmokeArrows.obscures(mob, mob.getTarget())) callback.setReturnValue(false);
  }

  @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$updateTracking(CallbackInfo callback) {
    if (SmokeArrows.obscures(mob, mob.getTarget())) callback.cancel();
  }
}
