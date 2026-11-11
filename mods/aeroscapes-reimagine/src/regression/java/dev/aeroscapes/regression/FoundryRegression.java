package dev.aeroscapes.regression;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler.GroupedItems;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Exercises the loaded tag/condition boundary behind the foundry's material conservation. */
public final class FoundryRegression {
  private FoundryRegression() {}

  public static void verify(ServerLevel level) {
    for (String metal : List.of("steel", "zinc", "netherite", "bronze")) wireCycle(level, metal);
    verifyLaserGlass(level);
    steelSheets(level);
    LadleRegression.verify(level);
    tungstenOre(level);
    for (String metal : List.of("steel", "bronze")) {
      var tag = TagKey.create(Registries.FLUID, id("c:molten_" + metal));
      for (String fluid : List.of("molten_" + metal, "flowing_molten_" + metal)) {
        require(
            BuiltInRegistries.FLUID
                .get(id("createmetallurgy:" + fluid))
                .defaultFluidState()
                .is(tag),
            "CBC foundry fluid tag: " + fluid);
      }
    }
    for (String recipe : List.of("curving/diamond", "turning/test_single_output_no_gui"))
      require(
          level.getRecipeManager().byKey(id("vintageimprovements:" + recipe)).isEmpty(),
          "Removed recipe still loaded: " + recipe);
    for (String stone : List.of("asurine", "crimsite", "tuff")) {
      for (String suffix : List.of("", "_recycling")) {
        var recipe = processing(level, "aeroscapes_reimagine:crushing/" + stone + suffix);
        String byproduct = stone.equals("asurine") ? "sulfur_chunk" : "vanadium_nugget";
        require(
            recipe.getRollableResults().stream()
                .anyMatch(
                    result ->
                        result.getStack().is(stack("vintageimprovements:" + byproduct).getItem())
                            && Math.abs(result.getChance() - 0.1F) < 0.0001F),
            "Reachable crushing byproduct: " + byproduct);
        require(recipe.getProcessingDuration() > 0, "Crushing duration remains defined");
      }
    }
    var recoil = recipe(level, "createbigcannons:sequenced_assembly/recoil_spring");
    require(
        encoded(level, recoil)
            .getAsJsonArray("sequence")
            .get(0)
            .getAsJsonObject()
            .get("type")
            .getAsString()
            .equals("vintageimprovements:coiling"),
        "CBC recoil spring uses the selected coiling machine");
    for (String recipe :
        List.of(
            "alloying/steel",
            "alloying/obdurium",
            "melting/tungsten/raw_material",
            "casting_in_table/steel/ingot",
            "casting_in_table/tungsten/ingot",
            "casting_in_table/obdurium/ingot"))
      require(recipe(level, "createmetallurgy:" + recipe) != null, "Foundry route " + recipe);
  }

  public static void verifyLaserGlass(ServerLevel level) {
    for (String color :
        List.of(
            "colorless",
            "black",
            "blue",
            "brown",
            "cyan",
            "gray",
            "green",
            "light_blue",
            "light_gray",
            "lime",
            "magenta",
            "orange",
            "pink",
            "purple",
            "red",
            "white",
            "yellow")) {
      boolean clear = color.equals("colorless");
      String name = clear ? "glass" : color + "_glass";
      String input = clear ? "glass" : color + "_stained_glass";
      String output = clear ? "glass_pane" : color + "_stained_glass_pane";
      var recipe = processing(level, "vintageimprovements:laser_cutting/" + name);
      var ingredient = recipe.getIngredients().getFirst();
      require(
          ingredient.test(stack("minecraft:" + input)), "Reachable glass laser input: " + color);
      require(!ingredient.test(stack("minecraft:" + output)), "Laser excludes panes: " + color);
      require(!ingredient.test(stack("minecraft:red_wool")), "Laser excludes dyed wool: " + color);
      require(
          !ingredient.test(
              stack("minecraft:" + (color.equals("red") ? "blue" : "red") + "_stained_glass")),
          "Laser preserves glass color: " + color);
      var result = recipe.getResultItem(level.registryAccess());
      require(
          result.is(stack("minecraft:" + output).getItem()) && result.getCount() == 6,
          "Six matching glass panes: " + color);
      var data = encoded(level, recipe);
      require(
          data.get("energy").getAsInt() == 2000 && data.get("max_charge_rate").getAsInt() == 50,
          "Native laser processing cost: " + color);
    }
    var laserComponent = recipe(level, "vintageimprovements:mechanical_crafting/laser");
    require(laserComponent instanceof MechanicalCraftingRecipe, "Native laser component recipe");
    var mechanical = (MechanicalCraftingRecipe) laserComponent;
    var redstone = stack("minecraft:redstone");
    var copper = stack("create:copper_sheet");
    var layout =
        CraftingInput.of(
            3,
            4,
            List.of(
                ItemStack.EMPTY,
                redstone,
                ItemStack.EMPTY,
                copper,
                stack("create:electron_tube"),
                copper.copy(),
                copper.copy(),
                stack("minecraft:glowstone_dust"),
                copper.copy(),
                ItemStack.EMPTY,
                stack("minecraft:red_stained_glass"),
                ItemStack.EMPTY));
    var grid = new ListTag();
    for (int slot = 0; slot < layout.size(); slot++) {
      var entry = new CompoundTag();
      entry.putInt("x", slot % 3);
      entry.putInt("y", 3 - slot / 3);
      entry.put("item", layout.getItem(slot).saveOptional(level.registryAccess()));
      grid.add(entry);
    }
    var data = new CompoundTag();
    data.put("Grid", grid);
    var grouped = GroupedItems.read(data, level.registryAccess());
    grouped.calcStats();
    var input = MechanicalCraftingInput.of(grouped);
    require(mechanical.matches(input, level), "Survival laser component accepts red glass");
    require(
        mechanical
            .assemble(input, level.registryAccess())
            .is(stack("vintageimprovements:laser_item").getItem()),
        "Native laser component output");
  }

