package dev.aeroscapes.engineering;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiStackList.ComparisonHashStrategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.loading.LoadingModList;

/** Exercises the same output identity and comparison used by EMI's recipe lookup. */
public final class LadleAssemblyOutputsTest {
  private LadleAssemblyOutputsTest() {}

  public static void main(String[] arguments) {
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    var recipeId = ResourceLocation.parse("createmetallurgy:sequenced_assembly/ladle");
    var recipe = fixture();
    var holder = new RecipeHolder<>(recipeId, recipe);
    List<EmiStack> outputs = new ArrayList<>();
    outputs.add(EmiStack.of(recipe.getResultItem(null)));
    var before = index(outputs);
    require(before.size() == 1, "Native Create/JEMI exposes only the first weighted output");
    require(
        !before.containsKey(EmiStack.of(Items.IRON_HOE)),
        "Secondary result reproduces missing R recipe");
    var primary = outputs.getFirst();
    var weights = recipe.resultPool.stream().map(ProcessingOutput::getChance).toList();

    require(
        LadleAssemblyOutputs.addAlternates(holder, outputs), "Ladle assembly holder recognized");
    require(
        outputs.size() == 6 && outputs.getFirst() == primary,
        "All outcomes indexed; native primary retained");
    require(
        LadleAssemblyOutputs.addAlternates(holder, outputs) && outputs.size() == 6,
        "No duplicate outcomes");
    require(
        recipe.resultPool.stream().map(ProcessingOutput::getChance).toList().equals(weights),
        "Native outcome weights unchanged");
    var repaired = index(outputs);
    for (var result : recipe.resultPool) {
      verifyLookup(repaired, result.getStack());
      var decorated = result.getStack().copy();
      var data = new CompoundTag();
      data.putString("tiered", "aeroscapes_reimagine:ladle_legendary");
      data.putString("fluid", "createmetallurgy:molten_steel");
      data.putInt("amount", 4000);
      decorated.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
      verifyLookup(repaired, decorated);
    }
    List<EmiStack> ordinary = new ArrayList<>(List.of(primary));
    require(
        !LadleAssemblyOutputs.addAlternates(
                new RecipeHolder<>(ResourceLocation.parse("create:precision_mechanism"), recipe),
                ordinary)
            && ordinary.size() == 1,
        "Unrelated assembly junk is not indexed");
    require(
        !LadleAssemblyOutputs.addAlternates(new Object(), ordinary),
        "Other recipe types unchanged");
    require(!LadleAssemblyOutputs.addAlternates(recipe, ordinary), "Unwrapped recipe unchanged");
    System.out.println(
        "Ladle EMI outputs: six alternatives and component-bearing recipe lookups passed");
  }

  private static SequencedAssemblyRecipe fixture() {
    // Distinct vanilla items stand in for six style IDs; no modded client or renderer is started.
    Item[] styles = {
      Items.IRON_PICKAXE,
      Items.IRON_HOE,
      Items.IRON_AXE,
      Items.IRON_SHOVEL,
      Items.IRON_SWORD,
      Items.SHEARS
    };
    var recipe = new SequencedAssemblyRecipe(null);
    for (int index = 0; index < styles.length; index++) {
      recipe.resultPool.add(
          new ProcessingOutput(new ItemStack(styles[index]), index < 4 ? 249.75F : 0.5F));
    }
    return recipe;
  }

  private static Map<EmiStack, List<EmiStack>> index(List<EmiStack> outputs) {
    Map<EmiStack, List<EmiStack>> index =
        new Object2ObjectOpenCustomHashMap<>(new ComparisonHashStrategy());
    for (var output : outputs) index.put(output, outputs);
    return index;
  }

  private static void verifyLookup(Map<EmiStack, List<EmiStack>> index, ItemStack item) {
    var context = EmiStack.of(item);
    var recipe = index.get(context);
    require(
        recipe != null && recipe.stream().anyMatch(stack -> stack.isEqual(context)),
        "EMI output index and R-source pruning find " + item.getItem());
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
