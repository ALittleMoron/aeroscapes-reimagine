package dev.aeroscapes.mixin.technique;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.combat.MartialArchery;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
abstract class MartialArrowImpactMixin {
  @WrapOperation(
      method = "onHitEntity",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$martialArrowHit(
      Entity target, DamageSource source, float damage, Operation<Boolean> original) {
    if (target instanceof LivingEntity living && damage > 0.0F) {
      AbstractArrow arrow = (AbstractArrow) (Object) this;
      boolean landed =
          original.call(target, source, MartialArchery.impactDamage(arrow, living, damage));
      if (landed) MartialArchery.landed(arrow, living);
      return landed;
    }
    return original.call(target, source, damage);
  }
}
