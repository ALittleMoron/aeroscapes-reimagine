package dev.aeroscapes.regression;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipeInput;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

/** Loaded native station matching and inventory/fluid conservation for the integrated recipes. */
public final class RecipeCompatibilityRegression {
  private RecipeCompatibilityRegression() {}

  public static void verify(ServerLevel level) {
    eggs(level);
    cutting(level);
    copiedIngredients(level);
    creamCheese(level);
    processing(level);
    filteredAlternatives(level);
    drinkRoundTrips(level);
    engineeringPatterns(level);
    System.out.println(
        "Recipe compatibility regression passed: physical eggs, kitchens, native basin conservation, processing and selectors");
  }

  private static void eggs(ServerLevel level) {
    var raw =
        BuiltInRegistries.ITEM
            .getTag(TagKey.create(Registries.ITEM, id("aeroscapes_reimagine:raw_eggs")))
            .orElseThrow();
    require(raw.size() == 14, "Fourteen physical egg sources remain");
    for (var item : raw) {
      ItemStack input = new ItemStack(item.value());
      cookedEgg(level, RecipeType.SMELTING, input);
      cookedEgg(level, RecipeType.SMOKING, input);
      cookedEgg(level, RecipeType.CAMPFIRE_COOKING, input);
    }
    for (var type : List.of(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING)) {
      var input = new SingleRecipeInput(stack("createfood:egg_powder"));
      require(
          level.getRecipeManager().getAllRecipesFor(type).stream()
              .noneMatch(holder -> holder.value().matches(input, level)),
          "Powder must not re-fry into a powderable egg");
    }
    var sandwich = crafting(level, "farmersdelight:egg_sandwich");
    for (String legacy :
        List.of("animalhusbandry:fried_egg", "naturalist:cooked_egg", "hybrid_birds:cooked_egg")) {
      var input =
          CraftingInput.of(3, 1, List.of(new ItemStack(Items.BREAD), stack(legacy), stack(legacy)));
      require(
          sandwich.matches(input, level),
          "Saved cooked eggs remain valid sandwich ingredients: " + legacy);
      result(sandwich.assemble(input, level.registryAccess()), "farmersdelight:egg_sandwich", 1);
      var assembly =
          (SequencedAssemblyRecipe)
              recipe(level, "delightfulcreators:sequenced_assembly/egg_sandwich");
      require(
          assembly.getSequence().getFirst().getRecipe().getIngredients().get(1).test(stack(legacy)),
          "Saved egg remains valid for mechanical sandwich assembly");
    }
    for (String advancement :
        List.of(
            "naturalist:recipes/food/cooked_egg",
            "hybrid_birds:recipes/food/cooked_egg_from_smelting",
            "hybrid_birds:recipes/food/cooked_egg_from_smoking",
            "hybrid_birds:recipes/food/cooked_egg_from_campfire_cooking")) {
      var holder = level.getServer().getAdvancements().get(id(advancement));
      require(holder != null, "Original recipe advancement remains registered");
      for (var award : Objects.requireNonNull(holder).value().rewards().recipes())
        require(
            level.getRecipeManager().byKey(award).isPresent(),
            "Recipe award must not name a removed recipe");
    }
    RecipeType<EmptyingRecipe> emptying = AllRecipeTypes.EMPTYING.getType();
    var eggInput = new SingleRecipeInput(new ItemStack(Items.EGG));
    require(
        level.getRecipeManager().getAllRecipesFor(emptying).stream()
                .filter(holder -> holder.value().matches(eggInput, level))
                .count()
            == 1,
        "Item drain has one physical-egg route");
    var drained = GenericItemEmptying.emptyItem(level, new ItemStack(Items.EGG), false);
    require(
        drained.getFirst().getFluid().isSame(fluid("ratatouille:egg_yolk", 1).getFluid())
            && drained.getFirst().getAmount() == 250,
        "Native drain yields 250mB yolk");
    result(drained.getSecond(), "ratatouille:egg_shell", 1);
    var wholeEgg = (BasinRecipe) recipe(level, "createfood:create/mixing/egg_fluid_from_mixing");
    try (var fixture = new BasinFixture(level, false)) {
      fixture.load(List.of(new ItemStack(Items.EGG)), List.of());
      require(
          BasinRecipe.match(fixture.basin, wholeEgg),
          "Whole-egg fluid remains reachable in the mixer");
      require(BasinRecipe.apply(fixture.basin, wholeEgg), "Native whole-egg mixing applies");
      require(
          fixture.quantity("createfood:eggshell") == 1
              && fixture.fluidQuantity("createfood:egg") == 250,
          "Mixer conserves one egg and produces one shell plus 250mB whole egg");
    }
  }

