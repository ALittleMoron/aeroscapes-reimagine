package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
abstract class ArrowVelocityDamageReforgeMixin {
  @ModifyExpressionValue(
      method = "onHitEntity",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private double aeroscapes$reforgedArrowDamage(double damage) {
    return RangedReforges.arrowImpactDamage((AbstractArrow) (Object) this, damage);
  }
}
