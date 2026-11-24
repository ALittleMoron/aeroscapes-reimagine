package dev.aeroscapes.mixin.workshop;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.equipment.workshop.WorkshopManufacture;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SmithingMenu.class)
abstract class WorkshopUpgradeMixin extends ItemCombinerMenu {
  protected WorkshopUpgradeMixin(
      MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
    super(type, id, inventory, access);
  }

  @ModifyExpressionValue(
      method = "createResult",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/crafting/SmithingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$requireUpgradeTraining(ItemStack result) {
    if (result.is(inputSlots.getItem(SmithingMenu.BASE_SLOT).getItem())
        || WorkshopManufacture.allowed(player, result, false)) return result;
    WorkshopManufacture.explain(player);
    return ItemStack.EMPTY;
  }
}
