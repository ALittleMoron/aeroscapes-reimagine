package dev.aeroscapes.enchanting.client;

import dev.aeroscapes.enchanting.CurseCleansing;
import dev.aeroscapes.enchanting.EquipmentCurses;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

final class CurseCleansingEmiRecipes {
  private CurseCleansingEmiRecipes() {}

  static void register(EmiRegistry registry, RegistryAccess access) {
    Item rune = BuiltInRegistries.ITEM.get(CurseCleansing.HOLY_RUNE);
    if (rune == Items.AIR) return;
    var enchantments = access.registryOrThrow(Registries.ENCHANTMENT);
    var curses =
        enchantments.getTag(EnchantmentTags.CURSE).stream()
            .flatMap(tag -> tag.stream())
            .sorted(Comparator.comparing(effect -> !effect.is(EquipmentCurses.SOURCED)))
            .toList();
    addRecipes(
        registry::addRecipe, curses, enchantments.getHolderOrThrow(Enchantments.MENDING), rune);
  }

  static void addRecipes(
      Consumer<EmiRecipe> recipes,
      List<Holder<Enchantment>> curses,
      Holder<Enchantment> mending,
      Item rune) {
    if (curses.isEmpty()) return;
    var examples = new LinkedHashMap<Item, Holder<Enchantment>>();
    for (var curse : curses) {
      for (var item : curse.value().definition().supportedItems()) {
        if (item.value().getDefaultInstance().supportsEnchantment(curse)) {
          examples.putIfAbsent(item.value(), curse);
        }
      }
    }
    examples.put(Items.ENCHANTED_BOOK, curses.getFirst());
    examples.forEach(
        (item, curse) -> recipes.accept(new CleansingRecipe(item, rune, curse, mending)));
  }

  private static final class CleansingRecipe extends BasicEmiRecipe {
    private final EmiStack cursed;
    private final EmiStack cleansed;

    private CleansingRecipe(
        Item item, Item rune, Holder<Enchantment> curse, Holder<Enchantment> mending) {
      super(VanillaEmiRecipeCategories.GRINDING, id(item), 116, 56);
      inputs = List.of(EmiStack.of(item), EmiStack.of(rune));
      var example = item.getDefaultInstance();
      if ((item == Items.ENCHANTED_BOOK || example.supportsEnchantment(mending))
          && Enchantment.areCompatible(curse, mending)) example.enchant(mending, 1);
      cleansed = EmiStack.of(example);
      var before = example.copy();
      before.enchant(curse, 1);
      cursed = EmiStack.of(before);
    }

    private static ResourceLocation id(Item item) {
      var itemId = BuiltInRegistries.ITEM.getKey(item);
      return ResourceLocation.fromNamespaceAndPath(
          "aeroscapes_reimagine",
          "/curse_cleansing/" + itemId.getNamespace() + "/" + itemId.getPath());
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
      widgets.addText(
          Component.translatable("aeroscapes_reimagine.enchanting.emi.curse_cleansing"),
          5,
          2,
          0xFFFFFF,
          true);
      widgets.addSlot(cursed, 18, 15);
      widgets.addSlot(inputs.get(1), 18, 35);
      widgets.addTexture(EmiTexture.EMPTY_ARROW, 53, 26);
      widgets.addSlot(cleansed, 92, 25);
    }

    @Override
    public boolean supportsRecipeTree() {
      return false;
    }

    @Override
    public boolean hideCraftable() {
      return true;
    }
  }
}