  private static <T extends Recipe<SingleRecipeInput>> void cookedEgg(
      ServerLevel level, RecipeType<T> type, ItemStack egg) {
    var input = new SingleRecipeInput(egg);
    var matches =
        level.getRecipeManager().getAllRecipesFor(type).stream()
            .filter(holder -> holder.value().matches(input, level))
            .toList();
    require(matches.size() == 1, "One cooked-egg result per physical source/station: " + egg);
    result(
        matches.getFirst().value().assemble(input, level.registryAccess()),
        "farmersdelight:fried_egg",
        1);
    require(
        matches.getFirst().value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
        "No extra cooked-egg remainder");
  }

  private static void cutting(ServerLevel level) {
    cut(level, "create:dough", "farmersdelight:raw_pasta", 1);
    cut(level, "minecraft:pumpkin_pie", "createfood:pumpkin_pie_slice", 4);
    var recombine = crafting(level, "delightfulcreators:crafting/pumpkin_pie_from_cutting");
    var slices =
        CraftingInput.of(
            2,
            2,
            List.of(
                stack("delightfulcreators:pumpkin_pie_slice"),
                stack("delightfulcreators:pumpkin_pie_slice"),
                stack("delightfulcreators:pumpkin_pie_slice"),
                stack("delightfulcreators:pumpkin_pie_slice")));
    require(recombine.matches(slices, level), "Four saved DC slices restore one original pie");
    result(recombine.assemble(slices, level.registryAccess()), "minecraft:pumpkin_pie", 1);
    for (String variant : List.of("", "chocolate_")) {
      cut(
          level,
          "createfood:" + variant + "graham_cracker_pie_crust",
          "createfood:mini_" + variant + "graham_cracker_pie_crust",
          2);
      cut(
          level,
          "createfood:mini_" + variant + "graham_cracker_pie_crust",
          "createfood:" + variant + "graham_cracker_crumbs",
          1);
    }
    cut(level, "ratatouille:dried_cocoa_beans", "createfood:cacao_nibs", 1);
    for (var type : List.of(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING)) {
      var input = new SingleRecipeInput(new ItemStack(Items.COCOA_BEANS));
      var matches =
          level.getRecipeManager().getAllRecipesFor(type).stream()
              .filter(holder -> holder.value().matches(input, level))
              .toList();
      require(matches.size() == 1, "Cocoa thermal processing is deterministic");
      result(
          matches.getFirst().value().assemble(input, level.registryAccess()),
          "ratatouille:dried_cocoa_beans",
          1);
    }
    RecipeType<MillingRecipe> milling = AllRecipeTypes.MILLING.getType();
    var input = new SingleRecipeInput(stack("ratatouille:dried_cocoa_beans"));
    var milled = level.getRecipeManager().getRecipeFor(milling, input, level).orElseThrow();
    result(milled.value().assemble(input, level.registryAccess()), "createfood:cacao_nibs", 1);
  }

