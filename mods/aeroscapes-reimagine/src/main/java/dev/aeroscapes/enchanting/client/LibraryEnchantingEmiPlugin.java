package dev.aeroscapes.enchanting.client;

import dev.aeroscapes.enchanting.LibraryBooks;
import dev.aeroscapes.equipment.emi.ReforgeEmiReload;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import me.alfie.alfinolib.datapacks.client.ClientDatapackUpdatedEvent;
import me.alfie.immersiveenchanting.datacomponent.ModDataComponents;
import me.alfie.immersiveenchanting.datacomponent.ReplicatedDataComponent;
import me.alfie.immersiveenchanting.datapack.enchantment_cost.CostRegistry;
import me.alfie.immersiveenchanting.datapack.enchantment_cost.codec.Cost;
import me.alfie.immersiveenchanting.datapack.enchantment_cost.codec.CostHolder;
import me.alfie.immersiveenchanting.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

@EmiEntrypoint
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class LibraryEnchantingEmiPlugin implements EmiPlugin {
  private static final String TEXT = "aeroscapes_reimagine.enchanting.emi.";
  private static final int TEXT_COLOR = 0x404040;

  @SubscribeEvent
  public static void updateCosts(ClientDatapackUpdatedEvent event) {
    if (ModList.get().isLoaded("emi")) ReforgeEmiReload.invalidate();
  }

  @Override
  public void register(EmiRegistry registry) {
    if (!ModList.get().isLoaded("immersiveenchanting")) return;
    var level = Minecraft.getInstance().level;
    CostRegistry costs = CostRegistry.client();
    if (level == null || costs == null) return;
    var workstation = EmiStack.of(Items.ENCHANTING_TABLE);
    var category = new EmiRecipeCategory(id("library_enchanting"), workstation);
    registry.addCategory(category);
    registry.addWorkstation(category, workstation);
    registry.addWorkstation(category, EmiStack.of(ModItems.ANCIENT_BOOK.get()));

    var fuels = costs.get(CostRegistry.ENCHANTING_FUELS).levelCosts();
    for (var effect : costs.getAllEnabledEnchantmentHolders()) {
      if (!LibraryBooks.playerEffect(effect)) continue;
      ItemStack example = exampleEquipment(effect);
      if (example.isEmpty()) continue;
      var prices = costs.get(effect).levelCosts();
      String effectId = effect.getRegisteredName().replace(':', '/');
      for (int target = 1; target <= effect.value().getMaxLevel(); target++) {
        ItemStack input = example.copy();
        ItemStack result = example.copy();
        if (target > 1) input.enchant(effect, target - 1);
        result.enchant(effect, target);
        var knowledge = EmiStack.of(LibraryBooks.ancient(effect, target));
        int rank = LibraryBooks.requiredMastery(effect, target);
        Component requirement =
            Component.translatable(TEXT + (rank == 0 ? "library" : "requirements"), target, rank);
        var display = new Example(input, result, knowledge, requirement, true);
        addCosts(
            registry,
            category,
            effectId + "/" + target,
            display,
            prices.getLevel(target),
            fuels.getLevel(target));
      }
    }

    var copying = costs.get(CostRegistry.REPLICATE);
    if (copying.enabled()) {
      var sharpness =
          level
              .registryAccess()
              .registryOrThrow(Registries.ENCHANTMENT)
              .getHolderOrThrow(Enchantments.SHARPNESS);
      ItemStack input = LibraryBooks.ancient(sharpness, 1);
      ItemStack result = input.copy();
      result.set(ModDataComponents.REPLICATED.get(), new ReplicatedDataComponent(true));
      var display =
          new Example(input, result, EmiStack.EMPTY, Component.translatable(TEXT + "copy"), false);
      addCosts(
          registry, category, "copy", display, copying.levelCosts().getLevel(1), fuels.getLevel(1));
    }
  }

  private static ItemStack exampleEquipment(Holder<Enchantment> effect) {
    return effect.value().definition().supportedItems().stream()
        .map(item -> new ItemStack(item.value()))
        .filter(
            stack ->
                !stack.is(Items.BOOK)
                    && !stack.is(Items.ENCHANTED_BOOK)
                    && !LibraryBooks.isAncient(stack)
                    && stack.supportsEnchantment(effect))
        .findFirst()
        .orElse(ItemStack.EMPTY);
  }

  private static void addCosts(
      EmiRegistry registry,
      EmiRecipeCategory category,
      String path,
      Example display,
      CostHolder prices,
      CostHolder fuels) {
    int alternative = 0;
    for (Cost price : prices.costs()) {
      for (Cost fuel : fuels.costs()) {
        registry.addRecipe(
            new CostRecipe(
                category,
                id("/library_enchanting/" + path + "/" + alternative++),
                display,
                price,
                fuel));
      }
    }
  }

  private static EmiIngredient ingredient(Cost cost) {
    return EmiIngredient.of(
        cost.getItemStacks().stream().filter(stack -> !stack.isEmpty()).map(EmiStack::of).toList(),
        cost.itemCost().count());
  }

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", path);
  }

  private record Example(
      ItemStack input,
      ItemStack result,
      EmiStack knowledge,
      Component requirement,
      boolean discountable) {}

  private static final class CostRecipe extends BasicEmiRecipe {
    private final Example display;
    private final EmiIngredient materials;
    private final EmiIngredient fuels;
    private final int levels;

    private CostRecipe(
        EmiRecipeCategory category, ResourceLocation id, Example display, Cost price, Cost fuel) {
      super(category, id, 146, 64);
      this.display = display;
      this.materials = ingredient(price);
      this.fuels = ingredient(fuel);
      this.levels = price.xpLevels() + fuel.xpLevels();
      var input = EmiStack.of(display.input());
      if (!display.discountable()) input.setRemainder(input.copy());
      inputs = List.of(input, materials, fuels);
      catalysts = display.knowledge().isEmpty() ? List.of() : List.of(display.knowledge());
      outputs = List.of(EmiStack.of(display.result()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
      widgets.addSlot(inputs.getFirst(), 0, 0);
      widgets.addSlot(materials, 20, 0);
      widgets.addSlot(fuels, 40, 0);
      if (!display.knowledge().isEmpty())
        widgets.addSlot(display.knowledge(), 60, 0).catalyst(true);
      widgets.addTexture(EmiTexture.EMPTY_ARROW, 84, 1);
      widgets.addSlot(outputs.getFirst(), 114, 0).recipeContext(this);
      widgets.addText(display.requirement(), 0, 23, TEXT_COLOR, false);
      widgets.addText(
          Component.translatable(TEXT + (display.discountable() ? "base_xp" : "xp"), levels),
          0,
          34,
          TEXT_COLOR,
          false);
      widgets.addText(Component.translatable(TEXT + "example"), 0, 45, TEXT_COLOR, false);
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
