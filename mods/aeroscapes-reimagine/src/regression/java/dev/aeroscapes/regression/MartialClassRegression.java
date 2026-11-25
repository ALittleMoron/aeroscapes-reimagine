package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.ClassTraits;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.weapons.AutoloaderCrossbow;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

final class MartialClassRegression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);

  private MartialClassRegression() {}

  static void verify(ServerLevel level) {
    var pos = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    for (CharacterClass role : CharacterClass.values()) {
      try (var session =
          TestShipPlayerSession.create(level, UUID.randomUUID(), "MartialFixture", pos, null)) {
        ServerPlayer player = session.player;
        ProgressionRegression.listPlayer(level, player, true);
        try {
          PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
          double armor = player.getAttributeValue(Attributes.ARMOR);
          double mana = player.getAttributeValue(AttributeRegistry.MAX_MANA);
          require(
              RaceIdentity.choose(
                  player,
                  Race.HUMAN,
                  0,
                  role,
                  role.starters().getFirst(),
                  Profession.BLACKSMITH,
                  "material_repair"),
              "Initial class selection");
          close(
              player.getAttributeValue(Attributes.ARMOR),
              armor + (role == CharacterClass.WARRIOR ? 2 : 0),
              "Warrior armor");
          close(
              player.getAttributeValue(AttributeRegistry.MAX_MANA),
              mana + (role == CharacterClass.MAGE ? 20 : 0),
              "Mage mana capacity");
          if (role == CharacterClass.ROGUE) verifyFootwork(player);
          if (role == CharacterClass.RANGER) verifyAbilities(player);
        } finally {
          ProgressionRegression.listPlayer(level, player, false);
        }
      }
    }
  }

  private static void verifyFootwork(ServerPlayer player) {
    player.setOnGround(true);
    double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
    player.setItemInHand(InteractionHand.MAIN_HAND, item("wetland_whimsy:dagger"));
    ClassTraits.refresh(player);
    close(
        player.getAttributeValue(Attributes.MOVEMENT_SPEED),
        speed * 1.05,
        "Rogue carrying a dagger");
    player.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_AXE.getDefaultInstance());
    ClassTraits.refresh(player);
    close(
        player.getAttributeValue(Attributes.MOVEMENT_SPEED),
        speed,
        "A held axe removes light-weapon footwork");
  }

  private static void verifyAbilities(ServerPlayer player) {
    var provider = SkillsMod.getInstance();
    provider.setPoints(
        player, COMBAT, ResourceLocation.parse("aeroscapes_reimagine:martial_fixture"), 6, true);
    require(
        provider.getSkillState(player, COMBAT, "bow_stride").orElseThrow() == Skill.State.UNLOCKED,
        "Chosen passive");
    for (String skill :
        List.of("quick_block", "shield_march", "steady_aim", "quick_recovery", "opening_strike")) {
      provider.tryUnlockSkill(player, COMBAT, skill, false);
      require(
          provider.getSkillState(player, COMBAT, skill).orElseThrow() == Skill.State.UNLOCKED,
          "Ordinary cross-class purchase of " + skill);
    }
    verifyShieldSync(player);
    double walking = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
    for (var item : List.of(Items.SHIELD, Items.BOW)) {
      player.setItemInHand(InteractionHand.MAIN_HAND, item.getDefaultInstance());
      player.startUsingItem(InteractionHand.MAIN_HAND);
      ClassTraits.refresh(player);
      close(
          player.getAttributeValue(Attributes.MOVEMENT_SPEED),
          walking * 1.25,
          "Walking while using " + item);
      if (item == Items.SHIELD) {
        for (int tick = 0; tick < 3; tick++) {
          require(!player.isBlocking(), "Shield blocked prematurely");
          player.doTick();
        }
        require(player.isBlocking(), "A learned shield blocks on tick three");
      }
      player.stopUsingItem();
      ClassTraits.refresh(player);
      close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), walking, "Stop using " + item);
    }
    verifyBow(player);
    verifyRangedProficiency(player);
    verifyKnife(player);
  }

  private static void verifyShieldSync(ServerPlayer player) {
    var attributes = player.getAttributes();
    for (var outgoing :
        List.of(attributes.getAttributesToSync(), attributes.getSyncableAttributes())) {
      var packet = new ClientboundUpdateAttributesPacket(player.getId(), outgoing);
      var snapshot =
          packet.getValues().stream()
              .filter(
                  value ->
                      value
                          .attribute()
                          .is(
                              ResourceLocation.parse(
                                  "aeroscapes_reimagine:shield_raise_reduction")))
              .findFirst()
              .orElseThrow();
      close(
          snapshot.base()
              + snapshot.modifiers().stream().mapToDouble(AttributeModifier::amount).sum(),
          2,
          "Both ongoing and initial tracking packets contain the off-class shield reward");
    }
    require(ClassTraits.shieldDelay(player, 5) == 5, "Empty hands have no block bonus");
    var mob = new Zombie(player.level());
    mob.setItemInHand(InteractionHand.MAIN_HAND, Items.SHIELD.getDefaultInstance());
    mob.startUsingItem(InteractionHand.MAIN_HAND);
    require(ClassTraits.shieldDelay(mob, 5) == 5, "A shield-bearing mob keeps the normal delay");
  }

  private static void verifyBow(ServerPlayer player) {
    player.setOnGround(true);
    player.setKnownMovement(Vec3.ZERO);
    close(ClassTraits.bowSpread(player, 1), 0.5, "Steady shot");
    player.setKnownMovement(new Vec3(0.14, 0, 0));
    close(ClassTraits.bowSpread(player, 1), 1, "Moving shot");
    player.setKnownMovement(Vec3.ZERO);
    player.setDeltaMovement(Vec3.ZERO);
    player.setOnGround(false);
    close(ClassTraits.bowSpread(player, 1), 1, "Airborne shot");
    var bow = Items.BOW.getDefaultInstance();
    player.setItemInHand(InteractionHand.MAIN_HAND, bow);
    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2));
    for (int draw : List.of(20, 12)) {
      Items.BOW.releaseUsing(bow, player.level(), player, bow.getUseDuration(player) - draw);
      var arrows =
          player
              .level()
              .getEntitiesOfClass(
                  AbstractArrow.class,
                  player.getBoundingBox().inflate(4),
                  arrow -> arrow.getOwner() == player);
      require(arrows.size() == 1, "One actual ranger bow shot");
      double expected =
          net.minecraft.world.item.BowItem.getPowerForTime(draw) * 3 * (draw == 20 ? 1.1 : 1);
      require(
          Math.abs(arrows.getFirst().getDeltaMovement().length() - expected) < 0.10,
          "Ranger launch speed at " + draw + " ticks");
      arrows.getFirst().discard();
    }
  }

  private static void verifyRangedProficiency(ServerPlayer player) {
    var crossbow = Items.CROSSBOW.getDefaultInstance();
    var autoloader = item("irons_spellbooks:autoloader_crossbow");
    APIUtils.setLevel("combat", player, 20);
    APIUtils.setLevel("archery", player, 0);
    ProgressionBridge.sync(player);
    require(!learnHandling(player, "crossbow_handling"), "Combat points do not replace practice");
    int slowCrossbow = CrossbowItem.getChargeDuration(crossbow, player);
    var quickCrossbow = crossbow.copy();
    quickCrossbow.enchant(
        player
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.QUICK_CHARGE),
        1);
    int noviceQuickCharge = slowCrossbow - CrossbowItem.getChargeDuration(quickCrossbow, player);
    int slowAutoloader = AutoloaderCrossbow.getChargeDuration(autoloader, player);
    double slowArrow = starlightShot(player);
    APIUtils.setLevel("archery", player, 2);
    require(
        slowCrossbow == CrossbowItem.getChargeDuration(crossbow, player),
        "Reaching the ranged level does not restore loading without a talent");
    require(learnHandling(player, "crossbow_handling"), "Buy crossbow handling");
    require(
        slowCrossbow > CrossbowItem.getChargeDuration(crossbow, player),
        "The corresponding talent restores the ordinary crossbow's loading time");
    require(
        noviceQuickCharge > 0
            && noviceQuickCharge
                == CrossbowItem.getChargeDuration(crossbow, player)
                    - CrossbowItem.getChargeDuration(quickCrossbow, player),
        "Quick Charge retains its normal contribution before and after training");
    require(
        slowAutoloader == AutoloaderCrossbow.getChargeDuration(autoloader, player),
        "The ordinary crossbow talent does not train the autoloader");
    APIUtils.setLevel("archery", player, 6);
    require(
        Math.abs(starlightShot(player) - slowArrow) < 0.1,
        "Practice prepares the Starlight talent");
    require(learnHandling(player, "starlight_bow_handling"), "Buy Starlight Bow handling");
    require(starlightShot(player) > slowArrow + 0.4, "Talent restores the Starlight Bow's draw");
    require(!learnHandling(player, "autoloader_handling"), "Autoloader requires its own practice");
    APIUtils.setLevel("archery", player, 10);
    require(
        slowAutoloader == AutoloaderCrossbow.getChargeDuration(autoloader, player),
        "Reaching the ranged level does not restore autoloading without a talent");
    require(learnHandling(player, "autoloader_handling"), "Buy autoloader handling");
    require(
        slowAutoloader > AutoloaderCrossbow.getChargeDuration(autoloader, player),
        "Talent restores the autoloader's native loading time");
  }

  private static boolean learnHandling(ServerPlayer player, String node) {
    var provider = SkillsMod.getInstance();
    provider.tryUnlockSkill(player, COMBAT, node, false);
    return provider.getSkillState(player, COMBAT, node).orElseThrow() == Skill.State.UNLOCKED;
  }

  private static double starlightShot(ServerPlayer player) {
    var bow = item("tide:starlight_bow");
    player.setItemInHand(InteractionHand.MAIN_HAND, bow);
    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW));
    ((BowItem) bow.getItem())
        .releaseUsing(bow, player.level(), player, bow.getUseDuration(player) - 20);
    var arrows =
        player
            .level()
            .getEntitiesOfClass(
                AbstractArrow.class,
                player.getBoundingBox().inflate(4),
                arrow -> arrow.getOwner() == player);
    require(arrows.size() == 1, "One actual Starlight Bow shot");
    var arrow = arrows.getFirst();
    double speed = arrow.getDeltaMovement().length();
    arrow.discard();
    return speed;
  }

  private static void verifyKnife(ServerPlayer player) {
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    ClassTraits.refresh(player);
    double baseline = player.getAttributeValue(Attributes.ATTACK_SPEED);
    var target = new Pig(EntityType.PIG, player.level());
    for (String id :
        List.of(
            "farmersdelight:flint_knife",
            "farmersdelight:iron_knife",
            "farmersdelight:golden_knife",
            "farmersdelight:diamond_knife",
            "farmersdelight:netherite_knife",
            "irons_spellbooks:amethyst_rapier",
            "wetland_whimsy:dagger")) {
      ItemStack weapon = item(id);
      player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
      ClassTraits.refresh(player);
      close(
          player.getAttributeValue(Attributes.ATTACK_SPEED),
          baseline * 1.1,
          "Quick recovery " + id);
      close(ClassTraits.openingDamage(player, target, weapon, 1, 10), 11.5, "Opening damage " + id);
      close(ClassTraits.openingDamage(player, target, weapon, 0.98F, 10), 10, "Uncharged " + id);
    }
    target.setHealth(target.getMaxHealth() - 1);
    close(
        ClassTraits.openingDamage(player, target, player.getMainHandItem(), 1, 10),
        10,
        "Injured target");
    player.setItemInHand(InteractionHand.MAIN_HAND, Items.WOODEN_SWORD.getDefaultInstance());
    ClassTraits.refresh(player);
    close(player.getAttributeValue(Attributes.ATTACK_SPEED), baseline, "Ordinary sword recovery");
  }

  private static ItemStack item(String id) {
    return BuiltInRegistries.ITEM
        .getOptional(ResourceLocation.parse(id))
        .orElseThrow()
        .getDefaultInstance();
  }

  private static void close(double actual, double expected, String message) {
    require(
        Math.abs(actual - expected) < 0.0001, message + ": " + actual + " expected " + expected);
  }

  private static void require(boolean value, String message) {
    if (!value) throw new IllegalStateException(message);
  }
}
