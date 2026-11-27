package dev.aeroscapes.regression;

import com.dannyandson.nutritionalbalance.api.IPlayerNutrient;
import com.dannyandson.nutritionalbalance.events.EventPlayerClone;
import com.dannyandson.nutritionalbalance.events.EventPlayerTick;
import com.dannyandson.nutritionalbalance.lunchbox.LunchBoxItem;
import com.dannyandson.nutritionalbalance.network.PlayerSync;
import com.dannyandson.nutritionalbalance.nutrients.PlayerNutritionData;
import com.dannyandson.nutritionalbalance.nutrients.WorldNutrients;
import com.dannyandson.nutritionalbalance.setup.Registration;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.survival.NutritionDeficits;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import sfiomn.legendarysurvivaloverhaul.api.data.manager.ThirstDataManager;
import sfiomn.legendarysurvivaloverhaul.api.thirst.HydrationEnum;
import sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil;
import sfiomn.legendarysurvivaloverhaul.common.attachments.thirst.ThirstAttachment;
import sfiomn.legendarysurvivaloverhaul.config.Config;
import sfiomn.legendarysurvivaloverhaul.util.AttachmentUtil;

/** Native server-player nutrition, drinking, recipe and persistence boundaries. */
public final class SurvivalRegression {
  private SurvivalRegression() {}

