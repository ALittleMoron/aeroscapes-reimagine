package dev.aeroscapes.mixin.magic;

import dev.aeroscapes.magic.MagicBonuses;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractSpell.class, remap = false)
abstract class MagicPowerMixin {
  @Inject(method = "getSpellPower", at = @At("RETURN"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$directionPower(
      int level, Entity caster, CallbackInfoReturnable<Float> callback) {
    callback.setReturnValue(
        MagicBonuses.power((AbstractSpell) (Object) this, caster, callback.getReturnValue()));
  }

  @Inject(method = "getEntityPowerMultiplier", at = @At("RETURN"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$directionMultiplier(
      LivingEntity caster, CallbackInfoReturnable<Float> callback) {
    callback.setReturnValue(
        MagicBonuses.power((AbstractSpell) (Object) this, caster, callback.getReturnValue()));
  }
}
