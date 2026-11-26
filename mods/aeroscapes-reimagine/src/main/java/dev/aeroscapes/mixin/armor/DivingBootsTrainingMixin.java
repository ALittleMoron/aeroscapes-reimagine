package dev.aeroscapes.mixin.armor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import dev.aeroscapes.equipment.armor.ArmorProficiency;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DivingBootsItem.class, remap = false)
abstract class DivingBootsTrainingMixin {
  @WrapOperation(
      method = "affects",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/simibubi/create/content/equipment/armor/DivingBootsItem;isWornBy(Lnet/minecraft/world/entity/Entity;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static boolean aeroscapes$divingFooting(Entity wearer, Operation<Boolean> original) {
    return original.call(wearer)
        && (!(wearer instanceof Player player)
            || !ArmorProficiency.undertrained(player, player.getItemBySlot(EquipmentSlot.FEET)));
  }
}
