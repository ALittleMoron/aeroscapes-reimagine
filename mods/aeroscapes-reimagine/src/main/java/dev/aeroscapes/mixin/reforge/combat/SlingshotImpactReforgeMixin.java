package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    targets = "net.mehvahdjukaar.supplementaries.common.entities.SlingshotProjectileEntity",
    remap = false)
abstract class SlingshotImpactReforgeMixin {
  @WrapOperation(
      method = "onHitEntity",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$nativeHit(
      Entity target,
      DamageSource source,
      float damage,
      Operation<Boolean> original,
      @Share("aeroscapes$damageOutcome") LocalIntRef outcome) {
    boolean ordinaryImpact = !((Projectile) (Object) this).isRemoved();
    boolean accepted = original.call(target, source, damage);
    outcome.set(accepted && ordinaryImpact ? 1 : -1);
    return accepted;
  }

  @Inject(method = "onHitEntity", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$stagger(
      EntityHitResult hit,
      CallbackInfo ci,
      @Share("aeroscapes$damageOutcome") LocalIntRef outcome) {
    if (hit.getEntity() instanceof LivingEntity target) {
      var projectile = (Projectile) (Object) this;
      // Ordinary stones can collide without entering the optional native damage branch.
      boolean landed = outcome.get() > 0 || (outcome.get() == 0 && !projectile.isRemoved());
      RangedReforges.slingshotHit(projectile, target, landed);
    }
  }

  @Inject(
      method = "onHitBlock",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/mehvahdjukaar/moonlight/api/entity/ImprovedProjectileEntity;onHitBlock(Lnet/minecraft/world/phys/BlockHitResult;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$ricochet(BlockHitResult hit, CallbackInfo ci) {
    if (RangedReforges.ricochet((Projectile) (Object) this, hit)) {
      ci.cancel();
    }
  }
}
