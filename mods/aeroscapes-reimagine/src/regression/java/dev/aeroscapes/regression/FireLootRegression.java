package dev.aeroscapes.regression;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.ISSDamageTypes;
import io.redspace.ironsspellbooks.entity.spells.magma_ball.FireField;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native loot tables, global modifiers and lethal fire effects on a disposable server. */
final class FireLootRegression {
  private static final String ASH = "supplementaries:ash";

  private record Death(List<ItemStack> drops, DamageSource source, boolean burning) {}

  private FireLootRegression() {}

  static void start(ServerLevel level) {
    var listener =
        new Consumer<ServerTickEvent.Post>() {
          @Override
          public void accept(ServerTickEvent.Post event) {
            if (event.getServer() != level.getServer()) return;
            NeoForge.EVENT_BUS.unregister(this);
            verify(level);
          }
        };
    NeoForge.EVENT_BUS.addListener(listener);
  }

  private static void verify(ServerLevel level) {
    boolean baseline = Boolean.getBoolean("aeroscapes.regression.fireLootBaseline");
    LivingEntity attacker = entity(level, "minecraft:zombie");
    var fire =
        new DamageSource(level.registryAccess().holderOrThrow(ISSDamageTypes.FIRE_MAGIC), attacker);
    require(!fire.is(DamageTypeTags.IS_FIRE), "Combat fire classification must remain native");
    verifyFoodTables(level, attacker, baseline);
    for (String type : List.of("minecraft:chicken", "hybrid_birds:turkey", "naturalist:turkey")) {
      LivingEntity victim = entity(level, type);
      int previousFire = victim.getRemainingFireTicks();
      Death death = kill(victim, target -> target.hurt(fire, 10000));
      require(death.source() == fire, "Actual lethal source/attribution retained: " + type);
      require(death.burning() != baseline, "Lethal fire loot sees burning: " + type);
      require(victim.getRemainingFireTicks() == previousFire, "Loot-only ignition restored");
      String food = type.equals("naturalist:turkey") ? "naturalist:drumstick" : type;
      String cooked =
          type.equals("minecraft:chicken")
              ? "minecraft:cooked_chicken"
              : type.equals("naturalist:turkey")
                  ? "naturalist:cooked_drumstick"
                  : "hybrid_birds:cooked_turkey";
      require(count(death.drops(), baseline ? food : cooked) > 0, "Lethal fire food: " + type);
      LivingEntity cold = entity(level, type);
      Death physical =
          kill(cold, target -> target.hurt(level.damageSources().mobAttack(attacker), 10000));
      require(!physical.burning() && count(physical.drops(), food) > 0, "Physical kill stays raw");
    }
    LivingEntity chicken = entity(level, "minecraft:chicken");
    Objects.requireNonNull(chicken.getAttribute(Attributes.BURNING_TIME)).setBaseValue(0);
    FireField field = new FireField(level);
    field.setOwner(attacker);
    field.setDamage(10000);
    Death fieldDeath = kill(chicken, field::applyEffect);
    require(fieldDeath.source().is(ISSDamageTypes.FIRE_FIELD), "Native FireField source");
    require(fieldDeath.burning() != baseline, "FireField ignites after lethal hurt returns");
    require(
        count(fieldDeath.drops(), baseline ? "minecraft:chicken" : "minecraft:cooked_chicken") > 0,
        "Native FireField lethal cooking");
    LivingEntity immune = entity(level, "minecraft:magma_cube");
    require(immune.fireImmune(), "Native fire-immune control");
    Death immuneDeath = kill(immune, target -> target.hurt(fire, 10000));
    require(!immuneDeath.burning(), "Fire immunity remains effective for loot predicates");
    if (!baseline) {
      RuntimeJsonPatchesRegression.verify(level);
      verifyModifiers(level, attacker, fire);
    }
    attacker.discard();
    field.discard();
    LogUtils.getLogger().info("Fire loot regression passed: baseline={}", baseline);
  }

