package dev.aeroscapes.mixin.armor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.equipment.armor.DivingHelmetItem;
import dev.aeroscapes.equipment.armor.ArmorProficiency;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DivingHelmetItem.class, remap = false)
abstract class DivingHelmetTrainingMixin {
  @WrapOperation(
      method = "breatheUnderwater",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/simibubi/create/content/equipment/armor/DivingHelmetItem;getWornItem(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static ItemStack aeroscapes$divingBreathing(
      Entity wearer, Operation<ItemStack> original) {
    var stack = original.call(wearer);
    return wearer instanceof Player player && ArmorProficiency.undertrained(player, stack)
        ? ItemStack.EMPTY
        : stack;
  }
}
