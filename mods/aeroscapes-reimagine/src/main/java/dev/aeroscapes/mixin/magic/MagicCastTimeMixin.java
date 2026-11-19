package dev.aeroscapes.mixin.magic;

import dev.aeroscapes.magic.MagicBonuses;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractSpell.class, remap = false)
abstract class MagicCastTimeMixin {
  @Inject(method = "getEffectiveCastTime", at = @At("RETURN"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$directionCastTime(
      int level, LivingEntity caster, CallbackInfoReturnable<Integer> callback) {
    callback.setReturnValue(
        MagicBonuses.castTime((AbstractSpell) (Object) this, caster, callback.getReturnValue()));
  }
}
