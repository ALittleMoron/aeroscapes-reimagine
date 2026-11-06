package dev.aeroscapes.regression;

import cc.abbie.emi_ores.mixin.accessor.HeightRangePlacementAccessor;
import cc.abbie.emi_ores.mixin.accessor.RarityFilterAccessor;
import cc.abbie.emi_ores.mixin.accessor.UniformHeightAccessor;
import cc.abbie.emi_ores.networking.FeaturesSender;
import cc.abbie.emi_ores.networking.payload.S2CSendFeaturesPayload;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.ordana.immersive_weathering.events.ModEvents;
import com.ordana.immersive_weathering.reg.ModWaxables;
import dev.aeroscapes.worldgen.BlackstoneOres;
import dev.aeroscapes.worldgen.OreInformation;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.mehvahdjukaar.moonlight.api.events.AfterLanguageLoadEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Loaded generation information, native waxing interactions, loot and saved provider blocks. */
public final class MiscContentRegression {
  private static final BlockPos ORIGIN = new BlockPos(8, 310, 8);

  private MiscContentRegression() {}

  public static void verify(ServerLevel level, Path output, boolean reload) {
    JsonObject report = new JsonObject();
    report.add("weathering_catalog", WeatheringCatalogRegression.verify(level));
    languageDefaults();
    var snapshot = OreInformation.snapshot(level.getServer());
    require(!snapshot.features().isEmpty(), "Active ore information exists");
    for (var id : snapshot.features().keySet())
      require(
          snapshot.biomes().keySet().stream().anyMatch(key -> key.location().equals(id)),
          "Every display feature has active biome membership: " + id);
    Set<String> blackstone = new HashSet<>();
    snapshot
        .features()
        .values()
        .forEach(
            feature -> {
              if (feature.feature().value().config() instanceof OreConfiguration ore)
                ore.targetStates.forEach(
                    target -> {
                      var id = BuiltInRegistries.BLOCK.getKey(target.state.getBlock());
                      if (id.getNamespace().equals("aeroscapes_reimagine"))
                        blackstone.add(id.getPath());
                    });
            });
    for (String material : BlackstoneOres.MATERIALS)
      require(blackstone.contains("blackstone_" + material + "_ore"), "Blackstone " + material);
    var lower =
        snapshot.features().entrySet().stream()
            .filter(entry -> entry.getKey().getPath().startsWith("lower/"))
            .toList();
    require(lower.size() >= 18, "All lower ore placement roots remain active");
    for (var entry : lower) {
      var range =
          entry.getValue().placement().stream()
              .filter(HeightRangePlacement.class::isInstance)
              .map(HeightRangePlacement.class::cast)
              .findFirst()
              .orElseThrow();
      var height = ((HeightRangePlacementAccessor) range).getHeight();
      require(height instanceof UniformHeight, "Uniform native lower height distribution");
      var uniform = (UniformHeightAccessor) height;
      require(
          uniform.getMinInclusive() instanceof VerticalAnchor.Absolute minimum
              && minimum.y() == -112
              && uniform.getMaxInclusive() instanceof VerticalAnchor.Absolute maximum
              && maximum.y() == -16,
          "Loaded lower height range");
    }
    for (var branch : List.of("choice_0", "default")) {
      var id =
          ResourceLocation.parse(
              "aeroscapes_reimagine:emi/selector/minecraft/amethyst_geode/" + branch);
      var feature = snapshot.features().get(id);
      require(feature != null, "Actual amethyst selector leaf " + branch);
      var rarity =
          feature.placement().stream()
              .filter(RarityFilter.class::isInstance)
              .map(RarityFilter.class::cast)
              .findFirst()
              .orElseThrow();
      require(
          ((RarityFilterAccessor) rarity).getChance() == (branch.equals("default") ? 20 : 180),
          "Selector probability remains part of attempt frequency");
    }
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      S2CSendFeaturesPayload.CODEC.encode(buffer, new S2CSendFeaturesPayload(snapshot.features()));
      var decoded = S2CSendFeaturesPayload.CODEC.decode(buffer);
      require(
          decoded.features().keySet().equals(snapshot.features().keySet()), "Native wire codec");
    } finally {
      buffer.release();
    }
    ItemStack craftedWool;
    // NeoForge deliberately suppresses advancement awards for FakePlayer.
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("6220d3cc-4b3b-4755-b87b-62b722404a2b"),
            "AeroWool",
            Vec3.atCenterOf(ORIGIN),
            null)) {
      craftedWool = steelWoolRecipe(level, session.player);
    }
    var player = FakePlayerFactory.getMinecraft(level);
    report.addProperty("steel_wool_crafting", "four_nuggets_inventory_and_workbench");
    FeaturesSender.onSyncDataPackContents(
        player,
        (ignoredPlayer, ignoredType) -> false,
        (ignoredPlayer, ignoredPacket) -> {
          throw new IllegalStateException("Optional channel denial must send nothing");
        });
    var channel = new EmbeddedChannel(player.connection.getConnection());
    try {
      FeaturesSender.onSyncDataPackContents(
          player,
          (ignoredPlayer, ignoredType) -> true,
          (ignoredPlayer, ignoredPacket) -> {
            throw new IllegalStateException(
                "Unnegotiated native client channels must send nothing");
          });
    } finally {
      channel.finishAndReleaseAll();
    }
    int waxPairs = 0;
    for (var entry : ModWaxables.getValues().entrySet()) {
      var original = entry.getKey().defaultBlockState();
      var waxed = HoneycombItem.getWaxed(original).orElseThrow();
      require(waxed.is(entry.getValue()), "Native iron wax data map");
      require(ModWaxables.getUnWaxed(waxed).orElseThrow().equals(original), "Safe iron unwaxing");
      waxPairs++;
    }
    require(waxPairs == 36, "Complete native waxable iron family");
    require(
        ModWaxables.getUnWaxed(Blocks.WAXED_COPPER_BLOCK.defaultBlockState())
            .orElseThrow()
            .is(Blocks.COPPER_BLOCK),
        "Vanilla copper unwaxing remains available");
    var plate =
        BuiltInRegistries.BLOCK.get(ResourceLocation.parse("immersive_weathering:plate_iron"));
    var rusted =
        BuiltInRegistries.BLOCK.get(
            ResourceLocation.parse("immersive_weathering:rusted_plate_iron"));
    require(plate != Blocks.AIR && rusted != Blocks.AIR, "Registered weathering content");
    if (!reload) {
      level.setBlockAndUpdate(ORIGIN, plate.defaultBlockState());
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEYCOMB, 2));
      var hit = new BlockHitResult(Vec3.atCenterOf(ORIGIN), Direction.UP, ORIGIN, false);
      var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
      require(
          player.getMainHandItem().useOn(context).consumesAction(), "Actual honeycomb interaction");
      require(player.getMainHandItem().getCount() == 1, "Wax protection consumes one honeycomb");
      var wool = craftedWool;
      player.setItemInHand(InteractionHand.MAIN_HAND, wool);
      require(
          ModEvents.onBlockCLicked(wool, player, level, InteractionHand.MAIN_HAND, hit)
              .consumesAction(),
          "Actual steel-wool interaction");
      require(level.getBlockState(ORIGIN).is(plate), "Steel wool safely removes iron wax");
      require(wool.getDamageValue() == 1, "Unwaxing charges native tool durability");
      level.setBlockAndUpdate(ORIGIN.above(), rusted.defaultBlockState());
    } else {
      require(level.getBlockState(ORIGIN).is(plate), "Saved unwaxed iron survives reload");
      require(level.getBlockState(ORIGIN.above()).is(rusted), "Saved rusted iron survives reload");
    }
    for (var type : List.of(EntityType.BLAZE, EntityType.PIG, EntityType.HOGLIN)) {
      var entity = type.create(level);
      require(entity != null, "Native loot-context entity");
      var parameters =
          new LootParams.Builder(level)
              .withParameter(LootContextParams.THIS_ENTITY, entity)
              .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(ORIGIN))
              .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
              .create(LootContextParamSets.ENTITY);
      var table = level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable());
      Set<String> drops = new HashSet<>();
      for (int attempt = 0; attempt < 32; attempt++)
        table
            .getRandomItems(parameters, (attempt + 1L) * 0x9e3779b97f4a7c15L)
            .forEach(stack -> drops.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
      require(
          drops.contains("immersive_weathering:" + (type == EntityType.BLAZE ? "soot" : "tallow")),
          "Actual entity table includes native weathering loot: " + type);
    }
    var toolsmith = EntityType.VILLAGER.create(level);
    require(toolsmith != null, "Native toolsmith gift context");
    toolsmith.setVillagerData(
        toolsmith.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
    var giftParameters =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, toolsmith)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(ORIGIN))
            .create(LootContextParamSets.GIFT);
    var gifts =
        level
            .getServer()
            .reloadableRegistries()
            .getLootTable(
                ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ResourceLocation.withDefaultNamespace(
                        "gameplay/hero_of_the_village/toolsmith_gift")));
    Set<String> giftItems = new HashSet<>();
    for (int attempt = 1; attempt <= 32; attempt++)
      gifts
          .getRandomItems(giftParameters, attempt * 0x9e3779b97f4a7c15L)
          .forEach(
              stack -> giftItems.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    require(
        giftItems.contains("immersive_weathering:steel_wool"),
        "Native toolsmith gift supplies steel wool");
    report.addProperty("display_features", snapshot.features().size());
    report.addProperty("lower_placements", lower.size());
    report.addProperty("blackstone_outputs", blackstone.size());
    report.addProperty("wax_pairs", waxPairs);
    report.addProperty("reload", reload);
    try {
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot write miscellaneous integration evidence", exception);
    }
  }

  private static ItemStack steelWoolRecipe(
      ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
    var id = ResourceLocation.parse("aeroscapes_reimagine:steel_wool");
    var wool =
        BuiltInRegistries.ITEM.get(ResourceLocation.parse("immersive_weathering:steel_wool"));
    var menu = player.inventoryMenu;
    menu.clearCraftingContent();
    for (int slot = 0; slot < 4; slot++)
      menu.getCraftSlots().setItem(slot, new ItemStack(Items.IRON_NUGGET, 2));
    var input = menu.getCraftSlots().asCraftInput();
    var matches = level.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, level);
    require(
        matches.size() == 1 && matches.getFirst().id().equals(id),
        "Exactly one loaded recipe matches four iron nuggets");
    var recipe = matches.getFirst().value();
    var result = recipe.assemble(input, level.registryAccess());
    require(
        result.is(wool)
            && result.getCount() == 1
            && result.getDamageValue() == 0
            && result.getMaxDamage() == 128,
        "Crafting supplies one fresh native steel-wool tool");
    require(
        recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
        "Iron nuggets have no crafting remainders");
    var advancement =
        level
            .getServer()
            .getAdvancements()
            .get(ResourceLocation.parse("aeroscapes_reimagine:recipes/tools/steel_wool"));
    require(advancement != null, "Steel-wool recipe unlock advancement loads");
    player.getAdvancements().revoke(advancement, "has_iron_nugget");
    player.getAdvancements().revoke(advancement, "has_the_recipe");
    player.resetRecipes(List.of(matches.getFirst()));
    require(
        !player.getRecipeBook().contains(matches.getFirst()),
        "Recipe is unknown before iron acquisition");
    var nugget = new ItemStack(Items.IRON_NUGGET);
    player.getInventory().setItem(0, nugget);
    net.minecraft.advancements.CriteriaTriggers.INVENTORY_CHANGED.trigger(
        player, player.getInventory(), nugget);
    require(
        player.getRecipeBook().contains(matches.getFirst()),
        "Actual iron acquisition unlocks steel wool before the first craft");
    player.getInventory().setItem(0, ItemStack.EMPTY);
    var output = menu.getSlot(menu.getResultSlotIndex());
    require(
        ItemStack.isSameItemSameComponents(output.getItem(), result),
        "The real inventory result slot exposes steel wool");
    var taken = output.remove(1);
    output.onTake(player, taken);
    for (int slot = 0; slot < 4; slot++)
      require(
          menu.getCraftSlots().getItem(slot).is(Items.IRON_NUGGET)
              && menu.getCraftSlots().getItem(slot).getCount() == 1,
          "One real craft consumes exactly one nugget from each slot");
    menu.clearCraftingContent();
    for (int count : new int[] {3, 4, 5}) {
      List<ItemStack> ingredients = new ArrayList<>();
      for (int slot = 0; slot < 9; slot++)
        ingredients.add(
            slot % 2 == 0 && slot / 2 < count ? new ItemStack(Items.IRON_NUGGET) : ItemStack.EMPTY);
      var workbench = CraftingInput.of(3, 3, ingredients);
      var workbenchMatches =
          level.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, workbench, level);
      require(
          workbenchMatches.stream().anyMatch(holder -> holder.id().equals(id)) == (count == 4),
          "Shapeless workbench recipe requires exactly four separate nuggets: " + count);
      if (count == 4)
        require(
            workbenchMatches.size() == 1,
            "No tag-based or generated recipe competes on the workbench");
    }
    return taken;
  }

  private static void languageDefaults() {
    String caption = "immersive_weathering.configuration.general.creative_tab";
    String custom = "immersive_weathering.configuration.general.block_growths";
    Map<String, String> lines = new HashMap<>(Map.of(custom, "Owner's custom caption"));
    var event = new AfterLanguageLoadEvent(lines, List.of("en_us", "ru_ru"));
    require(lines.get(caption).equals("Отдельная вкладка творчества"), "Russian before defaults");
    require(lines.get(custom).equals("Owner's custom caption"), "Player caption before defaults");
    event.addEntry(caption, "Generated English default");
    event.addEntry(custom, "Another generated default");
    require(
        lines.get(caption).equals("Отдельная вкладка творчества"),
        "Generated caption stays Russian");
    require(
        !event.getExtraLanguageLines().containsKey(caption)
            && !event.getExtraLanguageLines().containsKey(custom),
        "No generated component shadows an existing translation");
    String missing = "aeroscapes_reimagine.regression.generated_caption";
    event.addEntry(missing, "Native generated control");
    require(
        event.getExtraLanguageLines().get(missing).equals("Native generated control"),
        "Other native dynamic captions retain their component path");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