  private static void cut(ServerLevel level, String inputItem, String output, int count) {
    var input = new CuttingBoardRecipeInput(stack(inputItem), stack("farmersdelight:iron_knife"));
    var matches =
        level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CUTTING.get()).stream()
            .filter(holder -> holder.value().matches(input, level))
            .toList();
    require(matches.size() == 1, "One knife-board route for " + inputItem);
    var chosen =
        RecipeManager.createCheck(ModRecipeTypes.CUTTING.get())
            .getRecipeFor(input, level)
            .orElseThrow();
    require(chosen.value().getResults().size() == 1, "Cutting result pool remains singular");
    result(chosen.value().assemble(input, level.registryAccess()), output, count);
  }

  private static void copiedIngredients(ServerLevel level) {
    basinSwap(
        level,
        "createfood:create/mixing/pumpkin_custard_fluid_from_mixing_heated",
        "minecraft:pumpkin",
        "minecraft:apple",
        true);
    basinSwap(
        level,
        "createfood:create/compacting/raw_chorus_fruit_cookie_from_compacting",
        "minecraft:chorus_fruit",
        "minecraft:sweet_berries",
        false);
    basinSwap(
        level,
        "createfood:create/mixing/mashed_potatoes_bowl_cheese_from_mixing_heated_milk",
        "createfood:cheese_slice",
        "farmersdelight:cooked_bacon",
        true);
    for (String route : List.of("dough", "egg", "wheat")) {
      var recipe =
          crafting(
              level,
              "createfood:minecraft/crafting/raw_chorus_fruit_cookie_from_" + route + "_crafting");
      var items =
          swappedInputs(
              recipe.getIngredients(), "minecraft:chorus_fruit", "minecraft:sweet_berries");
      int replaced =
          changedSlot(recipe.getIngredients(), "minecraft:chorus_fruit", "minecraft:sweet_berries");
      while (items.size() < 9) items.add(ItemStack.EMPTY);
      var positive = CraftingInput.of(3, 3, items);
      require(
          recipe.matches(positive, level),
          "Chorus-cookie crafting accepts actual chorus fruit: " + route);
      result(
          recipe.assemble(positive, level.registryAccess()),
          "createfood:raw_chorus_fruit_cookie",
          8);
      if (route.equals("wheat"))
        require(
            recipe.getRemainingItems(positive).stream()
                    .filter(value -> value.is(Items.BUCKET))
                    .mapToInt(ItemStack::getCount)
                    .sum()
                == 1,
            "Water-bucket cookie route returns exactly one bucket");
      items.set(replaced, new ItemStack(Items.SWEET_BERRIES));
      require(
          !recipe.matches(CraftingInput.of(3, 3, items), level),
          "Chorus cookie rejects the copied berry ingredient");
    }
    var cooking =
        (CookingPotRecipe)
            recipe(
                level,
                "createfood:farmersdelight/cooking/mashed_potatoes_bowl_cheese_from_cooking");
    var items =
        swappedInputs(
            cooking.getIngredients(), "createfood:cheese_slice", "farmersdelight:cooked_bacon");
    int replaced =
        changedSlot(
            cooking.getIngredients(), "createfood:cheese_slice", "farmersdelight:cooked_bacon");
    var inventory = new ItemStackHandler(6);
    for (int index = 0; index < items.size(); index++)
      inventory.setStackInSlot(index, items.get(index));
    require(
        cooking.matches(new RecipeWrapper(inventory), level),
        "Cheese mashed potatoes require cheese in the native pot");
    inventory.setStackInSlot(replaced, stack("farmersdelight:cooked_bacon"));
    require(
        !cooking.matches(new RecipeWrapper(inventory), level),
        "Cheese pot recipe rejects the copied bacon ingredient");
  }

  private static void basinSwap(
      ServerLevel level, String recipeId, String accepted, String rejected, boolean heated) {
    var recipe = (BasinRecipe) recipe(level, recipeId);
    var items = swappedInputs(recipe.getIngredients(), accepted, rejected);
    int changed = changedSlot(recipe.getIngredients(), accepted, rejected);
    var fluids =
        recipe.getFluidIngredients().stream()
            .map(ingredient -> ingredient.getFluids()[0].copy())
            .toList();
    try (var fixture = new BasinFixture(level, heated)) {
      fixture.load(items, fluids);
      require(
          BasinRecipe.match(fixture.basin, recipe),
          "Native basin accepts intended food input: " + recipeId);
      items.set(changed, stack(rejected));
      fixture.load(items, fluids);
      require(
          !BasinRecipe.match(fixture.basin, recipe),
          "Native basin rejects copied food input: " + recipeId);
    }
  }

  private static int changedSlot(List<Ingredient> ingredients, String accepted, String rejected) {
    for (int index = 0; index < ingredients.size(); index++)
      if (ingredients.get(index).test(stack(accepted))
          && !ingredients.get(index).test(stack(rejected))) return index;
    throw new IllegalStateException(
        "Expected distinct food ingredient: " + accepted + " versus " + rejected);
  }

  private static List<ItemStack> swappedInputs(
      List<Ingredient> ingredients, String accepted, String rejected) {
    int changed = changedSlot(ingredients, accepted, rejected);
    List<ItemStack> items = new ArrayList<>();
    for (Ingredient ingredient : ingredients) {
      require(
          ingredient.getItems().length > 0, "Loaded food ingredient is reachable through its tag");
      items.add(ingredient.getItems()[0].copy());
    }
    items.set(changed, stack(accepted));
    return items;
  }

  private static void creamCheese(ServerLevel level) {
    var compact =
        (BasinRecipe)
            recipe(level, "createfood:create/compacting/cream_cheese_from_compacting_cream_cheese");
    try (var fixture = new BasinFixture(level, false)) {
      fixture.load(List.of(), List.of(fluid("createfood:cream_cheese", 999)));
      require(
          !BasinRecipe.match(fixture.basin, compact),
          "A partial bucket cannot yield three cream-cheese portions");
      fixture.load(List.of(), List.of(fluid("createfood:cream_cheese", 1000)));
      require(
          BasinRecipe.match(fixture.basin, compact) && BasinRecipe.apply(fixture.basin, compact),
          "Native basin batches one exact bucket");
      require(
          fixture.quantity("createfood:cream_cheese") == 3
              && fixture.fluidQuantity("createfood:cream_cheese") == 0,
          "Compacting conserves exactly1000mB as three solid portions");
    }
    var restore =
        crafting(level, "createfood:minecraft/crafting/cream_cheese_bucket_from_crafting");
    var input =
        CraftingInput.of(
            2,
            2,
            List.of(
                new ItemStack(Items.BUCKET),
                stack("createfood:cream_cheese"),
                stack("createfood:cream_cheese"),
                stack("createfood:cream_cheese")));
    require(restore.matches(input, level), "Three cream cheeses restore one bucket");
    var bucket = restore.assemble(input, level.registryAccess());
    result(bucket, "createfood:cream_cheese_bucket", 1);
    require(
        restore.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
        "Bucket assembly retains no ingredient");
    var drained = GenericItemEmptying.emptyItem(level, bucket, false);
    require(
        bucket.isEmpty()
            && drained.getFirst().getAmount() == 1000
            && drained.getFirst().getFluid().isSame(fluid("createfood:cream_cheese", 1).getFluid()),
        "Native item drain restores exactly the original1000mB");
    result(drained.getSecond(), "minecraft:bucket", 1);
  }

  private static void processing(ServerLevel level) {
    var sand = (HauntingRecipe) recipe(level, "create:haunting/soul_sand");
    var soil = (HauntingRecipe) recipe(level, "create:haunting/soul_soil");
    var sandySoil = new SingleRecipeInput(stack("natures_spirit:sandy_soil"));
    require(
        !sand.matches(sandySoil, level) && soil.matches(sandySoil, level),
        "Sandy soil has one haunting identity");
    require(
        sand.matches(new SingleRecipeInput(new ItemStack(Items.SAND)), level),
        "Ordinary sand retains soul-sand haunting");
    var broadPane = (SplashingRecipe) recipe(level, "create:splashing/stained_glass_pane");
    var ironPane = (SplashingRecipe) recipe(level, "create:splashing/industrial_iron_window_pane");
    var paneInput = new SingleRecipeInput(stack("create:industrial_iron_window_pane"));
    require(
        !broadPane.matches(paneInput, level) && ironPane.matches(paneInput, level),
        "Washing preserves the specific iron-pane route");
    result(
        ironPane.assemble(paneInput, level.registryAccess()),
        "create:weathered_iron_window_pane",
        1);
    require(
        broadPane.matches(
            new SingleRecipeInput(new ItemStack(Items.RED_STAINED_GLASS_PANE)), level),
        "Dyed glass washing remains reachable");
    var ordinary =
        (CrushingRecipe) recipe(level, "createdieselgenerators:crushing/wood_chip_boats");
    var chest =
        (CrushingRecipe) recipe(level, "createdieselgenerators:crushing/wood_chip_chest_boats");
    var tag =
        BuiltInRegistries.ITEM
            .getTag(TagKey.create(Registries.ITEM, id("minecraft:chest_boats")))
            .orElseThrow();
    for (var item : tag) {
      var input = new SingleRecipeInput(new ItemStack(item.value()));
      require(
          !ordinary.matches(input, level) && chest.matches(input, level),
          "Chest-boat wood accounting is unambiguous: " + item);
    }
    require(
        ordinary.matches(new SingleRecipeInput(new ItemStack(Items.OAK_BOAT)), level),
        "Ordinary boat recycling remains reachable");
    require(
        chest.getRollableResults().stream()
                .filter(output -> output.getChance() == 1)
                .mapToInt(output -> output.getStack().getCount())
                .sum()
            == 99,
        "Chest boats preserve their99 guaranteed chips");
  }

  private static void filteredAlternatives(ServerLevel level) {
    var first = (BasinRecipe) recipe(level, "ratatouille:mixing/melon_juice");
    var second = (BasinRecipe) recipe(level, "delightfulcreators:mixing/melon_juice");
    try (var fixture = new BasinFixture(level, false)) {
      fixture.load(
          List.of(new ItemStack(Items.MELON_SLICE, 4), new ItemStack(Items.SUGAR)), List.of());
      require(
          BasinRecipe.match(fixture.basin, first) && BasinRecipe.match(fixture.basin, second),
          "Both useful juice recipes remain");
      for (var selected : List.of(first, second)) {
        var other = selected == first ? second : first;
        var fluid = selected.getFluidResults().getFirst().copy();
        fluid.setAmount(1000);
        var container = stack("fluidlogistics:copper_bucket");
        var handler = container.getCapability(Capabilities.FluidHandler.ITEM);
        require(handler != null, "Native component bucket exposes its fluid capability");
        require(
            Objects.requireNonNull(handler).fill(fluid, FluidAction.EXECUTE) == 1000,
            "Native component bucket accepts the intended juice");
        var filterContainer = handler.getContainer();
        var resolved = GenericItemEmptying.emptyItem(level, filterContainer.copy(), true);
        require(
            resolved.getFirst().getAmount() == 1000
                && resolved.getFirst().getFluid().isSame(fluid.getFluid()),
            "Native filter resolver reads the exact component-backed juice fluid");
        require(
            fixture.basin.getFilter().setFilter(filterContainer),
            "Native basin accepts the fluid filter container");
        require(
            BasinRecipe.match(fixture.basin, selected) && !BasinRecipe.match(fixture.basin, other),
            "Native basin filter selects the intended juice fluid");
      }
    }
  }

  private static void drinkRoundTrips(ServerLevel level) {
    for (String drink : List.of("hot_cocoa", "glow_berry_custard", "melon_juice", "apple_cider")) {
      var filling = (FillingRecipe) recipe(level, "delightfulcreators:filling/" + drink);
      var partial = filling.getRequiredFluid().getFluids()[0].copy();
      partial.setAmount(249);
      int quote =
          FillingBySpout.getRequiredAmountForItem(
              level, new ItemStack(Items.GLASS_BOTTLE), partial);
      // The native spout waits when this quote exceeds its available tank amount.
      require(
          !filling.getRequiredFluid().test(partial) && quote == 250 && quote > partial.getAmount(),
          "Native spout requires the full250mB bottle portion: " + drink);
      require(partial.getAmount() == 249, "Partial-portion lookup consumes no fluid");
      var portion = partial.copy();
      portion.setAmount(250);
      var empty = new ItemStack(Items.GLASS_BOTTLE);
      int required = FillingBySpout.getRequiredAmountForItem(level, empty, portion);
      require(required == 250, "Native bottle filling cost remains250mB: " + drink);
      var filled = FillingBySpout.fillItem(level, required, empty, portion);
      require(
          empty.isEmpty() && portion.isEmpty(),
          "Native spout consumes exactly one bottle and250mB");
      result(filled, "farmersdelight:" + drink, 1);
      var selected =
          AllRecipeTypes.EMPTYING.find(new SingleRecipeInput(filled), level).orElseThrow();
      require(
          selected.id().equals(id("delightfulcreators:emptying/" + drink)),
          "Native item drain selects the intended same-provider beverage route");
      var drained = GenericItemEmptying.emptyItem(level, filled, false);
      require(
          filled.isEmpty()
              && drained.getFirst().getAmount() == 250
              && drained.getFirst().getFluid().isSame(partial.getFluid()),
          "Native beverage round trip preserves exactly250mB: " + drink);
      result(drained.getSecond(), "minecraft:glass_bottle", 1);
    }
  }

  private static void engineeringPatterns(ServerLevel level) {
    uniqueCraft(
        level,
        "fluidlogistics:package_list_filter",
        1,
        3,
        List.of(
            stack("create:copper_nugget"),
            new ItemStack(Items.WHITE_WOOL),
            stack("create:copper_nugget")));
    uniqueCraft(
        level,
        "create_connected:item_silo",
        2,
        2,
        List.of(
            stack("create:iron_sheet"),
            new ItemStack(Items.BARREL),
            ItemStack.EMPTY,
            stack("create:iron_sheet")));
    uniqueCraft(
        level,
        "create:item_vault",
        1,
        3,
        List.of(
            stack("create:iron_sheet"), new ItemStack(Items.BARREL), stack("create:iron_sheet")));
    uniqueCraft(
        level,
        "createdieselgenerators:oil_barrel",
        3,
        1,
        List.of(
            stack("create:iron_sheet"), new ItemStack(Items.BARREL), stack("create:iron_sheet")));
  }

  private static void uniqueCraft(
      ServerLevel level, String expected, int width, int height, List<ItemStack> items) {
    var input = CraftingInput.of(width, height, items);
    var matches =
        level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
            .filter(holder -> holder.value().matches(input, level))
            .toList();
    require(
        matches.size() == 1,
        "Engineering input must match exactly one native recipe: "
            + expected
            + "; matched="
            + matches.stream()
                .map(
                    holder ->
                        holder.id()
                            + " => "
                            + holder.value().assemble(input, level.registryAccess()))
                .toList());
    result(matches.getFirst().value().assemble(input, level.registryAccess()), expected, 1);
  }

  private static CraftingRecipe crafting(ServerLevel level, String name) {
    if (!(recipe(level, name) instanceof CraftingRecipe value))
      throw new IllegalStateException("Expected crafting recipe " + name);
    return value;
  }

  private static Recipe<?> recipe(ServerLevel level, String name) {
    return level
        .getRecipeManager()
        .byKey(id(name))
        .orElseThrow(() -> new IllegalStateException("Missing loaded recipe " + name))
        .value();
  }

  private static ResourceLocation id(String value) {
    return ResourceLocation.parse(value);
  }

  private static ItemStack stack(String value) {
    return new ItemStack(BuiltInRegistries.ITEM.getOptional(id(value)).orElseThrow());
  }

  private static FluidStack fluid(String value, int amount) {
    return new FluidStack(BuiltInRegistries.FLUID.getOptional(id(value)).orElseThrow(), amount);
  }

  private static void result(ItemStack value, String expected, int count) {
    require(
        value.is(stack(expected).getItem()) && value.getCount() == count,
        "Expected " + count + " " + expected + ", received " + value);
  }

  private static void require(boolean condition, String context) {
    if (!condition) throw new IllegalStateException(context);
  }

  private static final class BasinFixture implements AutoCloseable {
    private final ServerLevel level;
    private final BlockPos position;
    private final BlockState previous;
    private final BlockState previousHeat;
    private final CompoundTag previousData;
    private final CompoundTag previousHeatData;
    private final BasinBlockEntity basin;

    private BasinFixture(ServerLevel level, boolean heated) {
      this.level = level;
      position =
          new BlockPos(
              level.getSharedSpawnPos().getX() + 4,
              level.getMaxBuildHeight() - 8,
              level.getSharedSpawnPos().getZ() + 4);
      previous = level.getBlockState(position);
      previousHeat = level.getBlockState(position.below());
      var previousEntity = level.getBlockEntity(position);
      var previousHeatEntity = level.getBlockEntity(position.below());
      previousData =
          previousEntity == null
              ? null
              : previousEntity.saveWithFullMetadata(level.registryAccess());
      previousHeatData =
          previousHeatEntity == null
              ? null
              : previousHeatEntity.saveWithFullMetadata(level.registryAccess());
      level.setBlockAndUpdate(
          position.below(),
          heated
              ? AllBlocks.BLAZE_BURNER
                  .getDefaultState()
                  .setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.KINDLED)
              : Blocks.AIR.defaultBlockState());
      level.setBlockAndUpdate(position, AllBlocks.BASIN.getDefaultState());
      if (!(level.getBlockEntity(position) instanceof BasinBlockEntity value))
        throw new IllegalStateException("Native basin did not initialize");
      basin = value;
      basin.initialize();
    }

    private void load(List<ItemStack> items, List<FluidStack> fluids) {
      clear();
      for (int index = 0; index < items.size(); index++)
        basin.inputInventory.setStackInSlot(index, items.get(index).copy());
      for (var fluid : fluids)
        require(
            basin.inputTank.getPrimaryHandler().fill(fluid.copy(), FluidAction.EXECUTE)
                == fluid.getAmount(),
            "Basin accepts the complete input fluid");
    }

    private int quantity(String name) {
      var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, position, null);
      require(handler != null, "Native basin item capability");
      int count = 0;
      for (int index = 0; index < Objects.requireNonNull(handler).getSlots(); index++) {
        var value = handler.getStackInSlot(index);
        if (value.is(stack(name).getItem())) count += value.getCount();
      }
      return count;
    }

    private int fluidQuantity(String name) {
      var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, position, null);
      require(handler != null, "Native basin fluid capability");
      int amount = 0;
      for (int index = 0; index < Objects.requireNonNull(handler).getTanks(); index++) {
        var value = handler.getFluidInTank(index);
        if (value.getFluid().isSame(fluid(name, 1).getFluid())) amount += value.getAmount();
      }
      return amount;
    }

    @Override
    public void close() {
      clear();
      level.setBlockAndUpdate(position, previous);
      level.setBlockAndUpdate(position.below(), previousHeat);
      restore(position, previousData);
      restore(position.below(), previousHeatData);
    }

    private void clear() {
      // Basin's Container.clearContent resets items and the filter, not its fluid behaviours.
      basin.clearContent();
      for (var tanks : List.of(basin.getTanks().getFirst(), basin.getTanks().getSecond())) {
        var handler = tanks.getCapability();
        for (int tank = 0; tank < handler.getTanks(); tank++) {
          var previousFluid = handler.getFluidInTank(tank).copy();
          if (!previousFluid.isEmpty()) {
            require(
                handler.drain(previousFluid, FluidAction.EXECUTE).getAmount()
                    == previousFluid.getAmount(),
                "Fixture removes its previous native input/output fluid");
          }
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
          require(handler.getFluidInTank(tank).isEmpty(), "Fixture starts with empty fluid tanks");
        }
      }
    }

    private void restore(BlockPos position, CompoundTag saved) {
      BlockEntity restored = level.getBlockEntity(position);
      if (restored != null && saved != null)
        restored.loadWithComponents(saved, level.registryAccess());
    }
  }
}
