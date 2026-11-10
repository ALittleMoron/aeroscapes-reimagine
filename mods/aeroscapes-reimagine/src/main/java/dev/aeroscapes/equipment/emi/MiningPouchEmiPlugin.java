package dev.aeroscapes.equipment.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@EmiEntrypoint
public final class MiningPouchEmiPlugin implements EmiPlugin {
  @Override
  public void register(EmiRegistry registry) {
    var id = ResourceLocation.fromNamespaceAndPath("galosphere", "mining_pouch");
    if (!BuiltInRegistries.ITEM.containsKey(id)) return;
    String key = "aeroscapes_reimagine.equipment.mining_pouch.";
    registry.addRecipe(
        new EmiInfoRecipe(
            List.of(EmiStack.of(BuiltInRegistries.ITEM.get(id))),
            List.of(
                Component.translatable(key + "find"),
                Component.translatable(key + "fill"),
                Component.translatable(key + "return")),
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "/info/mining_pouch")));
  }
}
