package dev.aeroscapes.mixin.technique;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.combat.MartialTechniques;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class MartialShieldMixin {
  @ModifyExpressionValue(
      method = "hurt",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/common/CommonHooks;onDamageBlock(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;Z)Lnet/neoforged/neoforge/event/entity/living/LivingShieldBlockEvent;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private LivingShieldBlockEvent aeroscapes$martialBlock(LivingShieldBlockEvent event) {
    MartialTechniques.observeBlock(event);
    return event;
  }

  @WrapOperation(
      method = "hurt",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$braceKnockback(
      LivingEntity entity,
      double strength,
      double x,
      double z,
      Operation<Void> original,
      @Local(argsOnly = true) DamageSource source) {
    original.call(entity, MartialTechniques.braceKnockback(entity, source, strength), x, z);
  }
}
