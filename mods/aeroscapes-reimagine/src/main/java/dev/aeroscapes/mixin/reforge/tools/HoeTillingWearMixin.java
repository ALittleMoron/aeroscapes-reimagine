package dev.aeroscapes.mixin.reforge.tools;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.tools.ToolReforges;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HoeItem.class)
abstract class HoeTillingWearMixin {
  @WrapOperation(
      method = "useOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$tillingWear(
      ItemStack stack,
      int amount,
      LivingEntity user,
      EquipmentSlot slot,
      Operation<Void> original) {
    original.call(stack, ToolReforges.tillingWear(stack, user, amount), user, slot);
  }
}
