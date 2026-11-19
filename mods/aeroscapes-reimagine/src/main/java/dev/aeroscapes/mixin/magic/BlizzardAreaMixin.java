package dev.aeroscapes.mixin.magic;

import dev.aeroscapes.magic.MagicBonuses;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.spells.ice.BlizzardSpell;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlizzardSpell.class, remap = false)
abstract class BlizzardAreaMixin {
  @Inject(method = "getRadius", at = @At("RETURN"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$winterFront(
      int level, LivingEntity caster, CallbackInfoReturnable<Float> callback) {
    callback.setReturnValue(
        MagicBonuses.blizzardRadius(
            (AbstractSpell) (Object) this, caster, callback.getReturnValue()));
  }
}
