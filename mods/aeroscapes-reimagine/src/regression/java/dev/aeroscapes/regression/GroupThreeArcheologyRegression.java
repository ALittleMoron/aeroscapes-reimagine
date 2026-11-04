package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.Pandarix.block.entity.ArcheologyTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import tictim.paraglider.api.movement.Movement;
import tictim.paraglider.api.stamina.Stamina;
import tictim.paraglider.impl.movement.ServerPlayerMovement;

/** Loaded workbench, archeology-table, reward and movement integration contracts. */
public final class GroupThreeArcheologyRegression {
  private static final List<String> FOSSILS =
      List.of("chicken", "creeper", "guardian", "ocelot", "sheep", "villager", "wolf");
  private static final Map<String, String> FOSSIL_SITES =
      Map.of(
          "fossil_chicken", "chicken",
          "fossil_chicken_birch", "chicken",
          "fossil_creeper", "creeper",
          "fossil_jungle_0", "jungle",
          "fossil_jungle_1", "jungle",
          "fossil_sheep_0", "sheep",
          "fossil_wolf", "wolf");

  private GroupThreeArcheologyRegression() {}

  static JsonArray naturalFinds(ServerLevel level, ResourceLocation id, StructureStart start) {
    JsonArray deposits = new JsonArray();
    String fossil = FOSSIL_SITES.get(id.getPath());
    if (!id.getNamespace().equals("betterarcheology") || fossil == null) return deposits;
    var block =
        BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.parse("betterarcheology:fossiliferous_dirt"))
            .orElseThrow();
    require(
        !block.defaultBlockState().is(BlockTags.DIRT)
            && !block.defaultBlockState().is(BlockTags.LUSH_GROUND_REPLACEABLE),
        "Fossil deposits remain eligible for soil replacement");
    String expectedLoot = "betterarcheology:archeology/fossiliferous_dirt_" + fossil;
    for (var piece : start.getPieces()) {
      require(
          piece instanceof PoolElementStructurePiece,
          "Re-audit non-jigsaw fossil excavation: " + id);
      var pool = (PoolElementStructurePiece) piece;
      require(pool.getElement() instanceof SinglePoolElement, "Re-audit fossil pool: " + id);
      var single = (SinglePoolElement) pool.getElement();
      var template = single.template.map(level.getStructureManager()::getOrCreate, value -> value);
      require(template.palettes.size() == 1, "Re-audit multiple fossil palettes: " + id);
      var settings = new StructurePlaceSettings().setRotation(pool.getRotation());
      for (var info : template.palettes.getFirst().blocks()) {
        if (!info.state().is(block)) continue;
        BlockPos position =
            StructureTemplate.calculateRelativePosition(settings, info.pos())
                .offset(pool.getPosition());
        require(
            level.getBlockState(position).is(block),
            "Natural FULL fossil deposit overwritten: " + id + " at " + position);
        var entity = level.getBlockEntity(position);
        require(
            entity instanceof BrushableBlockEntity,
            "Natural FULL fossil deposit lost its brushable block entity: " + position);
        var data = entity.saveWithoutMetadata(level.registryAccess());
        require(
            expectedLoot.equals(data.getString("LootTable")),
            "Natural FULL fossil deposit lost its find table: " + position);
        JsonObject deposit = new JsonObject();
        deposit.addProperty("x", position.getX());
        deposit.addProperty("y", position.getY());
        deposit.addProperty("z", position.getZ());
        deposit.addProperty("loot_table", data.getString("LootTable"));
        deposits.add(deposit);
      }
    }
    require(!deposits.isEmpty(), "Natural fossil excavation contains no deposits: " + id);
    return deposits;
  }

  public static void run(ServerLevel level, Path output) {
    crafting(level);
    JsonObject report = new JsonObject();
    report.addProperty("seed", level.getSeed());
    report.addProperty("native_crafting_and_remainders", true);
    report.add("fossil_rewards", fossilRewards(level));
    report.add("localized_sword_rewards", swordRewards(level));
    BlockPos position = level.getSharedSpawnPos().above(32);
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("256a9b30-64e2-46fd-8b21-e3b8055daa69"),
            "ArcheologyFixture",
            Vec3.atCenterOf(position),
            null)) {
      report.add("native_identifying", identify(level, position));
      report.add("native_mobility", mobility(level, session.player));
    }
    try {
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write archeology fixture evidence", failure);
    }
  }

  private static void crafting(ServerLevel level) {
    craft(
        level,
        1,
        3,
        List.of(
            new ItemStack(Items.FEATHER),
            new ItemStack(Items.IRON_INGOT),
            new ItemStack(Items.STICK)),
        "iron_brush");
    craft(
        level,
        1,
        3,
        List.of(
            new ItemStack(Items.FEATHER), new ItemStack(Items.DIAMOND), new ItemStack(Items.STICK)),
        "diamond_brush");
    var smithing =
        new SmithingRecipeInput(
            new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
            stack("diamond_brush"),
            new ItemStack(Items.NETHERITE_INGOT));
    var upgrade =
        level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, smithing, level).orElseThrow();
    require(
        upgrade.value().assemble(smithing, level.registryAccess()).is(item("netherite_brush")),
        "Native smithing upgrade failed");
    craft(
        level,
        3,
        3,
        java.util.stream.IntStream.range(0, 9)
            .mapToObj(ignored -> stack("artifact_shards"))
            .toList(),
        "unidentified_artifact");
    craft(
        level,
        2,
        3,
        List.of(
            new ItemStack(Items.BRUSH),
            stack("artifact_shards"),
            new ItemStack(Items.OAK_PLANKS),
            new ItemStack(Items.OAK_PLANKS),
            new ItemStack(Items.OAK_PLANKS),
            new ItemStack(Items.OAK_PLANKS)),
        "archeology_table");
    for (String fossil : FOSSILS)
      craft(
          level,
          2,
          1,
          List.of(stack(fossil + "_fossil_head"), stack(fossil + "_fossil_body")),
          fossil + "_fossil");
  }

  private static void craft(
      ServerLevel level, int width, int height, List<ItemStack> ingredients, String expected) {
    var input = CraftingInput.of(width, height, ingredients);
    var recipe =
        level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
    ItemStack result = recipe.value().assemble(input, level.registryAccess());
    require(
        result.is(item(expected)) && result.getCount() == 1, "Native recipe failed: " + expected);
    require(
        recipe.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
        "Unexpected native recipe remainder: " + expected);
  }

  private static JsonObject identify(ServerLevel level, BlockPos position) {
    var original = level.getBlockState(position);
    require(original.isAir(), "Disposable identifying-table position must be air");
    var state =
        BuiltInRegistries.BLOCK
            .get(ResourceLocation.parse("betterarcheology:archeology_table"))
            .defaultBlockState();
    try {
      level.setBlock(position, state, 3);
      require(
          level.getBlockEntity(position) instanceof ArcheologyTableBlockEntity,
          "Native table block entity missing");
      var table = (ArcheologyTableBlockEntity) level.getBlockEntity(position);
      require(table != null, "Missing table");
      ItemStack brush = stack("iron_brush");
      table.setItem(0, brush);
      table.setItem(1, stack("unidentified_artifact"));
      var input = new SingleRecipeInput(stack("unidentified_artifact"));
      Set<String> offered = new HashSet<>();
      for (var recipe :
          level
              .getRecipeManager()
              .getRecipesFor(net.Pandarix.recipe.IdentifyingRecipe.Type.INSTANCE, input, level)) {
        var result = recipe.value().assemble(input, level.registryAccess());
        require(
            result.is(Items.ENCHANTED_BOOK)
                && result.get(DataComponents.ITEM_NAME) != null
                && result.get(DataComponents.ITEM_NAME).getContents()
                    instanceof TranslatableContents,
            "Identified artifact lost its localized item name");
        ItemEnchantments enchantments =
            result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        require(enchantments.size() == 1, "Unexpected identified-artifact enchantments");
        for (var entry : enchantments.entrySet()) {
          require(entry.getIntValue() == 1, "Unexpected identified-artifact enchantment level");
          offered.add(entry.getKey().unwrapKey().orElseThrow().location().toString());
        }
      }
      require(
          offered.equals(
              Set.of(
                  "betterarcheology:penetrating_strike",
                  "betterarcheology:soaring_winds",
                  "betterarcheology:tunneling")),
          "Native identifying recipe selection changed");
      for (int tick = 0; tick < 80; tick++)
        ArcheologyTableBlockEntity.tick(level, position, level.getBlockState(position), table);
      require(
          table.getItem(1).isEmpty()
              && table.getItem(2).is(Items.ENCHANTED_BOOK)
              && table.getItem(2).getCount() == 1
              && brush.getDamageValue() == 1,
          "Actual native table did not consume one artifact and one brush use");
      JsonObject result = new JsonObject();
      JsonArray rewards = new JsonArray();
      offered.stream().sorted().forEach(rewards::add);
      result.add("three_native_outputs", rewards);
      result.addProperty("consumed_artifacts", 1);
      result.addProperty("brush_damage", brush.getDamageValue());
      table.clearContent();
      return result;
    } finally {
      level.setBlock(position, original, 3);
    }
  }

  private static JsonArray fossilRewards(ServerLevel level) {
    JsonArray result = new JsonArray();
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(level.getSharedSpawnPos()))
            .create(LootContextParamSets.ARCHAEOLOGY);
    for (String fossil : FOSSILS) {
      String tableFossil = fossil.equals("ocelot") ? "jungle" : fossil;
      var table =
          level
              .getServer()
              .reloadableRegistries()
              .getLootTable(
                  ResourceKey.create(
                      Registries.LOOT_TABLE,
                      ResourceLocation.parse(
                          "betterarcheology:archeology/fossiliferous_dirt_" + tableFossil)));
      Set<Item> found = new HashSet<>();
      for (int seed = 0; seed < 256; seed++)
        for (ItemStack drop : table.getRandomItems(params, seed)) found.add(drop.getItem());
      require(
          found.contains(item(fossil + "_fossil_head"))
              && found.contains(item(fossil + "_fossil_body")),
          "Missing obtainable fossil parts: " + fossil);
      result.add(fossil);
    }
    return result;
  }

  private static JsonObject swordRewards(ServerLevel level) {
    var table =
        level
            .getServer()
            .reloadableRegistries()
            .getLootTable(
                ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ResourceLocation.parse("betterarcheology:archeology/sword_loot")));
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(level.getSharedSpawnPos()))
            .create(LootContextParamSets.CHEST);
    Set<String> found = new HashSet<>();
    for (int seed = 0; seed < 256; seed++)
      for (ItemStack drop : table.getRandomItems(params, seed)) {
        var name = drop.get(DataComponents.CUSTOM_NAME);
        require(name != null, "Named archaeology sword lost its name");
        if (name.getContents() instanceof TranslatableContents translated)
          found.add(translated.getKey());
        var lore = drop.get(DataComponents.LORE);
        require(lore != null && lore.lines().size() == 1, "Native sword lore changed");
        require(
            lore.lines().getFirst().getContents() instanceof TranslatableContents,
            "Archaeology sword lore remains a literal string");
      }
    require(found.size() == 6, "Not all six localized sword names were observed");
    JsonObject result = new JsonObject();
    result.addProperty("localized_names", found.size());
    result.addProperty("stylized_enchanting_script_name_preserved", true);
    result.addProperty("all_sampled_lore_translatable", true);
    return result;
  }

  private static JsonObject mobility(ServerLevel level, ServerPlayer player) {
    var enchantment =
        level
            .registryAccess()
            .registryOrThrow(Registries.ENCHANTMENT)
            .getHolderOrThrow(
                ResourceKey.create(
                    Registries.ENCHANTMENT,
                    ResourceLocation.parse("betterarcheology:soaring_winds")));
    ItemStack glider =
        new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("paraglider:paraglider")));
    require(
        !glider.supportsEnchantment(enchantment),
        "Handheld paraglider unexpectedly accepts Soaring Winds");
    player.setItemInHand(InteractionHand.MAIN_HAND, glider);
    ServerPlayerMovement movement = (ServerPlayerMovement) Movement.get(player);
    player.setOnGround(true);
    movement.update();
    player.setOnGround(false);
    player.setPos(player.getX(), player.getY() - 2.0, player.getZ());
    Stamina.get(player).setStamina(500.0);
    movement.update();
    require(movement.state().paragliding(), "Native handheld gliding did not start");
    player.setDeltaMovement(Vec3.ZERO);
    player.setXRot(-90);
    player.startFallFlying();
    require(player.getDeltaMovement().lengthSqr() == 0, "Soaring Winds boosted handheld gliding");
    player.stopFallFlying();
    ItemStack totem = stack("torrent_totem");
    player.setItemInHand(InteractionHand.OFF_HAND, totem);
    var whileGliding = player.gameMode.useItem(player, level, totem, InteractionHand.OFF_HAND);
    require(
        !whileGliding.consumesAction()
            && totem.getDamageValue() == 0
            && !player.getCooldowns().isOnCooldown(totem.getItem())
            && player.getDeltaMovement().lengthSqr() == 0,
        "Native gliding no longer blocks offhand item use without spending the totem");
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    movement.update();
    require(!movement.state().paragliding(), "Stowing the handheld glider did not close it");
    require(
        player.gameMode.useItem(player, level, totem, InteractionHand.OFF_HAND).consumesAction(),
        "Native midair torrent totem use was rejected after stowing the glider");
    Vec3 impulse = player.getDeltaMovement();
    require(
        impulse.y > 2.0
            && Math.abs(impulse.x) < 0.001
            && Math.abs(impulse.z) < 0.001
            && totem.getDamageValue() == 1
            && totem.getMaxDamage() == 32
            && player.getCooldowns().isOnCooldown(totem.getItem()),
        "Native upward totem impulse, durability or cooldown changed");
    player.gameMode.useItem(player, level, totem, InteractionHand.OFF_HAND);
    require(totem.getDamageValue() == 1, "Totem bypassed its native cooldown");
    player.setItemInHand(InteractionHand.MAIN_HAND, glider);
    // Controlled controller inputs establish redeployment, not a measured flight trajectory.
    player.setPos(player.getX(), player.getY() + 2.0, player.getZ());
    movement.update();
    require(!movement.state().paragliding(), "Glider opened before the upward sample ended");
    player.setPos(player.getX(), player.getY() - 2.0, player.getZ());
    movement.update();
    require(
        movement.state().paragliding() && movement.staminaDelta() < 0,
        "Native gliding did not resume on descent after the torrent dash");
    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    ItemStack elytra = new ItemStack(Items.ELYTRA);
    elytra.enchant(enchantment, 1);
    player.setItemSlot(EquipmentSlot.CHEST, elytra);
    player.setDeltaMovement(Vec3.ZERO);
    player.startFallFlying();
    require(player.getDeltaMovement().y > 0, "Native enchanted elytra startup boost did not run");
    player.stopFallFlying();
    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
    JsonObject result = new JsonObject();
    result.addProperty("active_gliding_blocks_offhand_totem", true);
    result.addProperty("active_gliding_interaction_result", whileGliding.toString());
    result.addProperty("stowed_glider_allows_midair_upward_torrent", true);
    result.addProperty("glider_redeploys_after_controlled_ascent_and_descent", true);
    result.addProperty("native_upward_impulse_y", impulse.y);
    result.addProperty("totem_uses", totem.getMaxDamage());
    result.addProperty("cooldown_rejects_second_use", true);
    result.addProperty("soaring_winds_affects_elytra_only", true);
    result.addProperty("trajectory_and_client_controls_pending", true);
    return result;
  }

  private static ItemStack stack(String path) {
    return new ItemStack(item(path));
  }

  private static Item item(String path) {
    return BuiltInRegistries.ITEM
        .getOptional(ResourceLocation.fromNamespaceAndPath("betterarcheology", path))
        .orElseThrow();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
