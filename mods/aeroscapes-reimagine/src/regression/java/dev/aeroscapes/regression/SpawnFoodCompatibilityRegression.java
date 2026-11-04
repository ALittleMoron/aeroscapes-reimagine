package dev.aeroscapes.regression;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.ninni.spawn.compat.farmersdelight.FarmersDelightCompat;
import com.ninni.spawn.registry.SpawnBlocks;
import com.ninni.spawn.registry.SpawnItems;
import com.ninni.spawn.server.block.SpawnFeastBlock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import vectorwing.farmersdelight.common.Configuration;
import vectorwing.farmersdelight.common.registry.ModEffects;

/** Runs the native tooltip, food-effect and serving paths without launching a client. */
final class SpawnFoodCompatibilityRegression {
  private SpawnFoodCompatibilityRegression() {}

  static void run(ServerLevel level, Path output) {
    var clientConfig = CommentedConfig.inMemory();
    Configuration.CLIENT_CONFIG.correct(clientConfig);
    // The client spec is normally unloaded on a dedicated server; native sync accepts memory data.
    var config =
        new ConfigTracker()
            .registerConfig(
                ModConfig.Type.CLIENT,
                Configuration.CLIENT_CONFIG,
                ModList.get().getModContainerById("farmersdelight").orElseThrow(),
                "aeroscapes-fixture-food-client.toml");
    ConfigTracker.acceptSyncedConfig(
        config, new TomlWriter().writeToString(clientConfig).getBytes(StandardCharsets.UTF_8));
    boolean originalSoupSetting = Configuration.VANILLA_SOUP_EXTRA_EFFECTS.get();
    boolean broken = Boolean.getBoolean("aeroscapes.regression.spawnFoodExpectBroken");
    try {
      if (broken) {
        missing("ENABLE_FOOD_EFFECT_TOOLTIP", () -> tooltip(level, new ItemStack(Items.STONE)));
        missing("ENABLE_VANILLA_SOUP_EXTRA_EFFECTS", () -> effects(level, true));
        missing("BLOCK_FOOD_TAKE_PORTION", () -> serving(level));
      } else {
        for (boolean tooltip : new boolean[] {false, true})
          for (boolean soup : new boolean[] {false, true}) {
            Configuration.FOOD_EFFECT_TOOLTIP.set(tooltip);
            Configuration.VANILLA_SOUP_EXTRA_EFFECTS.set(soup);
            require(
                tooltip(level, new ItemStack(SpawnItems.CLAM_CHOWDER.get())).size()
                    == (tooltip && soup ? 1 : 0),
                "Chowder tooltip ignored native settings");
            require(
                tooltip(level, new ItemStack(SpawnItems.CRAB_BOIL.get())).size()
                    == (tooltip ? 1 : 0),
                "Crab tooltip ignored native settings");
            require(
                tooltip(level, new ItemStack(Items.STONE)).isEmpty(), "Unrelated tooltip changed");
            effects(level, soup);
          }
        serving(level);
      }
      JsonObject report = new JsonObject();
      report.addProperty("original_linkage_errors_reproduced", broken);
      report.addProperty("native_tooltip_settings_effects_and_serving_passed", !broken);
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write food compatibility result", failure);
    } finally {
      Configuration.VANILLA_SOUP_EXTRA_EFFECTS.set(originalSoupSetting);
      Configuration.CLIENT_CONFIG.acceptConfig(null);
    }
    LogUtils.getLogger()
        .info("Spawn food compatibility regression passed: originalBroken={}", broken);
  }

  private static List<Component> tooltip(ServerLevel level, ItemStack stack) {
    List<Component> lines = new ArrayList<>();
    FarmersDelightCompat.addEffectTooltip(
        new ItemTooltipEvent(
            stack, null, lines, TooltipFlag.NORMAL, Item.TooltipContext.of(level)));
    return lines;
  }

  private static void effects(ServerLevel level, boolean soupEnabled) {
    var animal = EntityType.COW.create(level);
    require(animal != null, "Missing fixture animal");
    FarmersDelightCompat.applyEffects(animal, new ItemStack(SpawnItems.CLAM_CHOWDER.get()), true);
    require(animal.hasEffect(ModEffects.NOURISHMENT) == soupEnabled, "Soup effect ignored setting");
    animal.removeAllEffects();
    FarmersDelightCompat.applyEffects(animal, new ItemStack(SpawnItems.CRAB_BOIL.get()), false);
    var effect = animal.getEffect(ModEffects.NOURISHMENT);
    require(
        effect != null && effect.getDuration() == 3600 && effect.getAmplifier() == 0,
        "Native crab meal nourishment changed");
  }

  private static void serving(ServerLevel level) {
    BlockPos position = level.getSharedSpawnPos().above(4);
    var original = level.getBlockState(position);
    var originalBelow = level.getBlockState(position.below());
    var player = FakePlayerFactory.getMinecraft(level);
    var previousHand = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
    try {
      level.setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
      var state = SpawnBlocks.CRAB_BOIL_BLOCK.get().defaultBlockState();
      level.setBlockAndUpdate(position, state);
      int before = state.getValue(SpawnFeastBlock.SERVINGS);
      int portions = player.getInventory().countItem(SpawnItems.CRAB_BOIL.get());
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL, 2));
      var result =
          state.useItemOn(
              player.getMainHandItem(),
              level,
              player,
              InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
      require(result.consumesAction(), "Native serving interaction failed");
      require(
          level.getBlockState(position).getValue(SpawnFeastBlock.SERVINGS) == before - 1,
          "Native serving count changed");
      require(
          player.getMainHandItem().is(Items.BOWL) && player.getMainHandItem().getCount() == 1,
          "Native bowl cost changed");
      require(
          player.getInventory().countItem(SpawnItems.CRAB_BOIL.get()) == portions + 1,
          "Native serving output changed");
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, previousHand);
      level.setBlockAndUpdate(position, original);
      level.setBlockAndUpdate(position.below(), originalBelow);
    }
  }

  private static void missing(String field, Runnable operation) {
    try {
      operation.run();
    } catch (NoSuchFieldError expected) {
      require(
          expected.getMessage().contains(field), "Different original linkage error: " + expected);
      return;
    }
    throw new IllegalStateException("Original error did not reproduce: " + field);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
