package dev.aeroscapes.mixin.technique;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.combat.MartialArchery;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProjectileWeaponItem.class)
abstract class MartialArrowLaunchMixin {
  @WrapOperation(
      method = "shoot",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$martialArrow(
      ServerLevel level,
      Entity projectile,
      Operation<Boolean> original,
      @Local(argsOnly = true) ItemStack weapon,
      @Local(index = 15) ItemStack ammo) {
    boolean launched = original.call(level, projectile);
    if (launched) MartialArchery.launched(projectile, weapon, ammo);
    return launched;
  }
}
