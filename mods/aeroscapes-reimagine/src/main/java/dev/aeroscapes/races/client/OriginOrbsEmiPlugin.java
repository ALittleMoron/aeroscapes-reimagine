package dev.aeroscapes.races.client;

import dev.aeroscapes.races.OriginOrbs;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;

@EmiEntrypoint
public final class OriginOrbsEmiPlugin implements EmiPlugin {
  @Override
  public void register(EmiRegistry registry) {
    registry.removeRecipes(
        recipe ->
            recipe.getCategory() == VanillaEmiRecipeCategories.INFO
                && recipe.getOutputs().stream()
                    .anyMatch(stack -> stack.getItemStack().is(OriginOrbs.HIDDEN)));
  }
}
