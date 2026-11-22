package dev.aeroscapes.ecology.client;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@EmiEntrypoint
public final class RibbitsEmiPlugin implements EmiPlugin {
  @Override
  public void register(EmiRegistry registry) {
    if (!BuiltInRegistries.ITEM.containsKey(
        ResourceLocation.fromNamespaceAndPath("ribbits", "maraca"))) return;
    source(registry, "maraca", "maraca");
    source(registry, "plants", "swamp_daisy", "giant_lilypad", "toadstool", "umbrella_leaf");
    source(
        registry, "village", "brown_toadstool", "red_toadstool", "toadstool_stem", "swamp_lantern");
  }

  private static void source(EmiRegistry registry, String group, String... items) {
    var stacks =
        Arrays.stream(items)
            .map(item -> ResourceLocation.fromNamespaceAndPath("ribbits", item))
            .map(BuiltInRegistries.ITEM::get)
            .<EmiIngredient>map(EmiStack::of)
            .toList();
    registry.addRecipe(
        new EmiInfoRecipe(
            stacks,
            List.of(Component.translatable("aeroscapes_reimagine.emi.ribbits." + group)),
            ResourceLocation.fromNamespaceAndPath(
                "aeroscapes_reimagine", "/info/ribbits/" + group)));
  }
}