  private static void verifyFoodTables(ServerLevel level, LivingEntity attacker, boolean baseline) {
    try (var stream = FireLootRegression.class.getResourceAsStream("/fire-food-loot.json")) {
      require(stream != null, "Missing fire loot fixture");
      var fixtures =
          JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .getAsJsonArray();
      require(fixtures.size() == 34, "Complete missing-cooking table inventory");
      for (var fixture : fixtures) {
        var entry = fixture.getAsJsonObject();
        String table = entry.get("table").getAsString();
        LivingEntity victim = entity(level, entry.get("entity").getAsString());
        require(
            victim.getLootTable().location().toString().equals(table),
            "Actual entity loot route: " + table);
        Map<String, String> cooking = new HashMap<>();
        entry
            .getAsJsonObject("cooking")
            .entrySet()
            .forEach(food -> cooking.put(food.getKey(), food.getValue().getAsString()));
        var source = level.damageSources().mobAttack(attacker);
        int witnessed = 0;
        for (int seed = 1; seed <= 16; seed++) {
          victim.clearFire();
          Map<String, Integer> cold = counts(roll(level, table, victim, source, seed));
          Map<String, Integer> expected = new HashMap<>();
          for (var item : cold.entrySet()) {
            String cooked =
                baseline ? item.getKey() : cooking.getOrDefault(item.getKey(), item.getKey());
            expected.merge(cooked, item.getValue(), Integer::sum);
            if (cooking.containsKey(item.getKey())) witnessed++;
          }
          victim.setRemainingFireTicks(60);
          require(
              expected.equals(counts(roll(level, table, victim, source, seed))),
              "Food-only cooking/count conservation: " + table + " seed=" + seed);
        }
        require(witnessed > 0, "A real food drop was exercised: " + table);
        victim.discard();
      }
      LogUtils.getLogger().info("Fire food table regression passed: tables={}", fixtures.size());
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read fire loot fixture", failure);
    }
  }

