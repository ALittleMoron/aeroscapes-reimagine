package dev.aeroscapes.mixin.workshop.tools;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.ToolReforges;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.heateditem.HeatedItem", remap = false)
abstract class WorkshopTongsReforgeMixin {
  @WrapOperation(
      method = "tickInventory",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"),
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$applyTongsWear(
      ItemStack tongs,
      int amount,
      LivingEntity user,
      EquipmentSlot slot,
      Operation<Void> original) {
    int saving =
        switch (ReforgeProfiles.path(tongs)) {
          case "smithing_tongs_poor" -> -10;
          case "smithing_tongs_fine" -> 10;
          case "smithing_tongs_exceptional" -> 20;
          case "smithing_tongs_epic" -> 30;
          case "smithing_tongs_legendary" -> 40;
          default -> 0;
        };
    original.call(tongs, ToolReforges.modifyWear(user, amount, saving), user, slot);
  }
}