  public static void verify(ServerLevel level, Path output) {
    JsonObject report = auditFoods(level);
    write(output, report);
    require(
        report.getAsJsonArray("unassigned_foods").isEmpty(),
        "Unassigned actual foods: " + report.getAsJsonArray("unassigned_foods"));
    require(
        report.getAsJsonArray("unclassified_drinks").isEmpty(),
        "Unclassified drink roles: " + report.getAsJsonArray("unclassified_drinks"));
    verifyNutritionConfig();
    require(
        Config.Baked.thirstEnabled && Config.Baked.temperatureEnabled,
        "Selected survival modules disabled");
    require(
        !Config.Baked.localizedBodyDamageEnabled && !Config.Baked.healthOverhaulEnabled,
        "Unapproved injury or health system active");
    require(
        Config.Baked.naturalRegenerationEnabled
            && level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION),
        "Vanilla natural regeneration disabled");
    require(!Config.Baked.customHealthRegenEnabled, "Custom regeneration active");
    require(
        Config.Baked.baseFoodExhaustion == 0
            && Config.Baked.sprintingFoodExhaustion == 0
            && Config.Baked.onAttackFoodExhaustion == 0,
        "Additional hunger exhaustion active");
    require(
        Config.Baked.altitudeModifier == 0,
        "Altitude cold must not make every island an extreme climate");
    verifyDisabledContent(level);
    report.addProperty("disabled_module_recipes_hidden_items_and_loot", true);
    try (var session = openSession(level)) {
      ServerPlayer player = session.player;
      verifyDrinking(level, player);
      report.addProperty("purified_drinking_canteen_recipe_and_thirst_persistence", true);
      verifyNutrition(level, session);
      report.addProperty("native_food_event_cumulative_deficits_recovery_and_persistence", true);
    }
    write(output, report);
  }

  static void verifyNutritionPersistence(ServerLevel level, String phase) {
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("b2382a5e-a1e3-4c52-9d8b-188e35c08448"),
            "NutrientSave",
            Vec3.atCenterOf(level.getSharedSpawnPos()).add(0, 30, 0),
            null)) {
      var player = session.player;
      var data = PlayerNutritionData.getWorldNutritionData();
      var nutrition = data.getNutritionalBalancePlayer(player);
      var groups = nutrition.getPlayerNutrients();
      float initial = com.dannyandson.nutritionalbalance.Config.NUTRIENT_INITIAL.get().floatValue();
      float decay =
          com.dannyandson.nutritionalbalance.Config.NUTRIENT_DECAY_RATE.get().floatValue();
      float expected = initial - 10.0F * decay / groups.size();
      if (phase.equals("save")) {
        require(
            groups.stream().allMatch(group -> group.getValue() == initial),
            "The fixture starts from the initial nutrient values");
        nutrition.resetSavedSaturation(20);
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0);
        new EventPlayerTick().onPlayerTick(new PlayerTickEvent.Post(player));
        require(
            !data.isDirty(), "Drain-only play exercises the provider's clean saved-data record");
      } else {
        require(phase.equals("reload"), "Unknown nutrition persistence phase " + phase);
      }
      require(
          groups.size() == 5
              && expected < initial
              && groups.stream().allMatch(group -> near(group.getValue(), expected)),
          "All five depleted nutrient values must survive the normal world save, phase " + phase);
    }
  }

  static void verifyNutrition(ServerLevel level) {
    verifyNutritionConfig();
    try (var session = openSession(level)) {
      verifyNutrition(level, session);
    }
  }

  static void verifyNutritionCommands(ServerLevel level) {
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.randomUUID(),
            "NutrientCommand",
            Vec3.atCenterOf(level.getSharedSpawnPos()).add(0, 30, 0),
            null)) {
      var groups = List.of("carbs", "proteins", "fruits", "vegetables", "sugars");
      for (int count = 0; count < groups.size(); count++) {
        setNutrientByCommand(session, groups.get(count), 0, count + 1);
      }
      for (int count = groups.size() - 1; count >= 0; count--) {
        setNutrientByCommand(session, groups.get(count), 50, count);
      }
    }
  }

  private static void setNutrientByCommand(
      TestShipPlayerSession session, String group, int value, int deficient) {
    var player = session.player;
    player
        .server
        .getCommands()
        .performPrefixedCommand(
            player.createCommandSourceStack().withPermission(4),
            "nutritionalbalance set_nutrient " + group + " " + value);
    require(
        player.hasEffect(NutritionDeficits.attackEffect()) == (deficient >= 1)
            && player.hasEffect(NutritionDeficits.MOVEMENT) == (deficient >= 3)
            && player.hasEffect(NutritionDeficits.MINING) == (deficient >= 5),
        "The command immediately applies or clears the correct tiers at "
            + deficient
            + " deficits");
    var packet = session.takePayload(PlayerSync.class);
    var levels = JsonParser.parseString(packet.jsonData()).getAsJsonObject();
    require(
        !packet.openGUI() && levels.get(group).getAsInt() == value,
        "The command synchronizes its nutrient value immediately");
  }

  private static TestShipPlayerSession openSession(ServerLevel level) {
    return TestShipPlayerSession.create(
        level,
        UUID.randomUUID(),
        "SurvivalRegression",
        Vec3.atCenterOf(level.getSharedSpawnPos()).add(0, 30, 0),
        null);
  }

  private static void verifyNutritionConfig() {
    require(
        com.dannyandson.nutritionalbalance.Config.NUTRIENT_INITIAL.get() == 50.0
            && com.dannyandson.nutritionalbalance.Config.NUTRIENT_MALNOURISHED.get() == 10.0
            && com.dannyandson.nutritionalbalance.Config.NUTRIENT_ENGORGED.get() == 180.0,
        "Native nutrition thresholds did not load from authoritative server config");
    require(
        com.dannyandson.nutritionalbalance.Config.MALNOURISHED_ATTACK_DAMAGE.get() == -3.0
            && com.dannyandson.nutritionalbalance.Config.MALNOURISHED_MAX_HEALTH.get() == 0.0
            && com.dannyandson.nutritionalbalance.Config.MALNOURISHED_MOVEMENT_SPEED.get() == 0.0
            && com.dannyandson.nutritionalbalance.Config.MALNOURISHED_ATTACK_SPEED.get() == 0.0
            && com.dannyandson.nutritionalbalance.Config.NOURISHED_MAX_HEALTH.get() == 0.0,
        "Native mild nutrition effects did not load from authoritative server config");
    require(
        com.dannyandson.nutritionalbalance.Config.BAD_NUTRIENTS.get().isEmpty()
            && com.dannyandson.nutritionalbalance.Config.GOOD_NUTRIENTS.get().isEmpty()
            && com.dannyandson.nutritionalbalance.Config.NUTRIENT_DEATH_LOSS.get() == 0.0,
        "The five required groups and native death floor must come from the deployed config");
  }

  private static void verifyDisabledContent(ServerLevel level) {
    for (String name :
        List.of(
            "bandage",
            "healing_herbs",
            "heart_container",
            "medkit",
            "morphine",
            "plaster",
            "tonic")) {
      require(
          level
              .getRecipeManager()
              .byKey(ResourceLocation.parse("legendarysurvivaloverhaul:" + name))
              .isEmpty(),
          "Disabled health/limb recipe remains available: " + name);
    }
    var hidden =
        TagKey.create(Registries.ITEM, ResourceLocation.parse("c:hidden_from_recipe_viewers"));
    for (String name :
        List.of(
            "bandage",
            "healing_herbs",
            "heart_container",
            "medkit",
            "morphine",
            "plaster",
            "tonic",
            "first_aid_supplies",
            "heart_fragment",
            "shield_container")) {
      var id = ResourceLocation.parse("legendarysurvivaloverhaul:" + name);
      var item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow();
      require(new ItemStack(item).is(hidden), "Disabled health/limb item remains visible: " + id);
    }
    for (String name :
        List.of(
            "abandoned_mineshaft",
            "buried_treasure",
            "jungle_temple",
            "pillager_outpost",
            "bastion_treasure")) {
      var id =
          ResourceLocation.parse(
              "legendarysurvivaloverhaul:loot_table/inject/chests/" + name + ".json");
      try (var reader = level.getServer().getResourceManager().openAsReader(id)) {
        var table = JsonParser.parseReader(reader).getAsJsonObject();
        var pools = table.getAsJsonArray("pools");
        if (!name.equals("bastion_treasure")) {
          require(pools.isEmpty(), "Disabled-only loot injection still has rewards: " + name);
          continue;
        }
        require(pools.size() == 2, "Bastion selected rewards changed");
        for (int index = 0; index < pools.size(); index++) {
          var pool = pools.get(index).getAsJsonObject();
          require(pool.get("rolls").getAsInt() == 1, "Bastion reward rolls changed");
          var entries = pool.getAsJsonArray("entries");
          require(entries.size() == 2, "Bastion reward alternatives changed");
          var reward = entries.get(0).getAsJsonObject();
          var empty = entries.get(1).getAsJsonObject();
          require(
              reward.get("weight").getAsInt() == 5
                  && empty.get("weight").getAsInt() == 95
                  && empty.get("type").getAsString().equals("minecraft:empty"),
              "Bastion selected reward probability changed");
          String expected =
              index == 0
                  ? "legendarysurvivaloverhaul:cold_resistance_ring"
                  : "minecraft:enchanted_book";
          require(
              reward.get("name").getAsString().equals(expected),
              "Bastion selected reward changed: " + expected);
          if (index == 1)
            require(
                reward
                    .getAsJsonArray("functions")
                    .get(0)
                    .getAsJsonObject()
                    .get("options")
                    .getAsString()
                    .equals("legendarysurvivaloverhaul:purity"),
                "Bastion book enchantment changed");
        }
        var key =
            ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation.parse("legendarysurvivaloverhaul:inject/chests/bastion_treasure"));
        require(
            level.getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY,
            "Selected bastion loot failed to decode");
      } catch (IOException failure) {
        throw new IllegalStateException("Cannot inspect effective survival loot " + id, failure);
      }
    }
  }

  private static JsonObject auditFoods(ServerLevel level) {
    WorldNutrients.register();
    WorldNutrients.prewarm(level);
    JsonObject report = new JsonObject();
    JsonObject foods = new JsonObject();
    JsonObject drinks = new JsonObject();
    JsonObject properties = new JsonObject();
    JsonObject excludedFoods = new JsonObject();
    JsonArray unassigned = new JsonArray();
    JsonArray unclassifiedDrinks = new JsonArray();
    Map<String, String> drinkExclusions = drinkExclusions();
    Set<String> seenDrinkExclusions = new HashSet<>();
    for (var item : BuiltInRegistries.ITEM) {
      ItemStack stack = item.getDefaultInstance();
      String id = BuiltInRegistries.ITEM.getKey(item).toString();
      var food = stack.getFoodProperties(null);
      if (item instanceof LunchBoxItem)
        excludedFoods.addProperty(
            id,
            "Food-container proxy: native nutrients resolve the selected food; the empty box is not"
                + " an independent food.");
      if (food != null && !(item instanceof LunchBoxItem)) {
        JsonArray groups = new JsonArray();
        WorldNutrients.getNutrients(stack, level).stream()
            .map(nutrient -> nutrient.name)
            .sorted()
            .forEach(groups::add);
        foods.add(id, groups);
        if (groups.isEmpty()) unassigned.add(id);
        JsonObject values = new JsonObject();
        values.addProperty("nutrition", food.nutrition());
        values.addProperty("saturation", food.saturation());
        values.addProperty("can_always_eat", food.canAlwaysEat());
        values.addProperty("eat_seconds", food.eatSeconds());
        values.addProperty("use_animation", item.getUseAnimation(stack).name());
        values.addProperty("item_class", item.getClass().getName());
        properties.add(id, values);
      }
      if (item.getUseAnimation(stack) == UseAnim.DRINK) {
        var hydration = ThirstDataManager.getConsumable(stack);
        JsonObject values = new JsonObject();
        values.addProperty("assigned", hydration != null);
        if (hydration != null) {
          values.addProperty("hydration", hydration.hydration);
          values.addProperty("saturation", hydration.saturation);
        } else if (drinkExclusions.containsKey(id)) {
          seenDrinkExclusions.add(id);
          values.addProperty("hydration_exclusion", drinkExclusions.get(id));
        } else {
          unclassifiedDrinks.add(id);
        }
        drinks.add(id, values);
      }
    }
    report.add("food_groups", foods);
    report.add("food_properties", properties);
    report.add("food_exclusions", excludedFoods);
    report.add("unassigned_foods", unassigned);
    report.add("drink_hydration", drinks);
    report.add("unclassified_drinks", unclassifiedDrinks);
    require(
        seenDrinkExclusions.equals(drinkExclusions.keySet()),
        "Re-audit stale drink-role exclusions: "
            + drinkExclusions.keySet().stream()
                .filter(id -> !seenDrinkExclusions.contains(id))
                .toList());
    requireGroups(level, "minecraft:apple", Set.of("fruits"));
    requireGroups(level, "minecraft:bread", Set.of("carbs"));
    requireGroups(level, "minecraft:cooked_beef", Set.of("proteins"));
    requireGroups(level, "minecraft:carrot", Set.of("vegetables"));
    requireGroups(level, "farmersdelight:beef_stew", Set.of("carbs", "proteins", "vegetables"));
    requireGroups(level, "createfood:beef_bun_cheese", Set.of("carbs", "proteins"));
    requireGroups(level, "hybrid_aquatic:raw_shrimp", Set.of("proteins"));
    requireGroups(level, "hybrid_aquatic:blobfish", Set.of("proteins"));
    requireGroups(level, "natures_spirit:half_pizza", Set.of("carbs", "proteins", "vegetables"));
    requireGroups(level, "natures_delight:manakish_slice", Set.of("carbs", "proteins"));
    requireGroups(level, "brewinandchewin:fiery_fondue", Set.of("carbs", "proteins", "vegetables"));
    requireGroups(level, "createfood:blackstrap_molasses_bottle", Set.of("sugars"));
    var intermediate =
        BuiltInRegistries.ITEM.get(
            ResourceLocation.parse("delightfulcreators:incomplete_fruit_salad"));
    require(
        intermediate.getDefaultInstance().getFoodProperties(null).nutrition() == 3
            && intermediate.getUseAnimation(intermediate.getDefaultInstance()) == UseAnim.EAT,
        "Assembly intermediate is no longer the verified edible item");
    require(
        WorldNutrients.getNutrients(intermediate, level).stream()
            .map(nutrient -> nutrient.name)
            .collect(Collectors.toSet())
            .equals(Set.of("fruits")),
        "Intermediate receives nutrients before those ingredients are actually added");
    requireDrink("createfood:apple_juice_bottle", 8, 4.0f);
    requireDrink("createfood:milkshake_bottle", 6, 1.5f);
    requireDrink("createfood:hot_chocolate_bottle", 6, 3.0f);
    requireDrink("brewinandchewin:beer", 8, 6.0f);
    requireDrink("createfood:chocolate_milk_bottle", 6, 1.0f);
    requireDrink("createfood:hot_dark_chocolate_bottle", 6, 3.0f);
    requireDrink("createfood:hot_white_chocolate_bottle", 6, 3.0f);
    requireDrink("farmersdelight:bone_broth", 4, 2.0f);
    return report;
  }

  private static Map<String, String> drinkExclusions() {
    try (var input =
        SurvivalRegression.class.getResourceAsStream("/survival-drink-exclusions.json")) {
      if (input == null) throw new IllegalStateException("Missing reviewed drink-role exclusions");
      var roles =
          JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
              .getAsJsonObject();
      Map<String, String> result = new HashMap<>();
      for (var role : roles.entrySet()) {
        var definition = role.getValue().getAsJsonObject();
        String reason = definition.get("reason").getAsString();
        for (var id : definition.getAsJsonArray("ids"))
          require(
              result.put(id.getAsString(), reason) == null, "Duplicate reviewed drink role: " + id);
      }
      return Map.copyOf(result);
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read reviewed drink-role exclusions", failure);
    }
  }

  private static void requireDrink(String id, int hydration, float saturation) {
    var item =
        BuiltInRegistries.ITEM
            .getOptional(ResourceLocation.parse(id))
            .orElseThrow(() -> new IllegalStateException("Missing drink " + id));
    var actual = ThirstDataManager.getConsumable(item.getDefaultInstance());
    require(
        actual != null && actual.hydration == hydration && actual.saturation == saturation,
        "Missing or incorrect hydration for " + id);
  }

  private static void requireGroups(ServerLevel level, String id, Set<String> expected) {
    var item =
        BuiltInRegistries.ITEM
            .getOptional(ResourceLocation.parse(id))
            .orElseThrow(() -> new IllegalStateException("Missing food " + id));
    Set<String> actual =
        WorldNutrients.getNutrients(item, level).stream()
            .map(nutrient -> nutrient.name)
            .collect(Collectors.toSet());
    require(actual.containsAll(expected), "Missing food groups for " + id + ": " + actual);
  }

  private static void verifyDrinking(ServerLevel level, ServerPlayer player) {
    ThirstAttachment thirst = AttachmentUtil.getThirstAttachment(player);
    thirst.setHydrationLevel(2);
    thirst.setSaturation(0);
    ItemStack water =
        new ItemStack(
            BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("legendarysurvivaloverhaul:purified_water_bottle")));
    ItemStack remainder = water.getItem().finishUsingItem(water.copy(), level, player);
    NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, water, 0, remainder));
    require(
        thirst.getHydrationLevel() == 8 && thirst.getSaturationLevel() == 1.5f,
        "Purified drink hydration differs from pinned data");
    require(remainder.is(Items.GLASS_BOTTLE), "Purified bottle remainder lost");
    ItemStack canteen =
        new ItemStack(
            BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("legendarysurvivaloverhaul:canteen")));
    ThirstUtil.setCapacityTag(canteen, 3);
    ThirstUtil.setHydrationEnumTag(canteen, HydrationEnum.NORMAL);
    SingleRecipeInput input = new SingleRecipeInput(canteen);
    var recipe =
        level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level).orElseThrow();
    ItemStack purified = recipe.value().assemble(input, level.registryAccess());
    require(
        purified.is(canteen.getItem())
            && ThirstUtil.getCapacityTag(purified) == 3
            && ThirstUtil.getHydrationEnumTag(purified) == HydrationEnum.PURIFIED,
        "Canteen purification changed item/charges or failed to purify");
    CompoundTag saved = thirst.writeNBT();
    ThirstAttachment restored = new ThirstAttachment();
    restored.readNBT(saved);
    require(
        restored.getHydrationLevel() == 8 && restored.getSaturationLevel() == 1.5f,
        "Thirst state did not round trip");
  }

  private static void verifyNutrition(ServerLevel level, TestShipPlayerSession session) {
    var player = session.player;
    var data = PlayerNutritionData.getWorldNutritionData();
    var nutrition = data.getNutritionalBalancePlayer(player);
    require(
        nutrition.getStatus() != IPlayerNutrient.NutrientStatus.MALNOURISHED,
        "New player begins deficient");
    nutrition.getPlayerNutrients().forEach(nutrient -> nutrient.setValue(50));
    nutrition.resetSavedSaturation(
        player.getFoodData().getSaturationLevel() + player.getFoodData().getFoodLevel());
    var sugar = nutrition.getPlayerNutrientByName("sugars");
    var fruit = nutrition.getPlayerNutrientByName("fruits");
    var proteins = nutrition.getPlayerNutrientByName("proteins");
    var vegetables = nutrition.getPlayerNutrientByName("vegetables");
    var carbs = nutrition.getPlayerNutrientByName("carbs");
    ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
    player.setItemSlot(EquipmentSlot.MAINHAND, sword);
    sword.forEachModifier(
        EquipmentSlot.MAINHAND,
        (attribute, modifier) -> player.getAttribute(attribute).addTransientModifier(modifier));
    var normal = NutritionAttributes.of(player);
    require(normal.attack() >= 8, "Native weapon modifiers did not equip");

    var first = nutrition.getPlayerNutrients().getFirst();
    var last = nutrition.getPlayerNutrients().getLast();
    var excess = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(Registration.ENGORGED_EFFECT.get());
    first.setValue(181);
    verifyNutrientPenalties(session, normal, 0, "An excess alone keeps the provider status");
    require(player.hasEffect(excess), "The native excess effect must remain available");
    last.setValue(0);
    verifyNutrientPenalties(session, normal, 1, "An early excess cannot hide a later deficit");
    require(!player.hasEffect(excess), "The native deficit effect takes priority over excess");
    last.setValue(50);
    verifyNutrientPenalties(session, normal, 0, "Clearing a deficit restores the excess effect");
    require(player.hasEffect(excess), "The provider's excess status returns when deficits clear");
    first.setValue(50);

    sugar.setValue(0);
    fruit.setValue(10);
    verifyNutrientPenalties(session, normal, 1, "Empty sugar alone lowers attack damage");
    var attackEffect = NutritionDeficits.attackEffect();
    player.removeEffect(attackEffect);
    player.addEffect(
        new MobEffectInstance(attackEffect, Integer.MAX_VALUE - 20, 0, true, false, true));
    session.takeEffectChanges();
    tickNutrition(player);
    require(
        session.takeEffectChanges().get(attackEffect)
                instanceof ClientboundUpdateMobEffectPacket migrated
            && migrated.getEffectDurationTicks() == MobEffectInstance.INFINITE_DURATION
            && !player.getEffect(attackEffect).isAmbient(),
        "A saved finite attack penalty migrates to the new duration and presentation");
    player
        .getAttribute(Attributes.ATTACK_DAMAGE)
        .addOrReplacePermanentModifier(
            new AttributeModifier(
                ResourceLocation.parse("nutritionalbalance:malnourished_attack_damage"),
                -4,
                AttributeModifier.Operation.ADD_VALUE));
    verifyNutrientPenalties(session, normal, 1, "Refresh corrects a saved four-point modifier");
    sugar.setValue(10);
    verifyNutrientPenalties(session, normal, 0, "Exactly ten clears the sugar deficit");
    fruit.setValue(Math.nextDown(10.0f));
    verifyNutrientPenalties(session, normal, 1, "The first requirement immediately below ten");
    proteins.setValue(0);
    verifyNutrientPenalties(session, normal, 2, "Two deficiencies still only reduce attack damage");
    vegetables.setValue(0);
    verifyNutrientPenalties(session, normal, 3, "The third deficiency adds the movement penalty");
    verifyNutrientSnapshot(session, vegetables);
    carbs.setValue(0);
    verifyNutrientPenalties(session, normal, 4, "Four deficiencies still have just two penalties");
    sugar.setValue(Math.nextDown(10.0f));
    verifyNutrientPenalties(session, normal, 5, "Deficient sugars add mining at five deficiencies");
    verifyNutrientSnapshot(session, sugar);
    verifyNutrientPenalties(session, normal, 5, "Refreshing an unchanged tier does not stack it");
    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300));
    tickNutrition(player);
    require(
        player.hasEffect(MobEffects.WEAKNESS)
            && near(player.getAttributeValue(Attributes.ATTACK_DAMAGE), normal.attack() - 7),
        "Vanilla Weakness independently adds its ordinary four-point reduction");
    player.removeEffect(MobEffects.WEAKNESS);
    verifyNutrientPenalties(session, normal, 5, "Nutrition remains after vanilla Weakness ends");

    CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
    var restored =
        PlayerNutritionData.load(saved, level.registryAccess()).getNutritionalBalancePlayer(player);
    for (var nutrient : nutrition.getPlayerNutrients()) {
      require(
          restored.getPlayerNutrientByName(nutrient.getNutrientName()).getValue()
              == nutrient.getValue(),
          "Deficient nutrient state did not round trip: " + nutrient.getNutrientName());
    }

    sugar.setValue(10);
    verifyNutrientPenalties(session, normal, 4, "Recovering the fifth deficiency clears mining");
    verifyNutrientSnapshot(session, sugar);
    vegetables.setValue(10);
    verifyNutrientPenalties(session, normal, 3, "Three remaining deficiencies still slow movement");
    proteins.setValue(10);
    verifyNutrientPenalties(session, normal, 2, "Recovering the third deficiency clears movement");
    verifyNutrientSnapshot(session, proteins);
    carbs.setValue(10);
    verifyNutrientPenalties(
        session, normal, 1, "One deficiency still lowers equipped attack damage");
    ItemStack apple = new ItemStack(Items.APPLE);
    for (int index = 0; index < 4; index++)
      NeoForge.EVENT_BUS.post(
          new LivingEntityUseItemEvent.Finish(player, apple, 0, ItemStack.EMPTY));
    verifyNutrientPenalties(session, normal, 0, "Eating fruit clears the last penalty");
    require(
        fruit.getValue() >= 10
            && nutrition.getStatus() != IPlayerNutrient.NutrientStatus.MALNOURISHED,
        "Eating fruit did not recover the deficit");
    for (var required : List.of(carbs, proteins, fruit, vegetables, sugar)) required.setValue(0);
    verifyNutrientPenalties(session, normal, 5, "Five empty groups show all three penalties");
    sword.forEachModifier(
        EquipmentSlot.MAINHAND,
        (attribute, modifier) -> player.getAttribute(attribute).removeModifier(modifier.id()));
    player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    verifyNutritionDeathClone(
        player,
        replacement -> {
          var respawned = data.getNutritionalBalancePlayer(replacement);
          for (String group : List.of("carbs", "proteins", "fruits", "vegetables", "sugars"))
            require(
                respawned.getPlayerNutrientByName(group).getValue() == 50,
                "Death keeps the native starting-level floor for " + group);
          tickNutrition(replacement);
          require(
              near(replacement.getAttributeValue(Attributes.MOVEMENT_SPEED), normal.movement())
                  && near(
                      replacement.getAttributeValue(Attributes.BLOCK_BREAK_SPEED), normal.mining()),
              "The replacement player clears deficit penalties");
        });
    sugar.setValue(75);
    verifyNutritionDeathClone(
        player,
        replacement ->
            require(
                data.getNutritionalBalancePlayer(replacement)
                        .getPlayerNutrientByName("sugars")
                        .getValue()
                    == 75,
                "Death retains an existing value above the native floor"));
  }

  private static void verifyNutritionDeathClone(
      ServerPlayer original, Consumer<ServerPlayer> verification) {
    var replacement =
        new ServerPlayer(
            original.server,
            original.serverLevel(),
            original.getGameProfile(),
            original.clientInformation());
    replacement.connection = original.connection;
    replacement.getAttributes().assignBaseValues(original.getAttributes());
    try {
      // The synthetic player has no login state for unrelated mods' clone listeners.
      new EventPlayerClone().onPlayerClone(new PlayerEvent.Clone(replacement, original, true));
      verification.accept(replacement);
    } finally {
      replacement.getTextFilter().leave();
      replacement.getAdvancements().stopListening();
    }
  }

  private static void verifyNutrientPenalties(
      TestShipPlayerSession session, NutritionAttributes normal, int deficient, String scenario) {
    var player = session.player;
    List<Holder<MobEffect>> effects =
        List.of(
            NutritionDeficits.attackEffect(), NutritionDeficits.MOVEMENT, NutritionDeficits.MINING);
    var expectedCount = deficient == 5 ? 3 : deficient >= 3 ? 2 : deficient > 0 ? 1 : 0;
    var previous = effects.stream().filter(player::hasEffect).collect(Collectors.toSet());
    session.takeEffectChanges();
    tickNutrition(player);
    var changes = session.takeEffectChanges();
    for (int index = 0; index < effects.size(); index++) {
      var effect = effects.get(index);
      var packet = changes.get(effect);
      boolean expected = index < expectedCount;
      require(
          player.hasEffect(effect) == expected, scenario + " effect " + effect.getRegisteredName());
      require(
          !expected || player.getEffect(effect).isInfiniteDuration(),
          scenario + " active dietary penalty stays until its deficiency recovers");
      if (previous.contains(effect) == expected) {
        require(packet == null, scenario + " stable effects do not resend");
      } else if (expected) {
        require(
            packet instanceof ClientboundUpdateMobEffectPacket added
                && added.effectShowsIcon()
                && added.getEffectDurationTicks() == MobEffectInstance.INFINITE_DURATION
                && added.getEffectAmplifier() == 0,
            scenario + " sends the owning client a visible effect");
      } else {
        require(
            packet instanceof ClientboundRemoveMobEffectPacket,
            scenario + " clears the owning client's effect");
      }
    }
    var actual = NutritionAttributes.of(player);
    require(
        near(actual.attack(), normal.attack() - (deficient > 0 ? 3 : 0)),
        scenario + " attack damage");
    require(
        near(actual.movement(), normal.movement() * (deficient >= 3 ? 0.8 : 1)),
        scenario + " movement speed");
    double miningFactor = deficient >= 5 ? 0.7 : 1;
    require(
        near(actual.mining(), normal.mining() * miningFactor), scenario + " block speed attribute");
    require(
        near(actual.digSpeed(), normal.digSpeed() * miningFactor),
        scenario + " native mining speed");
    require(player.getMaxHealth() == 20, scenario + " maximum health");
  }

  private static void verifyNutrientSnapshot(
      TestShipPlayerSession session, IPlayerNutrient nutrient) {
    var packet = session.takePayload(PlayerSync.class);
    var levels = JsonParser.parseString(packet.jsonData()).getAsJsonObject();
    require(
        !packet.openGUI()
            && levels.get(nutrient.getNutrientName()).getAsFloat() == nutrient.getValue(),
        "A higher-tier transition sends the changed nutrition level without reopening the screen");
  }

  private static void tickNutrition(ServerPlayer player) {
    player.tickCount = (player.tickCount / 200 + 1) * 200;
    new EventPlayerTick().onPlayerTick(new PlayerTickEvent.Post(player));
  }

  private static boolean near(double actual, double expected) {
    return Math.abs(actual - expected) < 0.0001;
  }

  private record NutritionAttributes(
      double attack, double movement, double mining, double digSpeed) {
    static NutritionAttributes of(ServerPlayer player) {
      return new NutritionAttributes(
          player.getAttributeValue(Attributes.ATTACK_DAMAGE),
          player.getAttributeValue(Attributes.MOVEMENT_SPEED),
          player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED),
          player.getDigSpeed(Blocks.STONE.defaultBlockState(), player.blockPosition()));
    }
  }

  private static void write(Path output, JsonObject report) {
    try {
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write survival report", failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
