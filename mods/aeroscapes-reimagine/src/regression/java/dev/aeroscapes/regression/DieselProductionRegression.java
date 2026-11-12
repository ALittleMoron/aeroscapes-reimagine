package dev.aeroscapes.regression;

import com.jesz.createdieselgenerators.CDGRegistries;
import com.jesz.createdieselgenerators.fuel_type.FuelType;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler.GroupedItems;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.fluids.FluidStack;

/** Loaded survival recipes, renewable fuel production and native engine fuel registry contracts. */
public final class DieselProductionRegression {
  private DieselProductionRegression() {}

  public static void verify(ServerLevel level) {
    sprayer(level);
    machineRecipes(level);
    renewableFuel(level);
    refining(level);
    engineFuels(level);
  }

  private static void sprayer(ServerLevel level) {
    // The distributed artifact spells its recipe path "chemcial", while source spells "chemical".
    var loaded = recipe(level, "mechanical_crafting/chemcial_sprayer");
    require(loaded instanceof MechanicalCraftingRecipe, "Native sprayer uses mechanical crafting");
    var mechanical = (MechanicalCraftingRecipe) loaded;
    var input =
        mechanicalInput(
            level,
            4,
            2,
            List.of(
                stack("createdieselgenerators:kelp_handle"),
                stack("createdieselgenerators:kelp_handle"),
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                stack("create:precision_mechanism"),
                stack("minecraft:copper_block"),
                stack("minecraft:dried_kelp"),
                stack("create:fluid_pipe")));
    require(mechanical.matches(input, level), "Native HH__/MCKP sprayer grid matches");
    result(mechanical.assemble(input, level.registryAccess()), "chemical_sprayer", 1);
    long routes =
        level.getRecipeManager().getRecipes().stream()
            .filter(
                holder ->
                    holder
                        .value()
                        .getResultItem(level.registryAccess())
                        .is(stack("createdieselgenerators:chemical_sprayer").getItem()))
            .count();
    require(routes == 1, "Sprayer has one loaded production route without a duplicate repair");
    var upgrade = processing(level, "deploying/chemical_sprayer_lighter");
    require(
        upgrade.getIngredients().size() == 2
            && upgrade
                .getIngredients()
                .get(0)
                .test(stack("createdieselgenerators:chemical_sprayer"))
            && upgrade.getIngredients().get(1).test(stack("createdieselgenerators:lighter")),
        "Native lighter upgrade consumes one sprayer and one lighter");
    require(upgrade.getRollableResults().size() == 1, "One native upgraded sprayer result");
    result(upgrade.getRollableResults().getFirst().getStack(), "chemical_sprayer_lighter", 1);
  }

  private static void machineRecipes(ServerLevel level) {
    for (String path :
        List.of(
            "diesel_engine",
            "large_diesel_engine",
            "huge_diesel_engine",
            "engine_piston",
            "engine_turbocharger",
            "engine_silencer",
            "bulk_fermenter",
            "basin_lid",
            "pumpjack_hole",
            "pumpjack_head",
            "pumpjack_bearing",
            "distillation_controller",
            "lighter",
            "kelp_handle",
            "chemical_turret",
            "chemical_turret_lighter")) {
      var loaded = recipe(level, "crafting/" + path);
      require(loaded instanceof ShapedRecipe, "Native shaped machine recipe: " + path);
      var shaped = (ShapedRecipe) loaded;
      var input = CraftingInput.of(shaped.getWidth(), shaped.getHeight(), samples(shaped));
      require(shaped.matches(input, level), "Loaded native machine ingredients match: " + path);
      var output = shaped.assemble(input, level.registryAccess());
      require(!output.isEmpty(), "Native machine can be assembled: " + path);
      String item = path.equals("chemical_turret_lighter") ? "chemical_turret" : path;
      int count = path.equals("engine_piston") ? 2 : path.equals("distillation_controller") ? 4 : 1;
      result(output, item, count);
    }
    var loaded = recipe(level, "mechanical_crafting/pumpjack_crank");
    require(
        loaded instanceof MechanicalCraftingRecipe,
        "Pumpjack crank has a native mechanical recipe");
    var mechanical = (MechanicalCraftingRecipe) loaded;
    var input =
        mechanicalInput(level, mechanical.getWidth(), mechanical.getHeight(), samples(mechanical));
    require(
        mechanical.matches(input, level),
        "Native pumpjack crank grid matches registered ingredients");
    result(mechanical.assemble(input, level.registryAccess()), "pumpjack_crank", 1);
  }

