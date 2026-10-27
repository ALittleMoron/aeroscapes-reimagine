package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Tests selected loaded survival recipes and loot in a disposable server. */
@EventBusSubscriber(modid = "aeroscapes_regression", value = Dist.DEDICATED_SERVER)
public final class ShipProgressionRegression {
  private ShipProgressionRegression() {}

  @SubscribeEvent
  public static void onStarted(ServerStartedEvent event) {
    if (!Boolean.getBoolean("aeroscapes.regression.shipProgression")) return;
    ServerLevel level = event.getServer().overworld();
    verifyLoot(level);
    smelt(level, "minecraft:raw_iron", "minecraft:iron_ingot");
    smelt(level, "minecraft:raw_copper", "minecraft:copper_ingot");
    smelt(level, "create:raw_zinc", "create:zinc_ingot");
    processingData(
        level,
        "create:pressing/iron_ingot",
        "create:pressing",
        "create:iron_sheet",
        1,
        "minecraft:iron_ingot");
    processingData(
        level,
        "create:mixing/brass_ingot",
        "create:mixing",
        "create:brass_ingot",
        2,
        "minecraft:copper_ingot",
        "create:zinc_ingot");
    orderedProcessingData(
        level,
        "create:item_application/andesite_casing_from_log",
        "create:andesite_casing",
        "minecraft:stripped_oak_log",
        "create:andesite_alloy");
    orderedProcessingData(
        level,
        "create:item_application/brass_casing_from_log",
        "create:brass_casing",
        "minecraft:stripped_oak_log",
        "create:brass_ingot");
    craft(level, 1, "minecraft:oak_planks", 4, "minecraft:oak_log");
    craft(level, 1, "minecraft:stick", 4, "minecraft:oak_planks", "minecraft:oak_planks");
    craft(level, 1, "minecraft:iron_nugget", 9, "minecraft:iron_ingot");
    craft(
        level,
        3,
        "minecraft:bucket",
        1,
        "minecraft:iron_ingot",
        "",
        "minecraft:iron_ingot",
        "",
        "minecraft:iron_ingot",
        "");
    for (String material : List.of("minecraft:cobblestone", "minecraft:iron_ingot")) {
      String pickaxe =
          material.equals("minecraft:cobblestone")
              ? "minecraft:stone_pickaxe"
              : "minecraft:iron_pickaxe";
      craft(
          level,
          3,
          pickaxe,
          1,
          material,
          material,
          material,
          "",
          "minecraft:stick",
          "",
          "",
          "minecraft:stick",
          "");
    }
    craft(
        level,
        3,
        "minecraft:furnace",
        1,
        "minecraft:cobblestone",
        "minecraft:cobblestone",
        "minecraft:cobblestone",
        "minecraft:cobblestone",
        "",
        "minecraft:cobblestone",
        "minecraft:cobblestone",
        "minecraft:cobblestone",
        "minecraft:cobblestone");
    craft(
        level,
        2,
        "create:andesite_alloy",
        1,
        "minecraft:iron_nugget",
        "minecraft:andesite",
        "minecraft:andesite",
        "minecraft:iron_nugget");
    craft(level, 1, "create:shaft", 8, "create:andesite_alloy", "create:andesite_alloy");
    craft(level, 2, "create:cogwheel", 1, "create:shaft", "minecraft:oak_planks");
    craft(
        level,
        3,
        "create:water_wheel",
        1,
        "minecraft:oak_planks",
        "minecraft:oak_planks",
        "minecraft:oak_planks",
        "minecraft:oak_planks",
        "create:shaft",
        "minecraft:oak_planks",
        "minecraft:oak_planks",
        "minecraft:oak_planks",
        "minecraft:oak_planks");
    craft(
        level,
        3,
        "create:basin",
        1,
        "create:andesite_alloy",
        "",
        "create:andesite_alloy",
        "create:andesite_alloy",
        "create:andesite_alloy",
        "create:andesite_alloy");
    craft(
        level,
        3,
        "create:whisk",
        1,
        "",
        "create:andesite_alloy",
        "",
        "create:iron_sheet",
        "create:andesite_alloy",
        "create:iron_sheet",
        "create:iron_sheet",
        "create:iron_sheet",
        "create:iron_sheet");
    craft(
        level,
        1,
        "create:mechanical_mixer",
        1,
        "create:cogwheel",
        "create:andesite_casing",
        "create:whisk");
    craft(
        level,
        3,
        "create:mechanical_saw",
        1,
        "",
        "create:iron_sheet",
        "",
        "create:iron_sheet",
        "minecraft:iron_ingot",
        "create:iron_sheet",
        "",
        "create:andesite_casing",
        "");
    craft(
        level,
        3,
        "create:propeller",
        1,
        "",
        "create:iron_sheet",
        "",
        "create:iron_sheet",
        "create:andesite_alloy",
        "create:iron_sheet",
        "",
        "create:iron_sheet",
        "");
    craft(
        level,
        3,
        "create:empty_blaze_burner",
        1,
        "",
        "create:iron_sheet",
        "",
        "create:iron_sheet",
        "minecraft:netherrack",
        "create:iron_sheet",
        "",
        "create:iron_sheet",
        "");
    craft(
        level,
        2,
        "create:super_glue",
        1,
        "minecraft:slime_ball",
        "create:iron_sheet",
        "minecraft:iron_nugget",
        "minecraft:slime_ball");
    craft(
        level,
        3,
        "create:large_cogwheel",
        1,
        "create:shaft",
        "minecraft:oak_planks",
        "minecraft:oak_planks");
    craft(
        level,
        1,
        "create:mechanical_press",
        1,
        "create:shaft",
        "create:andesite_casing",
        "minecraft:iron_block");
    craft(
        level,
        2,
        "aeronautics:white_envelope",
        4,
        "minecraft:white_wool",
        "minecraft:stick",
        "minecraft:stick",
        "minecraft:white_wool");
    craft(
        level,
        3,
        "simulated:physics_assembler",
        1,
        "",
        "minecraft:lever",
        "",
        "create:andesite_alloy",
        "create:andesite_casing",
        "create:andesite_alloy");
    craft(
        level,
        1,
        "simulated:steering_wheel",
        1,
        "create:large_cogwheel",
        "create:andesite_casing",
        "create:shaft");
    craft(
        level,
        1,
        "aeronautics:propeller_bearing",
        1,
        "minecraft:oak_slab",
        "create:iron_sheet",
        "create:brass_casing");
    craft(
        level,
        3,
        "aeronautics:adjustable_burner",
        1,
        "create:iron_sheet",
        "",
        "create:iron_sheet",
        "create:iron_sheet",
        "minecraft:coal_block",
        "create:iron_sheet",
        "create:andesite_alloy",
        "minecraft:redstone",
        "create:andesite_alloy");
    craft(
        level,
        1,
        "simulated:red_portable_engine",
        1,
        "create:iron_sheet",
        "simulated:engine_assembly",
        "minecraft:blast_furnace");
    sampleNaturalResources(level);
    ShipMachineRegression.start(level);
  }

