package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.combat.RangedReforges;
import dev.aeroscapes.equipment.combat.ShieldReforges;
import dev.aeroscapes.equipment.combat.WeaponProficiency;
import dev.aeroscapes.races.ClassTraits;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(LivingEntity.class)
abstract class CombatKnockbackReforgeMixin {
  @ModifyConstant(method = "isBlocking", constant = @Constant(intValue = 5))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$raiseTicks(int normal) {
    LivingEntity user = (LivingEntity) (Object) this;
    int raised =
        ClassTraits.shieldDelay(user, ShieldReforges.raiseTicks(user.getUseItem(), normal));
    return WeaponProficiency.shieldRaiseTicks(user, raised);
  }

  @ModifyExpressionValue(
      method = "hurt",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/common/CommonHooks;onDamageBlock(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;Z)Lnet/neoforged/neoforge/event/entity/living/LivingShieldBlockEvent;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private LivingShieldBlockEvent aeroscapes$observeBlock(LivingShieldBlockEvent event) {
    ShieldReforges.observeBlock(event);
    return event;
  }

  @WrapOperation(
      method = "hurt",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$reforgedHurtKnockback(
      LivingEntity entity,
      double strength,
      double x,
      double z,
      Operation<Void> original,
      @Local(argsOnly = true) DamageSource source) {
    double outgoing = RangedReforges.hurtKnockback(entity, source, strength);
    original.call(entity, ShieldReforges.hurtKnockback(entity, source, outgoing), x, z);
  }
}
