package dev.aeroscapes.mixin.workshop;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.workshop.WorkshopManufacture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CraftingMenu.class)
abstract class WorkshopCraftingMixin {
  @ModifyExpressionValue(
      method = "slotChangedCraftingGrid",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static ItemStack aeroscapes$requireTraining(
      ItemStack result,
      @Local(argsOnly = true) Player player,
      @Local(argsOnly = true) CraftingContainer grid) {
    if (WorkshopManufacture.allowed(player, result, false)
        || grid.getItems().stream().anyMatch(input -> input.is(result.getItem()))) return result;
    WorkshopManufacture.explain(player);
    return ItemStack.EMPTY;
  }
}
