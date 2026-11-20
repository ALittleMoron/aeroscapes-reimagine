package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.races.ProfessionPot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;

@Mixin(RecipeBookMenu.class)
abstract class ProfessionPotRecipeBookMixin {
  @WrapMethod(method = "handlePlacement")
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$recipeBookIngredients(
      boolean all, RecipeHolder<?> recipe, ServerPlayer cook, Operation<Void> original) {
    if ((Object) this instanceof CookingPotMenu pot && !(cook instanceof FakePlayer)) {
      var ingredients = new ProfessionPot(pot.blockEntity);
      original.call(all, recipe, cook);
      ingredients.afterClick(pot.blockEntity, cook, -1);
    } else original.call(all, recipe, cook);
  }
}
