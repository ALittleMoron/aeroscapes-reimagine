package dev.aeroscapes.mixin.magic.travel;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.magic.travel.TravelMagicPolicy;
import io.redspace.ironsspellbooks.spells.holy.AngelWingsSpell;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AngelWingsSpell.class, remap = false)
abstract class AngelWingsSpellMixin {
  @Inject(method = "getEffectDuration", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$capDuration(
      int level, LivingEntity caster, CallbackInfoReturnable<Integer> callback) {
    AngelWingsSpell spell = (AngelWingsSpell) (Object) this;
    callback.setReturnValue(TravelMagicPolicy.wingsDuration(spell.getSpellPower(level, caster)));
  }

  @ModifyExpressionValue(
      method = "getUniqueInfo",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/spells/holy/AngelWingsSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$showCappedDuration(float power) {
    return TravelMagicPolicy.wingsDuration(power) / 20.0F;
  }
}
