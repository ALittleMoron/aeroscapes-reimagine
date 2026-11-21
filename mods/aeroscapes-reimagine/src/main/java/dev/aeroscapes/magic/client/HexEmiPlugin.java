package dev.aeroscapes.magic.client;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;

@EmiEntrypoint
public final class HexEmiPlugin implements EmiPlugin {
  @Override
  public void register(EmiRegistry registry) {
    if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("hexcasting:charged_amethyst")))
      return;
    registerInfo(
        registry,
        "getting_started",
        List.of(
            EmiStack.of(Items.AMETHYST_CLUSTER), item("amethyst_dust"), item("charged_amethyst")),
        List.of("cluster", "book", "staff"));
    var staves = TagKey.create(Registries.ITEM, ResourceLocation.parse("hexcasting:staves"));
    registerInfo(
        registry, "first_light", List.of(EmiIngredient.of(staves)), List.of("light", "patterns"));
    registerInfo(
        registry,
        "prepared_programs",
        List.of(item("cypher"), item("trinket"), item("artifact")),
        List.of("write", "prepare", "charge"));
  }

  private static EmiStack item(String path) {
    return EmiStack.of(
        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hexcasting", path)));
  }

  private static void registerInfo(
      EmiRegistry registry, String path, List<EmiIngredient> items, List<String> paragraphs) {
    registry.addRecipe(
        new EmiInfoRecipe(
            items,
            paragraphs.stream()
                .<Component>map(
                    key -> Component.translatable("aeroscapes_reimagine.hex.info." + key))
                .toList(),
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "/info/hex/" + path)));
  }
}
