package dev.aeroscapes.regression;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.travel.TravelIntegration;
import dev.aeroscapes.travel.WaystoneFees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.blay09.mods.waystones.core.FleetingMemorialManager;
import net.blay09.mods.waystones.item.ModItems;
import net.blay09.mods.waystones.requirement.ExperiencePointsRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Actual loot modifiers, death callback and requirement tooltip payloads. */
public final class WaystoneFeedbackRegression {
  private WaystoneFeedbackRegression() {}

  public static void verify(ServerLevel level) {
    WaystonesRegression.verify(level);
    balances(level);
    loot(level);
    disabledItems(level);
  }

  private static void balances(ServerLevel level) {
    int[] costs = {100, 120, 140, 360};
    int[] levels = {7, 8, 9, 16};
    int[] progress = {9, 8, 5, 8};
    for (int index = 0; index < costs.length; index++) {
      int floor = WaystoneFees.levelAtPoints(costs[index], costs[index]);
      require(
          floor == levels[index]
              && costs[index] - WaystoneFees.cumulativePoints(floor) == progress[index],
          "Incorrect exact minimum experience balance");
    }
    for (int playerLevel = 0; playerLevel <= 60; playerLevel++) {
      long total = WaystoneFees.cumulativePoints(playerLevel);
      require(
          WaystoneFees.levelAtPoints(total, playerLevel) == playerLevel, "Whole-level boundary");
      if (playerLevel > 0)
        require(
            WaystoneFees.levelAtPoints(total - 1, playerLevel) == playerLevel - 1,
            "One point below a whole-level boundary");
    }
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("332204d5-f636-4e66-b29d-9c721d907a16"),
            "WaystoneCostTooltip",
            Vec3.atCenterOf(level.getSharedSpawnPos()),
            null)) {
      var player = session.player;
      player.setGameMode(GameType.SURVIVAL);
      player.experienceLevel = 8;
      player.experienceProgress = 8f / player.getXpNeededForNextLevel();
      player.totalExperience = 99999;
      List<Component> text = new ArrayList<>();
      new ExperiencePointsRequirement(120).appendHoverText(player, text);
      require(text.size() == 3, "Native requirement did not use the exact tooltip");
      assertArguments(text.get(1), 8, 8L);
      assertArguments(text.get(2), 0, 0L);
      player.experienceProgress = 7f / player.getXpNeededForNextLevel();
      text.clear();
      new ExperiencePointsRequirement(120).appendHoverText(player, text);
      assertArguments(text.get(2), 1L);
    }
    LogUtils.getLogger().info("Waystone exact-point tooltip regression passed");
  }

  private static void assertArguments(Component component, Object... expected) {
    require(component.getContents() instanceof TranslatableContents, "Unlocalized tooltip");
    Object[] actual = ((TranslatableContents) component.getContents()).getArgs();
    require(java.util.Arrays.equals(actual, expected), "Tooltip experience values disagree");
  }

  private static void loot(ServerLevel level) {
    registeredLoot(level);
    BlockPos origin = level.getSharedSpawnPos().offset(16, 20, 16);
    var original = level.getBlockState(origin);
    require(level.getBlockEntity(origin) == null, "Do not replace a saved container");
    List<String> positive =
        List.of(
            "minecraft:chests/simple_dungeon",
            "nova_structures:chests/badland_miner_outpost",
            "philipsruins:chest/ancient_ruins",
            "galosphere:chests/pink_salt_shrine",
            "terralith:desert_outpost",
            "supplementaries:loot/galleon/chest",
            "minecraft:chests/trial_chambers/reward",
            "minecraft:chests/illager_mansion/generic",
            "nova_structures:chests/piglin_outstation/outstation_treasure");
    RandomSource random = RandomSource.create(829375429L);
    try {
      level.setBlockAndUpdate(origin, Blocks.CHEST.defaultBlockState());
      for (String table : positive) {
        int successes = 0;
        for (int attempt = 0; attempt < 1000; attempt++) {
          int count = sigils(level, origin, table, random.nextLong());
          require(count <= 1, "A nested chest table received multiple independent sigil rolls");
          successes += count;
        }
        require(
            successes >= 180 && successes <= 320,
            "Incorrect real sigil chance: " + table + "=" + successes);
        LogUtils.getLogger().info("Waystone sigil sample {}: {}/1000", table, successes);
      }
      for (String table :
          List.of(
              "minecraft:chests/village/village_toolsmith", "minecraft:chests/spawn_bonus_chest")) {
        for (int attempt = 0; attempt < 100; attempt++) {
          require(
              sigils(level, origin, table, random.nextLong()) == 0,
              "Excluded ordinary table received a sigil");
        }
      }
      level.setBlockAndUpdate(origin, Blocks.VAULT.defaultBlockState());
      for (String shared :
          List.of(
              "minecraft:chests/trial_chambers/reward",
              "nova_structures:chests/piglin_outstation/outstation_treasure")) {
        for (int attempt = 0; attempt < 100; attempt++) {
          require(
              sigils(level, origin, shared, random.nextLong()) == 0,
              "Shared vault reward received a sigil");
        }
      }
    } finally {
      level.setBlockAndUpdate(origin, original);
    }
  }

  private static void registeredLoot(ServerLevel level) {
    var resource =
        level
            .getServer()
            .getResourceManager()
            .getResource(
                ResourceLocation.parse("aeroscapes_reimagine:loot_modifiers/waystone_sigils.json"))
            .orElseThrow();
    try (var reader = resource.openAsReader()) {
      var terms =
          JsonParser.parseReader(reader)
              .getAsJsonObject()
              .getAsJsonArray("conditions")
              .get(0)
              .getAsJsonObject()
              .getAsJsonArray("terms");
      for (var term : terms) {
        var id = ResourceLocation.parse(term.getAsJsonObject().get("loot_table_id").getAsString());
        require(
            level
                    .getServer()
                    .reloadableRegistries()
                    .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id))
                != LootTable.EMPTY,
            "Selected loot root is absent from the loaded pack: " + id);
      }
      LogUtils.getLogger().info("Waystone registered eligible loot roots: {}", terms.size());
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static int sigils(ServerLevel level, BlockPos origin, String table, long seed) {
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(origin))
            .create(LootContextParamSets.CHEST);
    var loot =
        level
            .getServer()
            .reloadableRegistries()
            .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(table)));
    int raw =
        loot.getRandomItems(params, seed).stream()
            .filter(stack -> stack.is(TravelIntegration.WAYSTONE_SIGIL.get()))
            .mapToInt(ItemStack::getCount)
            .sum();
    var chest = new SimpleContainer(27);
    loot.fill(chest, params, seed);
    int filled = chest.countItem(TravelIntegration.WAYSTONE_SIGIL.get());
    require(raw == filled, "Sigil lost during actual 27-slot fill: " + table);
    return filled;
  }

  private static void disabledItems(ServerLevel level) {
    for (String name : List.of("epitaph", "dormant_shard", "twinbound_feather")) {
      require(
          level.getRecipeManager().byKey(ResourceLocation.parse("waystones:" + name)).isEmpty(),
          "Disabled route item remains craftable: " + name);
    }
    var hidden =
        TagKey.create(Registries.ITEM, ResourceLocation.parse("c:hidden_from_recipe_viewers"));
    verifyCreativeTabs(level);
    for (var item : List.of(ModItems.epitaph, ModItems.dormantShard, ModItems.twinboundFeather)) {
      require(new ItemStack(item).is(hidden), "Recipe viewer item is visible");
    }
    BlockPos feet = level.getSharedSpawnPos().offset(16, 40, 16);
    BlockPos markerPosition = feet.above();
    var original = level.getBlockState(markerPosition);
    require(level.getBlockEntity(markerPosition) == null, "Do not replace a saved memorial");
    boolean keepInventory = level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("422204d5-f636-4e66-b29d-9c721d907a16"),
            "WaystoneEpitaph",
            Vec3.atBottomCenterOf(feet),
            null)) {
      var player = session.player;
      player.setGameMode(GameType.SURVIVAL);
      level.setBlockAndUpdate(markerPosition, Blocks.AIR.defaultBlockState());
      require(
          FleetingMemorialManager.spawnMemorial(player),
          "Positive memorial placement control failed");
      require(
          level.getBlockEntity(markerPosition) instanceof WaystoneBlockEntityBase,
          "Positive memorial entity missing");
      var marker = ((WaystoneBlockEntityBase) level.getBlockEntity(markerPosition)).getWaystone();
      FleetingMemorialManager.removeAfterUse(level, marker);
      require(
          WaystonesAPI.getWaystone(level.getServer(), marker.getWaystoneUid()).isEmpty(),
          "Control marker remains registered");
      player.getInventory().setItem(0, new ItemStack(ModItems.epitaph));
      level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, level.getServer());
      player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
      require(player.isDeadOrDying(), "The disabled memorial guard must not cancel death");
      require(
          player.getInventory().getItem(0).is(ModItems.epitaph)
              && player.getInventory().getItem(0).getCount() == 1,
          "Existing epitaph was consumed for an unusable destination");
      require(
          level.getBlockState(markerPosition).isAir(), "Disabled death route placed a memorial");
    } finally {
      level
          .getGameRules()
          .getRule(GameRules.RULE_KEEPINVENTORY)
          .set(keepInventory, level.getServer());
      level.setBlockAndUpdate(markerPosition, original);
    }
    LogUtils.getLogger().info("Waystone disabled recipe/catalog/death regression passed");
  }

  public static void verifyCreativeTabs(ServerLevel level) {
    CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
    for (boolean permissions : List.of(false, true)) {
      require(
          CreativeModeTabs.tryRebuildTabContents(
              level.enabledFeatures(), permissions, level.registryAccess()),
          "Creative cache did not rebuild");
      var tab = ModItems.creativeModeTab.get();
      for (var entries :
          List.of(
              tab.getDisplayItems(),
              tab.getSearchTabDisplayItems(),
              CreativeModeTabs.searchTab().getDisplayItems())) {
        require(
            entries.stream()
                    .filter(stack -> stack.is(TravelIntegration.WAYSTONE_SIGIL.get()))
                    .count()
                == 1,
            "Sigil missing or duplicated in creative tab/search");
        require(
            entries.stream()
                .filter(stack -> stack.is(TravelIntegration.WAYSTONE_SIGIL.get()))
                .allMatch(
                    stack ->
                        ItemStack.isSameItemSameComponents(
                            stack, new ItemStack(TravelIntegration.WAYSTONE_SIGIL.get()))),
            "Nonstandard creative sigil components");
        for (var item :
            List.of(ModItems.epitaph, ModItems.dormantShard, ModItems.twinboundFeather)) {
          require(
              entries.stream().noneMatch(stack -> stack.is(item)),
              "Disabled item remains in creative/search");
        }
      }
    }
    LogUtils.getLogger().info("Waystone creative tab and global search regression passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
