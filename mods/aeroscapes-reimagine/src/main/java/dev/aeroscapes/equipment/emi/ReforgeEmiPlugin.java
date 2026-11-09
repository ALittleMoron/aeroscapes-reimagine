package dev.aeroscapes.equipment.emi;

import com.herobrot.tieredneo.api.TieredItemTags;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import dev.aeroscapes.equipment.ReforgeProfiles;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;

@EmiEntrypoint
public final class ReforgeEmiPlugin implements EmiPlugin {
  private static final String NAMESPACE = "aeroscapes_reimagine";

  @Override
  public void register(EmiRegistry registry) {
    if (!ModList.get().isLoaded("tieredneo")) return;
    var catalyst = EmiIngredient.of(TieredItemTags.REFORGE_ADDITION);
    if (catalyst.isEmpty()) return;

    var anvil = EmiStack.of(Blocks.ANVIL);
    var category =
        new EmiRecipeCategory(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "reforging"), anvil);
    registry.addCategory(category);
    registry.addWorkstation(category, anvil);
    registry.addWorkstation(category, EmiStack.of(Blocks.CHIPPED_ANVIL));
    registry.addWorkstation(category, EmiStack.of(Blocks.DAMAGED_ANVIL));

    for (Item item : BuiltInRegistries.ITEM) {
      if (!ReforgeProfiles.isEligible(item)) continue;
      var material = material(item);
      if (material.isEmpty()) continue;
      var id = BuiltInRegistries.ITEM.getKey(item);
      var recipeId =
          ResourceLocation.fromNamespaceAndPath(
              NAMESPACE, "/reforging/" + id.getNamespace() + "/" + id.getPath());
      registry.addRecipe(new ReforgeRecipe(category, recipeId, item, material, catalyst));
    }
  }

  private static EmiIngredient material(Item item) {
    var mapped = TieredNeoAPI.getReforgeBaseItems(item);
    if (!mapped.isEmpty()) return EmiIngredient.of(mapped.stream().map(EmiStack::of).toList());
    if (item instanceof TieredItem tool)
      return EmiIngredient.of(tool.getTier().getRepairIngredient());
    if (item instanceof ArmorItem armor) {
      return EmiIngredient.of(armor.getMaterial().value().repairIngredient().get());
    }
    return EmiIngredient.of(TieredItemTags.REFORGE_BASE_ITEM);
  }

  private static final class ReforgeRecipe extends BasicEmiRecipe {
    private ReforgeRecipe(
        EmiRecipeCategory category,
        ResourceLocation id,
        Item item,
        EmiIngredient material,
        EmiIngredient catalyst) {
      super(category, id, 112, 18);
      inputs = List.of(material, EmiStack.of(item), catalyst);
      outputs = List.of(EmiStack.of(item));
    }

    @Override
    public boolean supportsRecipeTree() {
      return false;
    }

    @Override
    public boolean hideCraftable() {
      return true;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
      for (int index = 0; index < inputs.size(); index++) {
        widgets.addSlot(inputs.get(index), index * 18, 0);
      }
      widgets.addTexture(EmiTexture.EMPTY_ARROW, 62, 1);
      widgets
          .addSlot(outputs.getFirst(), 94, 0)
          .recipeContext(this)
          .appendTooltip(
              Component.translatable("aeroscapes_reimagine.equipment.reforge.random_result"));
    }
  }
}
