package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.equipment.combat.GroundFootwork;
import dev.aeroscapes.equipment.combat.MartialTechnique;
import dev.aeroscapes.equipment.combat.MartialTechniques;
import dev.aeroscapes.magic.PreparedAbilities;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
          if (role == CharacterClass.ROGUE) {
            verifyFootwork(player);
            verifyDash(session);
          }
          if (role == CharacterClass.RANGER) {
            verifyAbilities(player);
            verifySlowingShot(player);
          }
        } finally {
          ProgressionRegression.listPlayer(level, player, false);
        }
      }
    }
    verifyDisengage(level, pos, 5);
    verifyDisengage(level, pos, 3);
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

  private static void verifyDash(TestShipPlayerSession session) {
    ServerPlayer player = session.player;
    var level = player.serverLevel();
    Vec3 start = player.position();
    var origin = player.blockPosition();
    var wallBase = origin.south(5);
    var wallTop = wallBase.above();
    var clock = level.getServer().getWorldData().overworldData();
    long gameTime = clock.getGameTime();
    require(level.isEmptyBlock(wallBase) && level.isEmptyBlock(wallTop), "Clear dash fixture");
    prepare(player, 0, MartialTechnique.DASH);
    prepare(player, 1, MartialTechnique.DISENGAGE);
    for (int index = -2; index <= 5; index++)
      level.setBlockAndUpdate(origin.south(index).below(), Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(wallBase, Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(wallTop, Blocks.STONE.defaultBlockState());
    try {
      player.setItemInHand(InteractionHand.MAIN_HAND, item("wetland_whimsy:dagger"));
      player.setOnGround(true);
      PreparedAbilities.select(player, 0);
      PreparedAbilities.cast(player);
      followFootwork(session, start);
      Vec3 stopped = player.position();
      close(stopped.x, start.x, "Dash follows facing");
      close(stopped.y, start.y, "Dash remains level at a full-height wall");
      require(
          stopped.z - start.z >= 4.0 && player.getBoundingBox().maxZ <= wallBase.getZ() + 0.0001,
          "Dash moves forward only as far as the wall allows");
      player.setOnGround(true);
      PreparedAbilities.select(player, 1);
      require(cooldown(session, MartialTechnique.DASH) > 0, "Dash starts its own cooldown");
      require(cooldown(session, MartialTechnique.DISENGAGE) == 0, "Dash leaves Disengage ready");
      PreparedAbilities.cast(player);
      require(
          cooldown(session, MartialTechnique.DISENGAGE) == 0,
          "Disengage cannot interrupt Dash's movement or spend its cooldown trying");
      clock.setGameTime(gameTime + GroundFootwork.DURATION);
      PreparedAbilities.cast(player);
      followFootwork(session, stopped);
      require(player.getZ() < stopped.z, "Disengage can retreat after Dash completes");
      require(
          cooldown(session, MartialTechnique.DASH) == 100 - GroundFootwork.DURATION
              && cooldown(session, MartialTechnique.DISENGAGE) == 100,
          "Successive techniques retain independent cooldown deadlines");
      require(MartialTechniques.dashAttack(player), "Dash prepares a light-weapon opening");
      var target = new Zombie(EntityType.ZOMBIE, level);
      target.setPos(player.position().add(0.85, 0, 0));
      require(level.addFreshEntity(target), "Spawn a Dash follow-up target");
      try {
        player.attack(target);
        require(
            target.getHealth() < target.getMaxHealth() && !MartialTechniques.dashAttack(player),
            "A native landed light-weapon hit consumes the Dash opening");
      } finally {
        target.discard();
      }
    } finally {
      clock.setGameTime(gameTime);
      level.removeBlock(wallTop, false);
      level.removeBlock(wallBase, false);
      for (int index = -2; index <= 5; index++)
        level.removeBlock(origin.south(index).below(), false);
    }
  }

  private static int cooldown(TestShipPlayerSession session, MartialTechnique technique) {
    return session.abilities().entries().stream()
        .filter(entry -> entry.id().equals(technique.id()))
        .findFirst()
        .orElseThrow()
        .cooldownTicks();
  }

  private static void verifyDisengage(ServerLevel level, Vec3 requestedStart, int floorLength) {
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "DisengageFixture", requestedStart, null)) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        require(
            RaceIdentity.choose(
                player,
                Race.HUMAN,
                0,
                CharacterClass.ROGUE,
                CharacterClass.ROGUE.starters().getFirst(),
                Profession.BLACKSMITH,
                "material_repair"),
            "The footwork fixture completes character creation before sending movement");
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
      Vec3 start = player.position();
      BlockPos origin = player.blockPosition();
      for (int index = 0; index <= floorLength; index++)
        level.setBlockAndUpdate(origin.north(index).below(), Blocks.STONE.defaultBlockState());
      try {
        prepare(player, 0, MartialTechnique.DISENGAGE);
        player.setOnGround(true);
        PreparedAbilities.select(player, 0);
        PreparedAbilities.cast(player);
        followFootwork(session, start);
        double retreat = start.z - player.getZ();
        close(player.getYRot(), 0.0, "Disengage preserves facing");
        close(player.getX(), start.x, "Disengage follows facing backwards");
        if (floorLength == 5) {
          close(retreat, 5.0, "Disengage covers five blocks on open ground");
          var attacker = new Zombie(EntityType.ZOMBIE, level);
          var arrow = new Arrow(EntityType.ARROW, level);
          var ranged = player.damageSources().arrow(arrow, attacker);
          var melee = player.damageSources().mobAttack(attacker);
          close(
              NeoForge.EVENT_BUS
                  .post(new LivingDamageEvent.Pre(player, new DamageContainer(ranged, 10.0F)))
                  .getNewDamage(),
              10.0,
              "A ranged hit preserves the Disengage protection");
          close(
              NeoForge.EVENT_BUS
                  .post(new LivingDamageEvent.Pre(player, new DamageContainer(melee, 10.0F)))
                  .getNewDamage(),
              5.0,
              "Disengage halves the first direct melee hit");
          close(
              NeoForge.EVENT_BUS
                  .post(new LivingDamageEvent.Pre(player, new DamageContainer(melee, 10.0F)))
                  .getNewDamage(),
              10.0,
              "The second melee hit deals normal damage");
        } else {
          require(retreat >= 3 && retreat < 4, "Disengage stops at an unsupported ledge");
        }
      } finally {
        for (int index = 0; index <= floorLength; index++)
          level.removeBlock(origin.north(index).below(), false);
      }
    }
  }

  private static void followFootwork(TestShipPlayerSession session, Vec3 start) {
    var player = session.player;
    require(player.position().equals(start), "Casting footwork does not jump to the endpoint");
    Vec3 travel = session.footworkTravel();
    for (int tick = 0; tick < GroundFootwork.DURATION; tick++) {
      Vec3 stride = GroundFootwork.motion(travel, tick);
      require(
          stride.lengthSqr() > 0 && stride.lengthSqr() < travel.lengthSqr(),
          "Each tick covers only part of the path");
      session.moveOnGround(player.position().add(stride));
    }
    close(player.position().distanceTo(start.add(travel)), 0.0, "All strides reach the endpoint");
  }

  private static void verifySlowingShot(ServerPlayer player) {
    var level = player.serverLevel();
    var bow = new ItemStack(Items.BOW);
    player.setItemInHand(InteractionHand.MAIN_HAND, bow);
    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2));
    player.setYRot(0);
    player.setXRot(0);
    player.setDeltaMovement(Vec3.ZERO);
    prepare(player, 0, MartialTechnique.SLOWING_SHOT);
    PreparedAbilities.select(player, 0);
    PreparedAbilities.cast(player);

    var target = new Zombie(EntityType.ZOMBIE, level);
    target.setPos(player.position().add(0, 0, 3.5));
    require(level.addFreshEntity(target), "Spawn a Slowing Shot target");
    try {
      player.startUsingItem(InteractionHand.MAIN_HAND);
      Items.BOW.releaseUsing(bow, level, player, bow.getUseDuration(player) - 12);
      player.stopUsingItem();
      require(player.getOffhandItem().getCount() == 1, "Slowing Shot spends one ordinary arrow");
      var arrows =
          level.getEntitiesOfClass(
              AbstractArrow.class,
              player.getBoundingBox().inflate(4),
              arrow -> arrow.getOwner() == player);
      try {
        require(arrows.size() == 1, "Slowing Shot releases one native arrow");
        var arrow = arrows.getFirst();
        for (int tick = 0; tick < 4 && !arrow.isRemoved(); tick++) arrow.tick();
        var slowness = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        require(
            target.getHealth() < target.getMaxHealth()
                && slowness != null
                && slowness.getAmplifier() == 3
                && slowness.getDuration() == 80,
            "An ordinary arrow hit applies four seconds of Slowness IV");
      } finally {
        arrows.forEach(AbstractArrow::discard);
      }
    } finally {
      target.discard();
    }
  }

  private static void prepare(ServerPlayer player, int slot, MartialTechnique technique) {
    SkillsMod.getInstance()
        .tryUnlockSkill(player, COMBAT, ResourceLocation.parse(technique.id()).getPath(), true);
    PreparedAbilities.edit(player, slot, technique.id());
    require(
        PreparedAbilities.preparation(player).slots().get(slot).equals(technique.id()),
        "Prepare " + technique.id());
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
        verifyBlockStartsOn(player, 8);
        player.stopUsingItem();
        provider.tryUnlockSkill(player, COMBAT, "shield_handling", false);
        require(
            provider.getSkillState(player, COMBAT, "shield_handling").orElseThrow()
                == Skill.State.UNLOCKED,
            "Shield Handling is available to this character");
        player.startUsingItem(InteractionHand.MAIN_HAND);
        verifyBlockStartsOn(player, 3);
      }
      player.stopUsingItem();
      ClassTraits.refresh(player);
      close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), walking, "Stop using " + item);
    }
    verifyBow(player);
    verifyRangedProficiency(player);
    verifyMeleeProficiency(player);
    verifyKnife(player);
  }

  private static void verifyBlockStartsOn(ServerPlayer player, int delay) {
    for (int tick = 0; tick < delay; tick++) {
      require(!player.isBlocking(), "Shield blocked prematurely");
      player.doTick();
    }
    require(player.isBlocking(), "Shield should begin blocking on tick " + delay);
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

  private static void verifyMeleeProficiency(ServerPlayer player) {
    APIUtils.setLevel("combat", player, 25);
    ProgressionBridge.sync(player);
    var sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.enchant(
        player
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SHARPNESS),
        2);
    player.setItemInHand(InteractionHand.MAIN_HAND, sword);
    float untrained = player.getCurrentItemAttackStrengthDelay();
    require(learnHandling(player, "sword_handling"), "Buy sword handling");
    require(learnHandling(player, "advanced_sword_handling"), "Buy advanced sword handling");
    close(
        untrained,
        player.getCurrentItemAttackStrengthDelay() * 1.5,
        "Same enchanted sword gains full recovery after training");
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
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
