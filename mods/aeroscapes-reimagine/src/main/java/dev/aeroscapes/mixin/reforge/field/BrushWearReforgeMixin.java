package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.field.FieldReforges;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BrushItem.class)
abstract class BrushWearReforgeMixin {
  @WrapOperation(
      method = "onUseTick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$brushWear(
      ItemStack brush,
      int amount,
      LivingEntity user,
      EquipmentSlot slot,
      Operation<Void> original) {
    original.call(brush, FieldReforges.brushWear(brush, user, amount), user, slot);
  }
}