  private static List<ItemStack> samples(ShapedRecipe recipe) {
    var samples = new ArrayList<ItemStack>();
    for (var ingredient : recipe.getIngredients()) {
      if (ingredient == Ingredient.EMPTY) {
        samples.add(ItemStack.EMPTY);
      } else {
        var choices = ingredient.getItems();
        require(
            choices.length > 0,
            "Native machine ingredient/tag has an obtainable registered member");
        samples.add(choices[0].copy());
      }
    }
    return samples;
  }

  private static void renewableFuel(ServerLevel level) {
    require(
        stack("minecraft:wheat_seeds").is(TagKey.create(Registries.ITEM, id("c:seeds"))),
        "Survival wheat seeds populate the loaded seed tag");
    require(
        stack("minecraft:potato")
            .is(TagKey.create(Registries.ITEM, id("createdieselgenerators:fermentable"))),
        "Farmable potatoes populate the loaded fermentable tag");
    var plant = processing(level, "compacting/plant_oil");
    require(
        plant.getIngredients().size() == 1
            && plant.getIngredients().getFirst().test(stack("minecraft:wheat_seeds")),
        "One native wheat-seed pressing input");
    fluidOutputs(plant, List.of("plant_oil"), 100);
    for (String type : List.of("basin_fermenting", "bulk_fermenting")) {
      var ethanol = processing(level, type + "/fermentable");
      require(
          ethanol.getIngredients().size() == 2
              && ethanol.getIngredients().get(0).test(stack("minecraft:potato"))
              && ethanol.getIngredients().get(1).test(stack("minecraft:bone_meal")),
          "Native fermentation accepts one farmable potato and one bone meal");
      require(ethanol.getProcessingDuration() == 400, "Native fermentation duration is 400 ticks");
      fluidOutputs(ethanol, List.of("ethanol"), type.equals("basin_fermenting") ? 200 : 400);
    }
    var biodiesel = processing(level, "mixing/biodiesel");
    require(biodiesel.getFluidIngredients().size() == 2, "Biodiesel uses precisely two fluids");
    fluidInput(biodiesel, 0, "plant_oil", 100);
    fluidInput(biodiesel, 1, "ethanol", 100);
    fluidOutputs(biodiesel, List.of("biodiesel"), 200);
  }

  private static void refining(ServerLevel level) {
    for (boolean superheated : List.of(false, true)) {
      var recipe =
          processing(level, "distillation/" + (superheated ? "superheated_" : "") + "crude_oil");
      require(
          recipe.getFluidIngredients().size() == 1 && recipe.getIngredients().isEmpty(),
          "Distillation takes only one crude-oil input");
      fluidInput(recipe, 0, "crude_oil", 100);
      fluidOutputs(recipe, List.of("diesel", "gasoline"), superheated ? 75 : 50);
      require(
          recipe.getProcessingDuration() == (superheated ? 40 : 60), "Native refining duration");
      require(
          recipe.getRequiredHeat()
              == (superheated ? HeatCondition.SUPERHEATED : HeatCondition.HEATED),
          "Native refining heat requirement remains distinct");
    }
  }

