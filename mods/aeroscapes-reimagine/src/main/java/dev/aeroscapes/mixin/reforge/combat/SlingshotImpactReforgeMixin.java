package dev.aeroscapes.mixin.reforge.combat;

import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    targets = "net.mehvahdjukaar.supplementaries.common.entities.SlingshotProjectileEntity",
    remap = false)
abstract class SlingshotImpactReforgeMixin {
  @Inject(method = "onHitEntity", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$stagger(EntityHitResult hit, CallbackInfo ci) {
    if (hit.getEntity() instanceof LivingEntity target) {
      RangedReforges.slingshotHit((Projectile) (Object) this, target);
    }
  }
}
