package dev.aeroscapes.races.client;

import dev.aeroscapes.races.ChangeItems;
import dev.aeroscapes.races.OriginOrbs;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

@EmiEntrypoint
public final class OriginOrbsEmiPlugin implements EmiPlugin {
  @Override
  public void register(EmiRegistry registry) {
    registry.removeRecipes(
        recipe ->
            recipe.getCategory() == VanillaEmiRecipeCategories.INFO
                && recipe.getOutputs().stream()
                    .anyMatch(stack -> stack.getItemStack().is(OriginOrbs.HIDDEN)));
    addChangeInfo(registry, ChangeItems.IDENTITY_SEAL.get(), "identity_seal");
    addChangeInfo(registry, ChangeItems.KNOTTED_THREAD.get(), "knotted_thread");
  }

  private static void addChangeInfo(EmiRegistry registry, Item item, String name) {
    String key = item.getDescriptionId();
    registry.addRecipe(
        new EmiInfoRecipe(
            List.of(EmiStack.of(item)),
            List.of(Component.translatable(key + ".use"), Component.translatable(key + ".info")),
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "/info/" + name)));
  }
}