  private static void engineFuels(ServerLevel level) {
    String[] names = {"plant_oil", "ethanol", "biodiesel", "gasoline", "diesel"};
    float[][] strengths = {
      {2048, 2560, 4096},
      {2048, 3072, 6144},
      {4096, 6144, 12288},
      {4096, 6144, 12288},
      {6144, 8192, 16384}
    };
    var registry = level.registryAccess().lookupOrThrow(CDGRegistries.FUEL_TYPE);
    for (int index = 0; index < names.length; index++) {
      var type = FuelType.getTypeFor(registry, fluid(names[index], 1).getFluid());
      var engines = List.of(type.normal(), type.modular(), type.huge());
      for (int engine = 0; engine < engines.size(); engine++) {
        var properties = engines.get(engine);
        require(
            Math.abs(properties.burn() - 0.05F) < 0.00001F,
            "Native engine fuel burn is 0.05mB/t: " + names[index]);
        require(
            properties.strength() == strengths[index][engine],
            "Native engine fuel strengths: " + names[index]);
        require(properties.speed() > 0, "Native registered fuel drives every engine type");
      }
    }
    require(
        !FuelType.getTypeFor(registry, fluid("crude_oil", 1).getFluid()).isFlammable(),
        "Crude oil must be refined before engine use");
  }

  private static void fluidInput(
      ProcessingRecipe<?, ?> recipe, int index, String name, int amount) {
    var input = recipe.getFluidIngredients().get(index);
    require(
        input.amount() == amount && input.test(fluid(name, amount)),
        "Loaded native fluid input: " + name);
  }

  private static void fluidOutputs(ProcessingRecipe<?, ?> recipe, List<String> names, int amount) {
    require(
        recipe.getFluidResults().size() == names.size() && recipe.getRollableResults().isEmpty(),
        "Native processing produces only its declared fluids");
    for (int index = 0; index < names.size(); index++) {
      var output = recipe.getFluidResults().get(index);
      require(
          output.getAmount() == amount
              && output.getFluid().isSame(fluid(names.get(index), 1).getFluid()),
          "Loaded native fluid yield: " + names.get(index));
    }
  }

  private static MechanicalCraftingInput mechanicalInput(
      ServerLevel level, int width, int height, List<ItemStack> items) {
    var grid = new ListTag();
    for (int slot = 0; slot < items.size(); slot++) {
      var entry = new CompoundTag();
      entry.putInt("x", slot % width);
      entry.putInt("y", height - 1 - slot / width);
      entry.put("item", items.get(slot).saveOptional(level.registryAccess()));
      grid.add(entry);
    }
    var data = new CompoundTag();
    data.put("Grid", grid);
    var grouped = GroupedItems.read(data, level.registryAccess());
    grouped.calcStats();
    return MechanicalCraftingInput.of(grouped);
  }

  private static ProcessingRecipe<?, ?> processing(ServerLevel level, String path) {
    if (!(recipe(level, path) instanceof ProcessingRecipe<?, ?> processing))
      throw new IllegalStateException("Expected native processing recipe: " + path);
    return processing;
  }

  private static Recipe<?> recipe(ServerLevel level, String path) {
    return level
        .getRecipeManager()
        .byKey(id("createdieselgenerators:" + path))
        .orElseThrow(
            () -> new IllegalStateException("Missing native Diesel production recipe: " + path))
        .value();
  }

  private static FluidStack fluid(String name, int amount) {
    var id = id("createdieselgenerators:" + name);
    require(BuiltInRegistries.FLUID.containsKey(id), "Registered native production fluid: " + name);
    return new FluidStack(BuiltInRegistries.FLUID.get(id), amount);
  }

  private static ItemStack stack(String name) {
    require(BuiltInRegistries.ITEM.containsKey(id(name)), "Registered production item: " + name);
    return new ItemStack(BuiltInRegistries.ITEM.get(id(name)));
  }

  private static void result(ItemStack output, String name, int amount) {
    require(
        output.is(stack("createdieselgenerators:" + name).getItem()) && output.getCount() == amount,
        "Exact native production result: " + name);
  }

  private static ResourceLocation id(String name) {
    return ResourceLocation.parse(name);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
