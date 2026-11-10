package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yori3o.yo_hooks.common.entity.HookEntity;
import dev.aeroscapes.equipment.tools.ToolReforges;
import dev.aeroscapes.movement.HookReforgeBonuses;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = HookEntity.class, remap = false)
public abstract class HookReforgeWearMixin {
  @WrapOperation(
      method = "onHitBlock",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"),
      require = 1,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$attachmentWear(
      ItemStack stack,
      int amount,
      LivingEntity user,
      EquipmentSlot slot,
      Operation<Void> original) {
    int wear = ToolReforges.modifyWear(user, amount, HookReforgeBonuses.of(stack).wearSaving());
    original.call(stack, wear, user, slot);
  }
}
