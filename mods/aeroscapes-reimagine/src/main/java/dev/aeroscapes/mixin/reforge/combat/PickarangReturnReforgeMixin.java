package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.combat.InstalledWeaponReforges;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "org.violetmoon.quark.content.tools.entity.rang.AbstractPickarang", remap = false)
abstract class PickarangReturnReforgeMixin {
  @Shadow
  public abstract ItemStack getStack();

  @WrapOperation(
      method = "onHitEntity",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$rememberHostileHit(
      Entity target, DamageSource source, float amount, Operation<Boolean> original) {
    boolean damaged = original.call(target, source, amount);
    if (damaged && amount > 0.0F) {
      InstalledWeaponReforges.pickarangHit((Projectile) (Object) this, getStack(), target);
    }
    return damaged;
  }

  @ModifyArg(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/violetmoon/quark/content/tools/entity/rang/AbstractPickarang;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
              ordinal = 1))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Vec3 aeroscapes$returnSpeed(Vec3 original) {
    return InstalledWeaponReforges.pickarangReturn(
        (Projectile) (Object) this, getStack(), original);
  }
}
