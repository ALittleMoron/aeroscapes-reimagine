package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.combat.CrossbowFollowup;
import dev.aeroscapes.equipment.combat.RangedProficiency;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(CrossbowItem.class)
abstract class CrossbowChargeReforgeMixin {
  @ModifyConstant(method = "getChargeDuration", constant = @Constant(floatValue = 1.25F))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static float aeroscapes$baseChargeTime(float time, ItemStack stack, LivingEntity user) {
    return time * RangedProficiency.drawTime(stack, user);
  }

  @ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static int aeroscapes$chargeDuration(int original, ItemStack stack, LivingEntity user) {
    return CrossbowFollowup.chargeDuration(stack, user, original);
  }

  @WrapOperation(
      method = "releaseUsing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/CrossbowItem;tryLoadProjectiles(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$consumeFollowup(
      LivingEntity user, ItemStack weapon, Operation<Boolean> original) {
    boolean loaded = original.call(user, weapon);
    if (loaded) {
      CrossbowFollowup.onChargeCompleted(user, weapon);
    }
    return loaded;
  }
}
