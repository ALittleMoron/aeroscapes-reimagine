package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CraftingMenu.class)
abstract class ProfessionCraftingMixin {
  @ModifyExpressionValue(
      method = "slotChangedCraftingGrid",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static ItemStack aeroscapes$manualFood(
      ItemStack result,
      @Local(argsOnly = true) Player player,
      @Local(argsOnly = true) CraftingContainer grid) {
    if (!result.has(DataComponents.FOOD) && !ProfessionEffects.isFeast(result)) return result;
    var input = grid.asCraftInput();
    if (input.ingredientCount() == 1
        && input.items().stream().anyMatch(stack -> stack.getItem() instanceof BlockItem))
      return result;
    return ProfessionEffects.prepare(result, ProfessionEffects.mealTraits(player));
  }
}
