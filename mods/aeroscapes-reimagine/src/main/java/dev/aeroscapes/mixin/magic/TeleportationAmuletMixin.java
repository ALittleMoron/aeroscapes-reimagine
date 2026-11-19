package dev.aeroscapes.mixin.magic;

import io.redspace.ironsspellbooks.item.curios.TeleportationAmuletItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

@Mixin(value = TeleportationAmuletItem.class, remap = false)
abstract class TeleportationAmuletMixin {
  // Iron's compares the necklace's local index against the flattened inventory before dropping it.
  @Redirect(
      method = "lambda$handleCurse$0",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/items/IItemHandlerModifiable;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$equippedStack(
      IItemHandlerModifiable combined,
      int index,
      SlotContext slot,
      ItemStack amulet,
      ICuriosItemHandler inventory) {
    return inventory
        .findCurio(slot.identifier(), index)
        .map(SlotResult::stack)
        .orElse(ItemStack.EMPTY);
  }
}
