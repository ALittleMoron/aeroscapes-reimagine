package dev.aeroscapes.mixin.technique;

import dev.aeroscapes.equipment.combat.SmokeArrows;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sensing.class)
abstract class SmokeSensingMixin {
  @Shadow @Final private Mob mob;

  @Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$smokeSight(Entity target, CallbackInfoReturnable<Boolean> callback) {
    if (SmokeArrows.obscures(mob, target)) callback.setReturnValue(false);
  }
}
