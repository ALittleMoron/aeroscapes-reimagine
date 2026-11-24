package dev.aeroscapes.enchanting.client;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiStackList.ComparisonHashStrategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.fml.loading.LoadingModList;

public final class CurseCleansingEmiTest {
  private CurseCleansingEmiTest() {}

  public static void main(String[] arguments) {
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    var curse = enchantment("fixture_curse");
    List<EmiRecipe> recipes = new ArrayList<>();
    CurseCleansingEmiRecipes.addRecipes(
        recipes::add, List.of(curse), enchantment("fixture_mending"), Items.AMETHYST_SHARD);

    Map<EmiStack, List<EmiRecipe>> uses =
        new Object2ObjectOpenCustomHashMap<>(new ComparisonHashStrategy());
    for (var recipe : recipes) {
      require(
          recipe.getCategory() == VanillaEmiRecipeCategories.GRINDING,
          "Cleansing is discoverable alongside other grindstone uses");
      require(
          recipe.getOutputs().isEmpty() && !recipe.supportsRecipeTree() && recipe.hideCraftable(),
          "The illustrated outcome is not a crafting or item acquisition route");
      for (var input : recipe.getInputs()) {
        for (var stack : input.getEmiStacks()) {
          uses.computeIfAbsent(stack.copy(), ignored -> new ArrayList<>()).add(recipe);
        }
      }
    }
    var sword = Items.IRON_SWORD.getDefaultInstance();
    sword.enchant(curse, 1);
    sword.set(DataComponents.CUSTOM_NAME, Component.literal("Cleansing witness"));
    var swordUses = uses.get(EmiStack.of(sword));
    require(
        swordUses != null && swordUses.size() == 1,
        "U on an enchanted, named sword shows its cleansing card");
    var runeUses = uses.get(EmiStack.of(Items.AMETHYST_SHARD));
    require(
        runeUses != null && runeUses.contains(swordUses.getFirst()),
        "U on the reagent shows the same card");
    require(
        uses.containsKey(EmiStack.of(Items.IRON_PICKAXE))
            && uses.containsKey(EmiStack.of(Items.IRON_BOOTS))
            && uses.containsKey(EmiStack.of(Items.ENCHANTED_BOOK))
            && !uses.containsKey(EmiStack.of(Items.STICK)),
        "The recipe covers compatible tools, armor and books");
    System.out.println("EMI curse cleansing uses by equipment and rune passed");
  }

  private static Holder<Enchantment> enchantment(String name) {
    var items =
        HolderSet.direct(
            Items.IRON_SWORD.getDefaultInstance().getItemHolder(),
            Items.IRON_PICKAXE.getDefaultInstance().getItemHolder(),
            Items.IRON_BOOTS.getDefaultInstance().getItemHolder());
    return Holder.direct(
        Enchantment.enchantment(
                Enchantment.definition(
                    items,
                    1,
                    1,
                    Enchantment.constantCost(1),
                    Enchantment.constantCost(10),
                    1,
                    EquipmentSlotGroup.ANY))
            .build(ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", name)));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