  private static void tungstenOre(ServerLevel level) {
    var nether = level.getServer().getLevel(Level.NETHER);
    require(nether != null, "Nether exists for native wolframite generation");
    var ore = BuiltInRegistries.BLOCK.get(id("createmetallurgy:wolframite_ore"));
    var position = new BlockPos.MutableBlockPos();
    for (int chunkX = 0; chunkX < 2; chunkX++) {
      for (int chunkZ = 0; chunkZ < 2; chunkZ++) {
        var chunk = nether.getChunk(chunkX, chunkZ);
        for (int x = 0; x < 16; x++) {
          for (int z = 0; z < 16; z++) {
            for (int y = 0; y <= 60; y++) {
              position.set(chunkX * 16 + x, y, chunkZ * 16 + z);
              if (chunk.getBlockState(position).is(ore)) {
                com.mojang.logging.LogUtils.getLogger()
                    .info("Natural wolframite witness: {} seed={}", position, nether.getSeed());
                return;
              }
            }
          }
        }
      }
    }
    throw new IllegalStateException("No natural wolframite in the four-chunk Nether cohort");
  }

  private static void wireCycle(ServerLevel level, String metal) {
    ItemStack rod = stack("vintageimprovements:" + metal + "_rod");
    var input = CraftingInput.of(1, 2, List.of(rod.copy(), rod.copy()));
    var crafting =
        level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
    var wires = crafting.value().assemble(input, level.registryAccess());
    require(wires.is(stack("vintageimprovements:" + metal + "_wire").getItem()), "Wire output");
    var casting = recipe(level, "createmetallurgy:casting_in_table/" + metal + "/rod");
    var castJson = encoded(level, casting);
    int rodCost =
        castJson.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("amount").getAsInt();
    int rods = casting.getResultItem(level.registryAccess()).getCount();
    var melting = processing(level, "createmetallurgy:melting/" + metal + "/wire");
    require(melting.getIngredients().getFirst().test(wires), "Wire is accepted by native melting");
    int returned = melting.getFluidResults().getFirst().getAmount() * wires.getCount();
    require(
        returned * rods == rodCost * 2,
        metal + " loop multiplies metal: input=" + rodCost * 2 / rods + ", output=" + returned);
    require(
        !castJson.has("mold_consumed") || !castJson.get("mold_consumed").getAsBoolean(),
        "Ordinary graphite mold stays reusable");
    require(
        crafting.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
        "Wire craft must not return input metal");
  }

  private static void steelSheets(ServerLevel level) {
    var sheet = stack("aeroscapes_reimagine:steel_sheet");
    require(sheet.is(TagKey.create(Registries.ITEM, id("c:plates/steel"))), "Steel plate tag");
    var pressing = processing(level, "aeroscapes_reimagine:pressing/steel_sheet");
    for (String ingot : List.of("createmetallurgy:steel_ingot", "createbigcannons:steel_ingot"))
      require(
          pressing.getIngredients().getFirst().test(stack(ingot)), "Shared steel ingot: " + ingot);
    require(
        pressing.getRollableResults().size() == 1
            && pressing.getRollableResults().getFirst().getStack().is(sheet.getItem())
            && pressing.getRollableResults().getFirst().getStack().getCount() == 1,
        "One steel ingot presses to one steel plate");
    var casting = recipe(level, "createmetallurgy:casting_in_table/steel/plate");
    require(
        casting.getResultItem(level.registryAccess()).is(sheet.getItem()), "Native plate casting");
    require(
        processing(level, "createmetallurgy:melting/steel/plate")
                .getFluidResults()
                .getFirst()
                .getAmount()
            == 90,
        "Steel plate remelts to one ingot");
    require(
        !stack("createbigcannons:spring_wire")
            .is(TagKey.create(Registries.ITEM, id("c:wires/steel"))),
        "CBC 30mB spring wire must not receive the 45mB generic wire route");
  }

  private static JsonObject encoded(ServerLevel level, Recipe<?> recipe) {
    var value =
        Recipe.CODEC
            .encodeStart(
                level.registryAccess().createSerializationContext(JsonOps.INSTANCE), recipe)
            .getOrThrow();
    if (!(value instanceof JsonObject object))
      throw new IllegalStateException("Recipe must encode as object");
    return object;
  }

  private static ProcessingRecipe<?, ?> processing(ServerLevel level, String name) {
    if (!(recipe(level, name) instanceof ProcessingRecipe<?, ?> recipe))
      throw new IllegalStateException("Expected processing recipe: " + name);
    return recipe;
  }

  private static Recipe<?> recipe(ServerLevel level, String name) {
    return level
        .getRecipeManager()
        .byKey(id(name))
        .orElseThrow(() -> new IllegalStateException("Missing loaded recipe " + name))
        .value();
  }

  private static ItemStack stack(String name) {
    require(BuiltInRegistries.ITEM.containsKey(id(name)), "Missing registered item " + name);
    return new ItemStack(BuiltInRegistries.ITEM.get(id(name)));
  }

  private static ResourceLocation id(String name) {
    return ResourceLocation.parse(name);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
