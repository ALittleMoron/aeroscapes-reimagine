package dev.aeroscapes.equipment.emi;

import dev.aeroscapes.equipment.armor.SpecialistArmor;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@EmiEntrypoint
public final class SpecialistArmorEmiPlugin implements EmiPlugin {
  @Override
  public void register(EmiRegistry registry) {
    for (var set : SpecialistArmor.Set.values()) {
      registry.addRecipe(
          new EmiInfoRecipe(
              set.items().stream().<EmiIngredient>map(EmiStack::of).toList(),
              List.of(
                  Component.translatable(
                      "tooltip.aeroscapes_reimagine.armor.specialist_info", set.trainingName()),
                  set.setEffect()),
              ResourceLocation.fromNamespaceAndPath(
                  "aeroscapes_reimagine", "/info/" + set.id() + "_armor")));
    }
  }
}
