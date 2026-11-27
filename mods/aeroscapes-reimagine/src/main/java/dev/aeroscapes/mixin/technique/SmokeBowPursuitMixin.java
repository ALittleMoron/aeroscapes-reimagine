package dev.aeroscapes.mixin.technique;

import dev.aeroscapes.equipment.combat.SmokeArrows;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RangedBowAttackGoal.class)
abstract class SmokeBowPursuitMixin {
  @Shadow @Final private Mob mob;

  @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$updateTracking(CallbackInfo callback) {
    if (!SmokeArrows.obscures(mob, mob.getTarget())) return;
    if (mob.isUsingItem()) mob.stopUsingItem();
    callback.cancel();
  }
}
