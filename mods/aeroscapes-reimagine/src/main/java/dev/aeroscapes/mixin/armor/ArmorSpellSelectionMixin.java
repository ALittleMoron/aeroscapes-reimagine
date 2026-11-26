package dev.aeroscapes.mixin.armor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.armor.ArmorProficiency;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.api.magic.SpellSelectionManager", remap = false)
abstract class ArmorSpellSelectionMixin {
  @WrapOperation(
      method = "init(Lnet/minecraft/world/entity/player/Player;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Player;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"),
      require = 6,
      allow = 6)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$trainedArmorSpells(
      Player player, EquipmentSlot slot, Operation<ItemStack> original) {
    var stack = original.call(player, slot);
    return ArmorProficiency.SLOTS.contains(slot) && ArmorProficiency.undertrained(player, stack)
        ? ItemStack.EMPTY
        : stack;
  }
}
