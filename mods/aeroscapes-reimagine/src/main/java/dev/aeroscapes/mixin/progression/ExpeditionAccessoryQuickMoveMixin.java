package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.expedition.ExceptionalAccessory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerMenu.class)
abstract class ExpeditionAccessoryQuickMoveMixin {
  @WrapOperation(
      method = "moveItemStackTo",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$transferCraft(
      Slot destination,
      ItemStack result,
      Operation<Void> original,
      @Local(argsOnly = true) ItemStack source) {
    var menu = (AbstractContainerMenu) (Object) this;
    // Quick-move splits into the destination before the result slot receives onTake.
    if (ExceptionalAccessory.canMarkCraft(result)) {
      for (var slot : menu.slots) {
        if (!(slot instanceof ResultSlot output) || output.getItem() != source) continue;
        ExceptionalAccessory.completeCraft(
            result, ((CraftingResultPlayerAccess) output).aeroscapes$craftingPlayer());
        break;
      }
    }
    original.call(destination, result);
  }
}
