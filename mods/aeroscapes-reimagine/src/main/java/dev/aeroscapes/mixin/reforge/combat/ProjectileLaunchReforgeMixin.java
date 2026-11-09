package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProjectileWeaponItem.class)
abstract class ProjectileLaunchReforgeMixin {
  @WrapOperation(
      method = "shoot",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$reforgedLaunch(
      ServerLevel level,
      Entity entity,
      Operation<Boolean> original,
      @Local(argsOnly = true) ItemStack weapon,
      @Local(index = 15) ItemStack ammo,
      @Local(argsOnly = true) boolean full) {
    RangedReforges.launch((Projectile) entity, weapon, ammo, full);
    return original.call(level, entity);
  }
}