  private static Item item(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key))
      throw new IllegalStateException("Missing item " + id);
    return BuiltInRegistries.ITEM.get(key);
  }

  private static ItemStack stack(String id) {
    return id.isEmpty() ? ItemStack.EMPTY : new ItemStack(item(id));
  }

  private static void craft(
      ServerLevel level, int width, String output, int count, String... slots) {
    CraftingInput input =
        CraftingInput.of(
            width,
            slots.length / width,
            java.util.Arrays.stream(slots).map(ShipProgressionRegression::stack).toList());
    var recipe =
        level
            .getServer()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, level)
            .orElseThrow(() -> new IllegalStateException("No crafting recipe for " + output));
    ItemStack actual = recipe.value().assemble(input, level.registryAccess());
    if (!actual.is(item(output)) || actual.getCount() != count)
      throw new IllegalStateException("Unexpected crafting result for " + output + ": " + actual);
    if (recipe.value().getRemainingItems(input).stream()
        .anyMatch(remaining -> !remaining.isEmpty()))
      throw new IllegalStateException("Unexpected crafting remainder for " + output);
  }

  private static void smelt(ServerLevel level, String inputId, String outputId) {
    SingleRecipeInput input = new SingleRecipeInput(stack(inputId));
    var recipe =
        level
            .getServer()
            .getRecipeManager()
            .getRecipeFor(RecipeType.SMELTING, input, level)
            .orElseThrow(() -> new IllegalStateException("No smelting recipe for " + inputId));
    ItemStack output = recipe.value().assemble(input, level.registryAccess());
    if (!output.is(item(outputId)) || output.getCount() != 1)
      throw new IllegalStateException("Unexpected smelting result for " + inputId + ": " + output);
  }

  private static void processingData(
      ServerLevel level,
      String recipeId,
      String typeId,
      String outputId,
      int count,
      String... inputIds) {
    var recipe =
        level
            .getServer()
            .getRecipeManager()
            .byKey(ResourceLocation.parse(recipeId))
            .orElseThrow(() -> new IllegalStateException("Missing processing recipe " + recipeId))
            .value();
    ItemStack output = recipe.getResultItem(level.registryAccess());
    if (!ResourceLocation.parse(typeId)
        .equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())))
      throw new IllegalStateException("Unexpected recipe type for " + recipeId);
    if (!output.is(item(outputId)) || output.getCount() != count)
      throw new IllegalStateException(
          "Unexpected processing result for " + recipeId + ": " + output);
    if (recipe.getIngredients().size() != inputIds.length)
      throw new IllegalStateException("Unexpected ingredient count in " + recipeId);
    if (!matchesIngredients(recipe.getIngredients(), inputIds, 0, new boolean[inputIds.length]))
      throw new IllegalStateException("Unexpected ingredients in " + recipeId);
  }

  private static boolean matchesIngredients(
      List<Ingredient> ingredients, String[] inputs, int index, boolean[] used) {
    if (index == inputs.length) return true;
    for (int slot = 0; slot < ingredients.size(); slot++) {
      if (!used[slot] && ingredients.get(slot).test(stack(inputs[index]))) {
        used[slot] = true;
        if (matchesIngredients(ingredients, inputs, index + 1, used)) return true;
        used[slot] = false;
      }
    }
    return false;
  }

  private static void orderedProcessingData(
      ServerLevel level, String recipeId, String outputId, String first, String second) {
    var recipe =
        level
            .getServer()
            .getRecipeManager()
            .byKey(ResourceLocation.parse(recipeId))
            .orElseThrow(() -> new IllegalStateException("Missing processing recipe " + recipeId))
            .value();
    ItemStack output = recipe.getResultItem(level.registryAccess());
    if (!ResourceLocation.parse("create:item_application")
            .equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()))
        || !output.is(item(outputId))
        || output.getCount() != 1
        || recipe.getIngredients().size() != 2
        || !recipe.getIngredients().get(0).test(stack(first))
        || !recipe.getIngredients().get(1).test(stack(second)))
      throw new IllegalStateException("Unexpected item application recipe " + recipeId);
  }

  private static void verifyLoot(ServerLevel level) {
    ServerPlayer player =
        new ServerPlayer(
            level.getServer(),
            level,
            new GameProfile(UUID.fromString("a2b67f8b-ea5a-4cc9-91e3-4a24ebd4e9f1"), "AeroSmoke"),
            ClientInformation.createDefault());
    for (String[] pair :
        List.of(
            new String[] {"minecraft:iron_ore", "minecraft:raw_iron", "minecraft:stone_pickaxe"},
            new String[] {
              "minecraft:copper_ore", "minecraft:raw_copper", "minecraft:stone_pickaxe"
            },
            new String[] {"minecraft:coal_ore", "minecraft:coal", "minecraft:stone_pickaxe"},
            new String[] {"create:zinc_ore", "create:raw_zinc", "minecraft:iron_pickaxe"},
            new String[] {
              "minecraft:redstone_ore", "minecraft:redstone", "minecraft:iron_pickaxe"
            })) {
      ResourceLocation blockId = ResourceLocation.parse(pair[0]);
      if (!BuiltInRegistries.BLOCK.containsKey(blockId))
        throw new IllegalStateException("Missing block " + pair[0]);
      BlockState state = BuiltInRegistries.BLOCK.get(blockId).defaultBlockState();
      BlockPos pos = level.getSharedSpawnPos();
      ItemStack tool = stack(pair[2]);
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
      if (!state.canHarvestBlock(level, pos, player))
        throw new IllegalStateException(pair[2] + " cannot harvest " + pair[0]);
      if (Block.getDrops(state, level, pos, null, player, tool).stream()
          .noneMatch(drop -> drop.is(item(pair[1]))))
        throw new IllegalStateException("No expected drop for " + pair[0]);
    }
  }

  private static void sampleNaturalResources(ServerLevel level) {
    if (level.getSeed() != 1)
      throw new IllegalStateException("Use seed 1 for the natural resource sample");
    ChunkPos center = new ChunkPos(level.getSharedSpawnPos());
    Map<String, Integer> counts = new LinkedHashMap<>();
    Map<String, BlockPos> first = new LinkedHashMap<>();
    BlockPos firstLog = null;
    BlockPos firstWater = null;
    for (String name : List.of("andesite", "coal", "iron", "copper", "zinc", "redstone"))
      counts.put(name, 0);
    BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
    for (int chunkX = center.x - 2; chunkX <= center.x + 2; chunkX++) {
      for (int chunkZ = center.z - 2; chunkZ <= center.z + 2; chunkZ++) {
        var chunk = level.getChunk(chunkX, chunkZ);
        for (int section = 0; section < chunk.getSections().length; section++) {
          if (chunk.getSections()[section].hasOnlyAir()) continue;
          int minY = level.getMinBuildHeight() + section * 16;
          for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
              for (int y = 0; y < 16; y++) {
                position.set(chunkX * 16 + x, minY + y, chunkZ * 16 + z);
                BlockState sampled = chunk.getBlockState(position);
                if (firstLog == null && sampled.is(BlockTags.LOGS)) firstLog = position.immutable();
                if (firstWater == null
                    && sampled.getFluidState().is(FluidTags.WATER)
                    && sampled.getFluidState().isSource()
                    && sampled.getBlock() instanceof BucketPickup)
                  firstWater = position.immutable();
                String id = BuiltInRegistries.BLOCK.getKey(sampled.getBlock()).toString();
                String resource =
                    switch (id) {
                      case "minecraft:andesite" -> "andesite";
                      case "minecraft:coal_ore",
                          "minecraft:deepslate_coal_ore",
                          "natures_spirit:chert_coal_ore" ->
                          "coal";
                      case "minecraft:iron_ore",
                          "minecraft:deepslate_iron_ore",
                          "natures_spirit:chert_iron_ore" ->
                          "iron";
                      case "minecraft:copper_ore",
                          "minecraft:deepslate_copper_ore",
                          "natures_spirit:chert_copper_ore" ->
                          "copper";
                      case "create:zinc_ore", "create:deepslate_zinc_ore" -> "zinc";
                      case "minecraft:redstone_ore",
                          "minecraft:deepslate_redstone_ore",
                          "natures_spirit:chert_redstone_ore" ->
                          "redstone";
                      default -> null;
                    };
                if (resource != null) {
                  counts.put(resource, counts.get(resource) + 1);
                  first.putIfAbsent(resource, position.immutable());
                }
              }
        }
      }
    }
    LogUtils.getLogger()
        .info(
            "Ship progression natural sample: seed={}, center={}, radius=2 chunks, counts={}",
            level.getSeed(),
            center,
            counts);
    for (var entry : counts.entrySet()) {
      if (entry.getValue() == 0)
        throw new IllegalStateException("No " + entry.getKey() + " in the seed-1 spawn sample");
    }
    ServerPlayer player =
        new ServerPlayer(
            level.getServer(),
            level,
            new GameProfile(UUID.fromString("a2b67f8b-ea5a-4cc9-91e3-4a24ebd4e9f1"), "AeroSmoke"),
            ClientInformation.createDefault());
    if (firstLog != null) {
      BlockState log = level.getBlockState(firstLog);
      ItemStack hand = ItemStack.EMPTY;
      if (!log.canHarvestBlock(level, firstLog, player)
          || Block.getDrops(log, level, firstLog, null, player, hand).stream()
              .noneMatch(drop -> drop.is(log.getBlock().asItem())))
        throw new IllegalStateException("Natural log cannot be harvested by hand at " + firstLog);
    }
    if (firstWater != null) {
      BlockState water = level.getBlockState(firstWater);
      ItemStack filled =
          ((BucketPickup) water.getBlock()).pickupBlock(player, level, firstWater, water);
      if (!filled.is(item("minecraft:water_bucket")))
        throw new IllegalStateException("Natural water cannot fill a bucket at " + firstWater);
    }
    LogUtils.getLogger()
        .info("Ship progression natural extras: log={}, water source={}", firstLog, firstWater);
    if (firstLog == null || firstWater == null)
      throw new IllegalStateException(
          "Seed-1 spawn sample (5x5 chunks) lacks a natural log or source of water");
    for (var entry : first.entrySet()) {
      String resource = entry.getKey();
      String toolId =
          resource.equals("zinc") || resource.equals("redstone")
              ? "minecraft:iron_pickaxe"
              : "minecraft:stone_pickaxe";
      String expected =
          switch (resource) {
            case "andesite" -> "minecraft:andesite";
            case "coal" -> "minecraft:coal";
            case "iron" -> "minecraft:raw_iron";
            case "copper" -> "minecraft:raw_copper";
            case "zinc" -> "create:raw_zinc";
            case "redstone" -> "minecraft:redstone";
            default -> throw new IllegalStateException("Unexpected sampled resource " + resource);
          };
      BlockPos pos = entry.getValue();
      BlockState state = level.getBlockState(pos);
      ItemStack tool = stack(toolId);
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
      if (!state.canHarvestBlock(level, pos, player))
        throw new IllegalStateException(
            toolId + " cannot harvest natural " + resource + " at " + pos);
      if (Block.getDrops(state, level, pos, null, player, tool).stream()
          .noneMatch(drop -> drop.is(item(expected))))
        throw new IllegalStateException("No expected drop for natural " + resource + " at " + pos);
    }
  }
}