  private static void verifyModifiers(ServerLevel level, LivingEntity attacker, DamageSource fire) {
    LivingEntity skeleton = entity(level, "minecraft:skeleton");
    LivingEntity excluded = entity(level, "minecraft:iron_golem");
    LivingEntity pig = entity(level, "minecraft:pig");
    LivingEntity hoglin = entity(level, "minecraft:hoglin");
    var physical = level.damageSources().mobAttack(attacker);
    skeleton.setRemainingFireTicks(60);
    excluded.setRemainingFireTicks(60);
    int ashWitnesses = 0;
    int knifeWitnesses = 0;
    for (int seed = 1; seed <= 128; seed++) {
      var skeletonDrops = roll(level, "minecraft:entities/skeleton", skeleton, fire, seed);
      ashWitnesses += count(skeletonDrops, ASH);
      require(count(skeletonDrops, "minecraft:bone_meal") == 0, "Bones are not smelted");
      require(
          count(roll(level, "minecraft:entities/skeleton", skeleton, physical, seed), ASH) == 0,
          "Ash requires a fire lethal source");
      require(
          count(roll(level, "minecraft:entities/iron_golem", excluded, fire, seed), ASH) == 0,
          "Ash blacklist retained");
      attacker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
      require(
          count(
                  roll(level, "immersive_weathering:injects/pig_tallow", pig, fire, seed),
                  "immersive_weathering:tallow")
              == 0,
          "Plain-hand fire does not satisfy named enchantments");
      require(
          count(
                  roll(level, "immersive_weathering:injects/pig_tallow", pig, physical, seed),
                  "immersive_weathering:tallow")
              >= 1,
          "Nonfire tallow retained");
      int fireTallow =
          count(
              roll(level, "immersive_weathering:injects/hoglin_tallow", hoglin, fire, seed),
              "immersive_weathering:tallow");
      require(fireTallow >= 2 && fireTallow <= 4, "Hoglin retains its unconditional base tallow");
      pig.setRemainingFireTicks(60);
      require(
          count(roll(level, "minecraft:entities/pig", pig, fire, seed), "farmersdelight:smoked_ham")
              == 0,
          "Ham still requires a knife");
      attacker.setItemSlot(EquipmentSlot.MAINHAND, item("farmersdelight:iron_knife"));
      knifeWitnesses +=
          count(
              roll(level, "minecraft:entities/pig", pig, fire, seed), "farmersdelight:smoked_ham");
    }
    require(
        ashWitnesses > 0 && ashWitnesses < 128, "Native probabilistic ash route remains active");
    require(knifeWitnesses > 0, "Native knife and smoked-ham route remains active");
    for (String enchantment :
        List.of("minecraft:flame", "minecraft:fire_aspect", "minecraft:unbreaking")) {
      ItemStack weapon = new ItemStack(Items.IRON_SWORD);
      weapon.enchant(
          level
              .registryAccess()
              .lookupOrThrow(Registries.ENCHANTMENT)
              .getOrThrow(
                  ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(enchantment))),
          1);
      attacker.setItemSlot(EquipmentSlot.MAINHAND, weapon);
      int tallow =
          count(
              roll(level, "immersive_weathering:injects/pig_tallow", pig, fire, 13),
              "immersive_weathering:tallow");
      require(
          (tallow > 0) != enchantment.equals("minecraft:unbreaking"),
          "Exact named fire enchantment: " + enchantment);
    }
    for (LivingEntity victim : List.of(skeleton, excluded, pig, hoglin)) victim.discard();
    attacker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    LogUtils.getLogger().info("Fire global loot modifiers regression passed");
  }

  private static Death kill(LivingEntity victim, Consumer<LivingEntity> attack) {
    List<Death> deaths = new ArrayList<>();
    Consumer<LivingDropsEvent> capture =
        event -> {
          if (event.getEntity() != victim) return;
          deaths.add(
              new Death(
                  event.getDrops().stream().map(drop -> drop.getItem().copy()).toList(),
                  event.getSource(),
                  victim.isOnFire()));
          event.setCanceled(true);
        };
    NeoForge.EVENT_BUS.addListener(capture);
    try {
      attack.accept(victim);
      require(victim.isDeadOrDying() && deaths.size() == 1, "Native lethal damage/drop event");
      return deaths.getFirst();
    } finally {
      NeoForge.EVENT_BUS.unregister(capture);
      victim.discard();
    }
  }

  private static List<ItemStack> roll(
      ServerLevel level, String table, LivingEntity victim, DamageSource source, long seed) {
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, victim)
            .withParameter(LootContextParams.ORIGIN, victim.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, source)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
            .withOptionalParameter(
                LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
            .create(LootContextParamSets.ENTITY);
    return level
        .getServer()
        .reloadableRegistries()
        .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(table)))
        .getRandomItems(params, seed);
  }

  private static LivingEntity entity(ServerLevel level, String id) {
    var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
    require(type != null, "Registered entity: " + id);
    var entity = type.create(level);
    require(entity instanceof LivingEntity, "Living entity: " + id);
    var victim = (LivingEntity) entity;
    victim.setPos(
        level.getSharedSpawnPos().getX(),
        level.getSharedSpawnPos().getY() + 40,
        level.getSharedSpawnPos().getZ());
    victim.setHealth(victim.getMaxHealth());
    return victim;
  }

  private static ItemStack item(String id) {
    var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    require(item != Items.AIR, "Registered item: " + id);
    return new ItemStack(item);
  }

  private static int count(List<ItemStack> drops, String id) {
    return drops.stream()
        .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id))
        .mapToInt(ItemStack::getCount)
        .sum();
  }

  private static Map<String, Integer> counts(List<ItemStack> drops) {
    Map<String, Integer> result = new HashMap<>();
    for (var stack : drops) {
      String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
      if (!id.equals(ASH)) result.merge(id, stack.getCount(), Integer::sum);
    }
    return result;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
