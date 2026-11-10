package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.field.CompanionReforges;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Wolf.class)
abstract class WolfArmorReforgeMixin {
  @WrapOperation(
      method = "actuallyHurt",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$wolfArmorWear(
      ItemStack armor,
      int wear,
      LivingEntity wearer,
      EquipmentSlot slot,
      Operation<Void> original,
      @Local(argsOnly = true) DamageSource source) {
    if (wearer instanceof Wolf wolf
        && !wolf.level().isClientSide()
        && source.getEntity() != null
        && wear > 0) {
      original.call(armor, CompanionReforges.wolfArmorWear(wolf, armor, wear), wolf, slot);
      CompanionReforges.readyGuard(wolf, armor);
    } else {
      original.call(armor, wear, wearer, slot);
    }
  }
}
