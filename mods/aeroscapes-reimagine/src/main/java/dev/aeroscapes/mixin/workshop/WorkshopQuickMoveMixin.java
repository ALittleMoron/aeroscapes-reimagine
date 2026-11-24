package dev.aeroscapes.mixin.workshop;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.workshop.WorkshopManufacture;
import dev.aeroscapes.equipment.workshop.WorkshopQuality;
import dev.aeroscapes.mixin.progression.CraftingResultPlayerAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerMenu.class)
abstract class WorkshopQuickMoveMixin {
  @WrapOperation(
      method = "moveItemStackTo",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$finishQuickMovedCraft(
      Slot destination,
      ItemStack result,
      Operation<Void> original,
      @Local(argsOnly = true) ItemStack source) {
    if (WorkshopManufacture.finishedEquipment(result)) {
      var menu = (AbstractContainerMenu) (Object) this;
      for (var slot : menu.slots) {
        if (!(slot instanceof ResultSlot output) || output.getItem() != source) continue;
        var inputs = ((CraftingResultInputAccess) output).aeroscapes$craftInput().getItems();
        if (inputs.stream().noneMatch(input -> input.is(result.getItem())))
          WorkshopQuality.finishQuickMove(
              ((CraftingResultPlayerAccess) output).aeroscapes$craftingPlayer(), result);
        break;
      }
    }
    original.call(destination, result);
  }
}
