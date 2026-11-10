package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.combat.CrossbowFollowup;
import io.redspace.ironsspellbooks.item.weapons.AutoloaderCrossbow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AutoloaderCrossbow.class)
abstract class AutoloaderChargeReforgeMixin {
  @WrapOperation(
      method = "handleTicking",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/item/weapons/AutoloaderCrossbow;tryLoadProjectiles(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static boolean aeroscapes$consumeFollowup(
      LivingEntity user, ItemStack weapon, Operation<Boolean> original) {
    boolean loaded = original.call(user, weapon);
    if (loaded) CrossbowFollowup.onAutoChargeCompleted(user, weapon);
    return loaded;
  }
}
