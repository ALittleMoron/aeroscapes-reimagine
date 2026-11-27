package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import com.herobrot.tieredneo.api.ModifierUtils;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.FieldRepairKit;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

final class FieldRepairRegression {
  private static final String TALENT = "field_repair";
  private static final int MATERIAL_SLOT = 10;
  private static final int USE_TICKS = 40;

  private record Supplies(ItemStack kit, ItemStack target, ItemStack material) {}

  private FieldRepairRegression() {}

  static void verify(ServerLevel level) {
    var origin = level.getSharedSpawnPos().above(28);
    var floor = origin.below();
    var previousFloor = level.getBlockState(floor);
    level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "FieldRepair", Vec3.atBottomCenterOf(origin), null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        require(
            RaceIdentity.choose(
                player,
                Race.HUMAN,
                0,
                CharacterClass.WARRIOR,
                "quick_block",
                Profession.FARMER,
                "extra_harvest"),
            "Complete character setup before testing repair and damage");
        player.setOnGround(true);
        for (int tick = 0; tick < 60; tick++) {
          // Spawn protection advances on the outer server tick, while item use uses doTick.
          player.tick();
          player.doTick();
        }

        var supplies =
            prepare(player, Items.IRON_PICKAXE, Items.IRON_INGOT, InteractionHand.MAIN_HAND);
        var unchanged = supplies.target().copy();
        require(!start(player, InteractionHand.MAIN_HAND), "A purchased kit requires Field Repair");
        assertUntouched(supplies, unchanged, "Unlearned use");

        SkillsMod.getInstance()
            .tryUnlockSkill(player, ProfessionDiscipline.SMITHING.category(), TALENT, true);
        checkRepair(player, Items.IRON_PICKAXE, Items.IRON_INGOT, InteractionHand.MAIN_HAND);
        checkRepair(player, Items.IRON_SWORD, Items.IRON_INGOT, InteractionHand.MAIN_HAND);
        checkRepair(player, Items.IRON_CHESTPLATE, Items.IRON_INGOT, InteractionHand.OFF_HAND);
        checkRepair(
            player,
            registered("overgeared:copper_pickaxe"),
            Items.COPPER_INGOT,
            InteractionHand.MAIN_HAND);
        checkRepair(
            player,
            registered("overgeared:steel_chestplate"),
            registered("overgeared:steel_ingot"),
            InteractionHand.MAIN_HAND);
        failedMaterial(player);
        interruptions(player);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    } finally {
      level.setBlockAndUpdate(floor, previousFloor);
    }
  }

  private static void checkRepair(
      ServerPlayer player, Item targetItem, Item material, InteractionHand kitHand) {
    var supplies = prepare(player, targetItem, material, kitHand);
    var target = supplies.target();
    var expected = target.copy();
    expected.setDamageValue(target.getDamageValue() - Math.max(1, target.getMaxDamage() / 8));
    if (kitHand == InteractionHand.OFF_HAND) {
      var mainAction =
          player.gameMode.useItem(player, player.level(), target, InteractionHand.MAIN_HAND);
      require(
          mainAction == InteractionResult.PASS && player.getMainHandItem() == target,
          "An offhand kit lets the intended repair start without auto-equipping main-hand armor");
    }
    require(start(player, kitHand), "Native item use starts field repair for " + targetItem);
    Vec3 start = player.position();
    for (int tick = 1; tick < USE_TICKS; tick++) {
      advance(player, 1);
      require(
          player.isUsingItem() && player.getUseItemRemainingTicks() == USE_TICKS - tick,
          "The native repair channel must be active at tick "
              + tick
              + " for "
              + targetItem
              + " (remaining "
              + player.getUseItemRemainingTicks()
              + ", movement "
              + player.position().distanceTo(start)
              + ")");
    }
    require(
        supplies.kit().getCount() == 2 && supplies.material().getCount() == 2,
        "No supplies are paid before the final tick");
    advance(player, 1);
    var repaired = player.getItemInHand(other(kitHand));
    require(repaired == target, "The repaired item remains in the opposite hand");
    require(
        supplies.kit().getCount() == 1 && supplies.material().getCount() == 1,
        "Success consumes one kit and ingredient (kit "
            + supplies.kit().getCount()
            + ", ingredient "
            + supplies.material().getCount()
            + ", damage "
            + repaired.getDamageValue()
            + ", expected "
            + expected.getDamageValue()
            + ")");
    require(
        ItemStack.matches(expected, repaired),
        "Success repairs exactly the same enchanted/reforged item (damage "
            + repaired.getDamageValue()
            + ", expected "
            + expected.getDamageValue()
            + ")");
  }

  private static void failedMaterial(ServerPlayer player) {
    var supplies =
        prepare(player, Items.IRON_CHESTPLATE, Items.COPPER_INGOT, InteractionHand.MAIN_HAND);
    var target = supplies.target().copy();
    require(!start(player, InteractionHand.MAIN_HAND), "Wrong ingredient cannot begin repair");
    assertUntouched(supplies, target, "Wrong ingredient");
  }

  private static void interruptions(ServerPlayer player) {
    var supplies = prepare(player, Items.IRON_PICKAXE, Items.IRON_INGOT, InteractionHand.MAIN_HAND);
    var target = supplies.target().copy();
    require(start(player, InteractionHand.MAIN_HAND), "Start before releasing");
    advance(player, 20);
    player.releaseUsingItem();
    advance(player, USE_TICKS);
    assertUntouched(supplies, target, "Early release");

    require(start(player, InteractionHand.MAIN_HAND), "Start before moving");
    Vec3 position = player.position();
    player.setPos(position.add(0.5, 0, 0));
    advance(player, 1);
    player.setPos(position);
    advance(player, USE_TICKS);
    assertUntouched(supplies, target, "Movement");

    require(start(player, InteractionHand.MAIN_HAND), "Start before removing material");
    player.getInventory().setItem(MATERIAL_SLOT, Items.IRON_INGOT.getDefaultInstance());
    advance(player, 1);
    player.getInventory().setItem(MATERIAL_SLOT, supplies.material());
    advance(player, USE_TICKS);
    assertUntouched(supplies, target, "Ingredient changed");

    require(start(player, InteractionHand.MAIN_HAND), "Start before taking damage");
    var attacker = new Zombie(player.level());
    attacker.setPos(player.position().add(0, 0, 2));
    float health = player.getHealth();
    player.invulnerableTime = 0;
    require(
        player.hurt(player.damageSources().mobAttack(attacker), 2) && player.getHealth() < health,
        "An ordinary mob hit deals damage after spawn protection expires");
    require(!player.isUsingItem(), "Damage stops the repair channel immediately");
    advance(player, USE_TICKS);
    assertUntouched(supplies, target, "Combat interruption");

    require(start(player, InteractionHand.MAIN_HAND), "Start before swapping target");
    player.setItemInHand(InteractionHand.OFF_HAND, target.copy());
    advance(player, 1);
    player.setItemInHand(InteractionHand.OFF_HAND, supplies.target());
    advance(player, USE_TICKS);
    assertUntouched(supplies, target, "Target changed");
  }

  private static Supplies prepare(
      ServerPlayer player, Item targetItem, Item material, InteractionHand kitHand) {
    var kit = new ItemStack(FieldRepairKit.ITEM.get(), 2);
    var target = new ItemStack(targetItem);
    if (targetItem == Items.IRON_PICKAXE) {
      var fine =
          ReforgeProfiles.paidGrades(targetItem).stream()
              .filter(grade -> grade.key().equals("fine"))
              .findFirst()
              .orElseThrow();
      var tier = fine.outcomes().getFirst().id();
      ModifierUtils.applyTierToStack(target, tier, TieredNeoAPI.getTierAttribute(tier));
    }
    target.set(DataComponents.CUSTOM_NAME, Component.literal("Original field witness"));
    target.enchant(
        player
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.UNBREAKING),
        2);
    target.setDamageValue(target.getMaxDamage() / 2);
    var ingredient = new ItemStack(material, 2);
    player.setItemInHand(kitHand, kit);
    player.setItemInHand(other(kitHand), target);
    player.getInventory().setItem(MATERIAL_SLOT, ingredient);
    return new Supplies(kit, target, ingredient);
  }

  private static boolean start(ServerPlayer player, InteractionHand hand) {
    var result = player.gameMode.useItem(player, player.level(), player.getItemInHand(hand), hand);
    return result.consumesAction() && player.isUsingItem() && player.getUsedItemHand() == hand;
  }

  private static void advance(ServerPlayer player, int ticks) {
    for (int index = 0; index < ticks; index++) player.doTick();
  }

  private static void assertUntouched(Supplies supplies, ItemStack expected, String context) {
    require(
        supplies.kit().getCount() == 2
            && supplies.material().getCount() == 2
            && ItemStack.matches(supplies.target(), expected),
        context + " keeps both supplies and the original damage");
  }

  private static InteractionHand other(InteractionHand hand) {
    return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
  }

  private static Item registered(String id) {
    return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
