package dev.aeroscapes.regression;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Exercises native recipes and sensor categories backed by repaired legacy tags. */
public final class TagCompatibilityRegression {
  private TagCompatibilityRegression() {}

  public static void verify(ServerLevel level) {
    crafting(level);
    sensors(level);
    horseFeed(level);
  }

  private static void crafting(ServerLevel level) {
    craft(
        level,
        "glass/glass_button_recipe",
        2,
        "glass_button",
        Items.IRON_NUGGET,
        Items.IRON_NUGGET,
        Items.GLASS,
        Items.OAK_BUTTON);
    craft(
        level,
        "powerplant/red_power_plant_recipe",
        2,
        "red_power_plant",
        Items.STICK,
        Items.STICK,
        Items.RED_TULIP,
        Items.OAK_PRESSURE_PLATE);
  }

  private static void craft(
      ServerLevel level, String recipePath, int width, String resultPath, Item... ingredients) {
    ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("rsgauges", recipePath);
    var loaded = level.getRecipeManager().byKey(recipeId).orElseThrow().value();
    require(loaded instanceof CraftingRecipe, "Native crafting recipe: " + recipeId);
    var recipe = (CraftingRecipe) loaded;
    var input =
        CraftingInput.of(
            width,
            ingredients.length / width,
            Arrays.stream(ingredients).map(ItemStack::new).toList());
    require(recipe.matches(input, level), "Native survival grid matches: " + recipeId);
    var result = recipe.assemble(input, level.registryAccess());
    require(
        !result.isEmpty() && result.getCount() == 1 && result.is(item("rsgauges:" + resultPath)),
        "Native survival grid produces expected item: " + recipeId);
  }

  private static void sensors(ServerLevel level) {
    var spawn = level.getSharedSpawnPos();
    var position =
        new BlockPos(
            spawn.getX() + 32,
            Math.min(spawn.getY() + 32, level.getMaxBuildHeight() - 2),
            spawn.getZ() + 32);
    var previous = level.getBlockState(position);
    var previousEntity = level.getBlockEntity(position);
    var previousData =
        previousEntity == null ? null : previousEntity.saveWithFullMetadata(level.registryAccess());
    try {
      var getMatcher =
          Class.forName("wile.rsgauges.detail.BlockCategories")
              .getMethod("getMatcher", String.class);
      var match =
          Class.forName("wile.rsgauges.detail.BlockCategories$Matcher")
              .getMethod("match", Level.class, BlockPos.class);
      for (var sample :
          List.of(
              new SensorCase("material_clay", "clay_like", Blocks.CLAY),
              new SensorCase("material_glass", "glass_like", Blocks.GLASS),
              new SensorCase("woodlog", "logs", Blocks.OAK_LOG),
              new SensorCase("ore", "ores", Blocks.IRON_ORE),
              new SensorCase("planks", "planks", Blocks.OAK_PLANKS),
              new SensorCase("plant", "plants", Blocks.OAK_LOG),
              new SensorCase("sapling", "saplings", Blocks.OAK_SAPLING),
              new SensorCase("slab", "slabs", Blocks.STONE_SLAB),
              new SensorCase("soil", "soils", Blocks.DIRT),
              new SensorCase("material_stone", "stone_like", Blocks.STONE),
              new SensorCase("material_water", "water_like", Blocks.WET_SPONGE),
              new SensorCase("material_wood", "wooden", Blocks.OAK_PLANKS))) {
        var tag =
            TagKey.create(
                Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("rsgauges", sample.tag()));
        require(sample.block().defaultBlockState().is(tag), "Loaded sensor tag: " + sample.tag());
        var matcher = getMatcher.invoke(null, sample.category());
        level.setBlock(position, sample.block().defaultBlockState(), 2);
        require(
            Boolean.TRUE.equals(match.invoke(matcher, level, position)),
            "Native sensor recognizes " + sample.category());
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
        require(
            Boolean.FALSE.equals(match.invoke(matcher, level, position)),
            "Native sensor rejects air: " + sample.category());
      }
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Native RSGauges sensor API", exception);
    } finally {
      level.setBlock(position, previous, 2);
      if (previousData != null) {
        var restored = level.getBlockEntity(position);
        require(restored != null, "Restore previous sensor-test block entity");
        restored.loadWithComponents(previousData, level.registryAccess());
      }
    }
  }

  private static void horseFeed(ServerLevel level) {
    var recipeId = ResourceLocation.parse("delightfulcreators:sequenced_assembly/horse_feed");
    var loaded = level.getRecipeManager().byKey(recipeId).orElseThrow().value();
    require(loaded instanceof SequencedAssemblyRecipe, "Native horse-feed assembly");
    var recipe = (SequencedAssemblyRecipe) loaded;
    for (String bale : List.of("farmersdelight:rice_bale", "minecraft:hay_block"))
      require(
          recipe.getIngredient().test(new ItemStack(item(bale))),
          "Native horse-feed assembly accepts " + bale);
    require(
        !recipe.getIngredient().test(new ItemStack(Items.COBBLESTONE)),
        "Native horse-feed assembly rejects unrelated blocks");
  }

  private static Item item(String id) {
    return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
  }

  private static void require(boolean condition, String context) {
    if (!condition) throw new IllegalStateException(context);
  }

  private record SensorCase(String category, String tag, Block block) {}
}
