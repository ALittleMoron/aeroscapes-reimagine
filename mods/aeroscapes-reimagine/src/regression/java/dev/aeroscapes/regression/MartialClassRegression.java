package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.equipment.combat.BerserkerTechniques;
import dev.aeroscapes.equipment.combat.GroundFootwork;
import dev.aeroscapes.equipment.combat.MartialArchery;
import dev.aeroscapes.equipment.combat.MartialTechnique;
import dev.aeroscapes.equipment.combat.MartialTechniques;
import dev.aeroscapes.equipment.combat.SwingTempo;
import dev.aeroscapes.magic.AbilityNetwork;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.progression.CombatDirection;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.ClassTraits;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.weapons.AutoloaderCrossbow;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

final class MartialClassRegression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);

  private static final ResourceLocation RANGER =
      ResourceLocation.parse(ProgressionBridge.RANGER_CATEGORY);

  private MartialClassRegression() {}

  static void verify(ServerLevel level) {
    var pos = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    for (CharacterClass role : CharacterClass.selectable()) {
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
          if (role == CharacterClass.RANGER) {
            verifyAbilities(player);
            player.setNoGravity(true);
            verifyBowCancellations(session);
            verifySlowingShot(session);
            verifyMarkingShot(session);
            verifyQuickShot(session);
            verifyTripleShot(session);
            verifyTripleShotAmmo(session);
          }
          if (role == CharacterClass.WARRIOR) {
            verifyShieldShove(session);
            verifyLastStand(player);
          }
        } finally {
          ProgressionRegression.listPlayer(level, player, false);
        }
      }
    }
    verifyDash(level, pos);
    verifyDisengage(level, pos, 5);
    verifyDisengage(level, pos, 3);
    verifySmokeArrow(level, pos);
    verifyDuelistSidestep(level, pos);
  }

  private static Zombie opponent(ServerPlayer player, double x, double z) {
    var target = new Zombie(EntityType.ZOMBIE, player.serverLevel());
    target.setPos(player.position().add(x, 0.0, z));
    require(player.serverLevel().addFreshEntity(target), "Register combat fixture opponent");
    return target;
  }

  private static void verifyShieldShove(TestShipPlayerSession session) {
    ServerPlayer player = session.player;
    var clock = player.serverLevel().getServer().getWorldData().overworldData();
    long gameTime = clock.getGameTime();
    player.setYRot(0.0F);
    player.setNoGravity(true);
    for (int tick = 0; tick < 60; tick++) player.tick();
    player.setItemInHand(InteractionHand.OFF_HAND, Items.SHIELD.getDefaultInstance());
    prepare(player, 0, MartialTechnique.SHIELD_SHOVE);
    PreparedAbilities.select(player, 0);
    PreparedAbilities.cast(player);
    require(cooldown(session, MartialTechnique.SHIELD_SHOVE) == 0, "No block means no shove");

    var first = opponent(player, 0.0, 1.5);
    var second = opponent(player, 1.1, 1.5);
    var behind = opponent(player, 0.0, -1.5);
    try {
      blockMelee(player, first);
      PreparedAbilities.sync(player, true);
      var shove =
          session.abilities().entries().stream()
              .filter(entry -> entry.id().equals(MartialTechnique.SHIELD_SHOVE.id()))
              .findFirst()
              .orElseThrow();
      require(
          shove.usable(),
          "A blocked attacker in reach opens Shield Shove: "
              + shove.reason()
              + " at distance "
              + player.distanceTo(first));
      PreparedAbilities.cast(player);
      require(first.getDeltaMovement().z > 0.4, "A real shield block opens a shove");
      require(second.getDeltaMovement().equals(Vec3.ZERO), "The first shove affects one attacker");
      require(cooldown(session, MartialTechnique.SHIELD_SHOVE) > 0, "Shove starts its cooldown");
      first.setDeltaMovement(Vec3.ZERO);
      setCombatTalent(player, "wide_shield_shove");
      clock.setGameTime(gameTime + MartialTechnique.SHIELD_SHOVE.cooldownTicks());
      PreparedAbilities.cast(player);
      require(
          first.getDeltaMovement().equals(Vec3.ZERO),
          "The same block cannot be used for another shove");
      blockMelee(player, first);
      PreparedAbilities.cast(player);
      require(
          first.getDeltaMovement().z > 0.4 && second.getDeltaMovement().z > 0.4,
          "Wider shove pushes the attacker and nearby enemies in front");
      require(behind.getDeltaMovement().equals(Vec3.ZERO), "Wider shove leaves enemies behind");
    } finally {
      clock.setGameTime(gameTime);
      first.discard();
      second.discard();
      behind.discard();
      player.setNoGravity(false);
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }
  }

  private static void blockMelee(ServerPlayer player, Zombie attacker) {
    player.invulnerableTime = 0;
    player.setDeltaMovement(Vec3.ZERO);
    player.startUsingItem(InteractionHand.OFF_HAND);
    for (int tick = 0; tick < 12 && !player.isBlocking(); tick++) player.doTick();
    require(player.isBlocking(), "Shield is raised before the incoming blow");
    float health = player.getHealth();
    int shieldDamage = player.getOffhandItem().getDamageValue();
    player.hurt(player.damageSources().mobAttack(attacker), 4.0F);
    player.stopUsingItem();
    close(player.getHealth(), health, "The fixture blocks a real melee hit");
    require(
        player.getOffhandItem().getDamageValue() > shieldDamage, "A real block wears the shield");
  }

  private static void verifyLastStand(ServerPlayer player) {
    setCombatTalent(player, "last_stand");
    player.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_AXE.getDefaultInstance());
    var attacker = opponent(player, 0.0, 1.5);
    var target = opponent(player, 0.0, 2.4);
    try {
      float startingHealth = player.getMaxHealth() * 0.45F;
      player.setHealth(startingHealth);
      player.invulnerableTime = 0;
      player.hurt(player.damageSources().fall(), 4.0F);
      require(!BerserkerTechniques.lastStandReady(player), "Falls cannot prime Last Stand");
      player.setHealth(startingHealth);
      player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 80, 1));
      player.invulnerableTime = 0;
      player.hurt(player.damageSources().mobAttack(attacker), 4.0F);
      require(!BerserkerTechniques.lastStandReady(player), "Absorbed hits do not lower health");
      player.removeEffect(MobEffects.ABSORPTION);
      player.invulnerableTime = 0;
      player.hurt(player.damageSources().mobAttack(attacker), 4.0F);
      require(BerserkerTechniques.lastStandReady(player), "Enemy hit crosses the health threshold");
      player.resetAttackStrengthTicker();
      player.attack(target);
      require(BerserkerTechniques.lastStandReady(player), "Early melee hit leaves the reply ready");
      target.invulnerableTime = 0;
      target.setHealth(target.getMaxHealth());
      for (int tick = 0; tick < 60 && player.getAttackStrengthScale(0.5F) < 1.0F; tick++)
        player.doTick();
      require(player.getAttackStrengthScale(0.5F) == 1.0F, "Fixture charges a heavy attack");
      player.attack(target);
      require(!BerserkerTechniques.lastStandReady(player), "A charged hit consumes the bonus");
    } finally {
      attacker.discard();
      target.discard();
    }
  }

  private static void setCombatTalent(ServerPlayer player, String skill) {
    SkillsMod.getInstance()
        .tryUnlockSkill(player, CombatDirection.categoryForNode(skill), skill, true);
    require(
        SkillsMod.getInstance()
                .getSkillState(player, CombatDirection.categoryForNode(skill), skill)
                .orElseThrow()
            == Skill.State.UNLOCKED,
        "Fixture unlocks " + skill);
  }

  private static void verifyDash(ServerLevel level, Vec3 position) {
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "DashFixture", position, null)) {
      chooseFootworkRanger(level, session.player);
      verifyDash(session);
    }
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
    return techniqueEntry(session, technique).cooldownTicks();
  }

  private static AbilityNetwork.Entry techniqueEntry(
      TestShipPlayerSession session, MartialTechnique technique) {
    return session.abilities().entries().stream()
        .filter(entry -> entry.id().equals(technique.id()))
        .findFirst()
        .orElseThrow();
  }

  private static void chooseFootworkRanger(ServerLevel level, ServerPlayer player) {
    ProgressionRegression.listPlayer(level, player, true);
    try {
      PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
      require(
          RaceIdentity.choose(
              player,
              Race.HUMAN,
              0,
              CharacterClass.RANGER,
              CharacterClass.RANGER.starters().getFirst(),
              Profession.BLACKSMITH,
              "material_repair"),
          "The footwork fixture completes character creation before sending movement");
    } finally {
      ProgressionRegression.listPlayer(level, player, false);
    }
  }

  private static void verifyDisengage(ServerLevel level, Vec3 requestedStart, int floorLength) {
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "DisengageFixture", requestedStart, null)) {
      ServerPlayer player = session.player;
      chooseFootworkRanger(level, player);
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

  private static void castBow(TestShipPlayerSession session, MartialTechnique technique) {
    session.receivePlayPayload(new AbilityNetwork.Cast(), "6");
    require(
        session.player.isUsingItem()
            && techniqueEntry(session, technique).reason().equals(MartialArchery.DRAWING)
            && cooldown(session, technique) == 0
            && ownedArrows(session.player).isEmpty(),
        "Cast starts the native bow draw without launching or spending cooldown");
  }

  private static void bowTicks(ServerPlayer player, int ticks) {
    var clock = player.serverLevel().getServer().getWorldData().overworldData();
    for (int tick = 0; tick < ticks; tick++) {
      clock.setGameTime(clock.getGameTime() + 1);
      player.doTick();
    }
  }

  private static void verifyBowCancellations(TestShipPlayerSession session) {
    var player = session.player;
    var technique = MartialTechnique.SLOWING_SHOT;
    var bow = new ItemStack(Items.BOW);
    var another = bow.copy();
    session.selectHotbarSlot(0);
    player.setItemInHand(InteractionHand.MAIN_HAND, bow);
    player.getInventory().setItem(1, another);
    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2));
    prepare(player, 0, technique);
    PreparedAbilities.select(player, 0);

    castBow(session, technique);
    bowTicks(player, 6);
    session.releaseUsingItem();
    require(
        player.isUsingItem() && ownedArrows(player).isEmpty(),
        "A native early release packet cannot interrupt or fire an ability draw");
    session.selectHotbarSlot(1);
    session.selectHotbarSlot(0);
    session.releaseUsingItem();
    bowTicks(player, BowItem.MAX_DRAW_DURATION);
    require(
        !player.isUsingItem()
            && cooldown(session, technique) == 0
            && player.getOffhandItem().getCount() == 2
            && bow.getDamageValue() == 0
            && another.getDamageValue() == 0
            && ownedArrows(player).isEmpty(),
        "Switching between identical bows cancels even when switched back on the same tick");
    require(
        session.abilities().loadout().active().slots().getFirst().equals(technique.id()),
        "Switching bows leaves the ability prepared");

    castBow(session, technique);
    bowTicks(player, 6);
    session.swapHands();
    session.swapHands();
    bowTicks(player, BowItem.MAX_DRAW_DURATION);
    require(
        !player.isUsingItem()
            && player.getMainHandItem() == bow
            && cooldown(session, technique) == 0
            && player.getOffhandItem().getCount() == 2
            && ownedArrows(player).isEmpty(),
        "The native hand swap also cancels the prepared draw without paying for a shot");

    player.gameMode.useItem(player, player.serverLevel(), bow, InteractionHand.MAIN_HAND);
    bowTicks(player, 6);
    session.releaseUsingItem();
    var manualShot = ownedArrows(player);
    require(
        manualShot.size() == 1
            && !manualShot.getFirst().isCritArrow()
            && player.getOffhandItem().getCount() == 1,
        "The same release packet still fires an ordinary, partially drawn bow");
    manualShot.forEach(AbstractArrow::discard);

    Consumer<ArrowNockEvent> refuseNock =
        event -> {
          if (event.getEntity() == player) event.setCanceled(true);
        };
    NeoForge.EVENT_BUS.addListener(ArrowNockEvent.class, refuseNock);
    try {
      session.receivePlayPayload(new AbilityNetwork.Cast(), "6");
      require(!player.isUsingItem(), "Cast honors a native ArrowNockEvent refusal");
    } finally {
      NeoForge.EVENT_BUS.unregister(refuseNock);
    }

    Consumer<ArrowLooseEvent> refuseLoose =
        event -> {
          if (event.getEntity() == player) event.setCanceled(true);
        };
    NeoForge.EVENT_BUS.addListener(ArrowLooseEvent.class, refuseLoose);
    try {
      castBow(session, technique);
      bowTicks(player, BowItem.MAX_DRAW_DURATION);
      require(
          !player.isUsingItem()
              && cooldown(session, technique) == 0
              && player.getOffhandItem().getCount() == 1
              && bow.getDamageValue() == 1
              && ownedArrows(player).isEmpty(),
          "A native ArrowLooseEvent refusal clears the draw without ammo, wear or cooldown");
    } finally {
      NeoForge.EVENT_BUS.unregister(refuseLoose);
    }
  }

  private static void verifySlowingShot(TestShipPlayerSession session) {
    var player = session.player;
    var level = player.serverLevel();
    var bow = new ItemStack(Items.BOW);
    player.setItemInHand(InteractionHand.MAIN_HAND, bow);
    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2));
    player.setYRot(0);
    player.setXRot(0);
    player.setDeltaMovement(Vec3.ZERO);
    var technique = MartialTechnique.SLOWING_SHOT;
    prepare(player, 0, technique);
    PreparedAbilities.select(player, 0);
    castBow(session, technique);

    var target = new Zombie(EntityType.ZOMBIE, level);
    target.setPos(player.position().add(0, 0, 3.5));
    require(level.addFreshEntity(target), "Spawn a Slowing Shot target");
    try {
      bowTicks(player, BowItem.MAX_DRAW_DURATION - 1);
      require(
          player.isUsingItem() && player.getOffhandItem().getCount() == 2,
          "Slowing Shot waits through the ordinary draw with ammo intact");
      bowTicks(player, 1);
      require(
          !player.isUsingItem()
              && player.getOffhandItem().getCount() == 1
              && cooldown(session, technique) == technique.cooldownTicks(),
          "Slowing Shot ends the draw and charges one arrow and cooldown at release");
      var arrows =
          level.getEntitiesOfClass(
              AbstractArrow.class,
              player.getBoundingBox().inflate(4),
              arrow -> arrow.getOwner() == player);
      try {
        require(
            arrows.size() == 1 && arrows.getFirst().isCritArrow(),
            "Slowing Shot releases one native full-draw arrow");
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

  private static void verifyMarkingShot(TestShipPlayerSession session) {
    var player = session.player;
    var technique = MartialTechnique.MARKING_SHOT;
    var bow = new ItemStack(Items.BOW);
    player.setItemInHand(InteractionHand.MAIN_HAND, bow);
    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2));
    prepare(player, 0, technique);
    PreparedAbilities.select(player, 0);
    castBow(session, technique);
    var target = opponent(player, 0, 3.5);
    target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
    target.setHealth(100);
    try {
      bowTicks(player, BowItem.MAX_DRAW_DURATION);
      var markedArrow = ownedArrows(player).getFirst();
      for (int tick = 0; tick < 4 && !markedArrow.isRemoved(); tick++) markedArrow.tick();
      markedArrow.discard();
      require(
          techniqueEntry(session, technique).reason().equals(MartialTechniques.MARKED),
          "A Cast-launched Marking Shot marks its visible target");
      target.invulnerableTime = 0;
      var followUp = fullDraw(player, bow).getFirst();
      close(
          MartialArchery.impactDamage(followUp, target, 10),
          17.0,
          "An ordinary follow-up keeps the Marking Shot's damage bonus");
      for (int tick = 0; tick < 4 && !followUp.isRemoved(); tick++) followUp.tick();
      require(
          !techniqueEntry(session, technique).reason().equals(MartialTechniques.MARKED),
          "The native follow-up consumes the mark");
    } finally {
      target.discard();
      ownedArrows(player).forEach(AbstractArrow::discard);
    }
  }

  private static void verifyQuickShot(TestShipPlayerSession session) {
    var player = session.player;
    var technique = MartialTechnique.QUICK_SHOT;
    var bow = new ItemStack(Items.BOW);
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    player.setItemInHand(InteractionHand.OFF_HAND, bow);
    player.getInventory().setItem(10, new ItemStack(Items.ARROW, 2));
    prepare(player, 0, technique);
    PreparedAbilities.select(player, 0);
    castBow(session, technique);
    require(
        player.getUseItem() == bow && player.getUsedItemHand() == InteractionHand.OFF_HAND,
        "Quick Shot starts the real offhand draw when the main hand has no bow");
    bowTicks(player, 8);
    require(
        player.isUsingItem() && ownedArrows(player).isEmpty(),
        "Quick Shot still holds the bow at eight ticks");
    bowTicks(player, 1);
    var arrows = ownedArrows(player);
    require(
        arrows.size() == 1
            && arrows.getFirst().isCritArrow()
            && player.getInventory().getItem(10).getCount() == 1
            && cooldown(session, technique) == technique.cooldownTicks(),
        "Quick Shot fires at full power after nine ticks and spends one arrow");
    arrows.forEach(AbstractArrow::discard);
  }

  private static void verifyTripleShot(TestShipPlayerSession session) {
    ServerPlayer player = session.player;
    var level = player.serverLevel();
    var clock = level.getServer().getWorldData().overworldData();
    var bow = new ItemStack(Items.BOW);
    player.setItemInHand(InteractionHand.MAIN_HAND, bow);
    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2));
    player.getInventory().setItem(10, new ItemStack(Items.ARROW, 2));
    session.look(90, 0);
    player.setDeltaMovement(Vec3.ZERO);
    var technique = MartialTechnique.TRIPLE_SHOT;
    prepare(player, 0, technique);
    PreparedAbilities.select(player, 0);
    castBow(session, technique);

    var target = new Zombie(EntityType.ZOMBIE, level);
    target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
    target.setHealth(100);
    target.setPos(player.position().add(0, 0, 5.5));
    require(level.addFreshEntity(target), "Spawn a Triple Shot target");
    var bounds = player.getBoundingBox().inflate(8);
    int previousCues = session.volleyLaunchPackets();
    try {
      bowTicks(player, 12);
      session.releaseUsingItem();
      require(
          player.isUsingItem()
              && player.getOffhandItem().getCount() == 2
              && player.getInventory().getItem(10).getCount() == 2
              && ownedArrows(player).isEmpty()
              && session.volleyLaunchPackets() == previousCues,
          "Early native release cannot fire or pay for the volley");

      session.look(0, 0);
      require(player.getYRot() == 0.0F, "The server received the new aim before release");
      bowTicks(player, BowItem.MAX_DRAW_DURATION - 12);
      long release = clock.getGameTime();
      require(
          player.getOffhandItem().isEmpty()
              && player.getInventory().getItem(10).getCount() == 1
              && cooldown(session, technique) == technique.cooldownTicks(),
          "A full draw pays for three arrows across the off hand and inventory");
      session.look(90, 0);
      int previousTrails = session.volleyTrailPackets();
      for (int elapsed = 0; elapsed <= 10; elapsed++) {
        clock.setGameTime(release + elapsed);
        MartialTechniques.tick(player);
        var arrows =
            level.getEntitiesOfClass(
                AbstractArrow.class, bounds, arrow -> arrow.getOwner() == player);
        if (elapsed % 5 != 0) {
          require(
              arrows.isEmpty() && session.volleyLaunchPackets() == previousCues,
              "No arrow or launch cue is sent between releases");
          continue;
        }
        require(
            arrows.size() == 1 && session.volleyLaunchPackets() == ++previousCues,
            "Every release spawns one arrow and delivers one visible cue to the archer");
        var arrow = arrows.getFirst();
        require(
            arrow.getDeltaMovement().z > 2.5 && Math.abs(arrow.getDeltaMovement().x) < 0.15,
            "Every arrow uses the release-time aim even after the player turns");
        float health = target.getHealth();
        arrow.tick();
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(arrow));
        if (elapsed == 0) {
          require(
              arrow.isCritArrow() && session.volleyTrailPackets() == previousTrails,
              "The native full draw already has its critical trail");
        } else {
          require(
              session.volleyTrailPackets() == ++previousTrails,
              "Each follow-up supplies its own short trail");
        }
        for (int tick = 1; tick < 4 && !arrow.isRemoved(); tick++) arrow.tick();
        require(
            target.getHealth() < health, "Each arrow damages the same target in quick succession");
        arrow.discard();
      }
    } finally {
      target.discard();
      level
          .getEntitiesOfClass(AbstractArrow.class, bounds, arrow -> arrow.getOwner() == player)
          .forEach(AbstractArrow::discard);
    }
  }

  private static void verifyTripleShotAmmo(TestShipPlayerSession session) {
    var player = session.player;
    var level = player.serverLevel();
    var clock = level.getServer().getWorldData().overworldData();
    long start = clock.getGameTime();
    var technique = MartialTechnique.TRIPLE_SHOT;
    var bow = Items.BOW.getDefaultInstance();
    bow.enchant(
        level
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.INFINITY),
        1);
    player.setItemInHand(InteractionHand.MAIN_HAND, bow);
    player.setYRot(0);
    player.setXRot(0);
    player.setDeltaMovement(Vec3.ZERO);
    int launches = session.volleyLaunchPackets();
    try {
      clock.setGameTime(start + technique.cooldownTicks() + 1);
      player.getInventory().setItem(10, ItemStack.EMPTY);
      player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2));
      session.receivePlayPayload(new AbilityNetwork.Cast(), "6");
      require(
          !player.isUsingItem() && cooldown(session, technique) == 0,
          "Infinity cannot replace a missing third arrow");
      var arrows = fullDraw(player, bow);
      require(
          arrows.size() == 1 && player.getOffhandItem().getCount() == 2,
          "Without three physical arrows an Infinity bow performs one native shot");
      arrows.forEach(AbstractArrow::discard);

      player.getInventory().setItem(10, new ItemStack(Items.ARROW, 3));
      for (var special : List.of(Items.TIPPED_ARROW.getDefaultInstance(), smokeAmmo())) {
        player.setItemInHand(InteractionHand.OFF_HAND, special);
        session.receivePlayPayload(new AbilityNetwork.Cast(), "6");
        require(
            !player.isUsingItem() && cooldown(session, technique) == 0,
            "Selecting " + special + " blocks Triple Shot even with three ordinary arrows");
      }
      for (var special : List.of(Items.TIPPED_ARROW.getDefaultInstance(), smokeAmmo())) {
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        castBow(session, technique);
        bowTicks(player, 6);
        player.setItemInHand(InteractionHand.OFF_HAND, special);
        bowTicks(player, BowItem.MAX_DRAW_DURATION);
        require(
            !player.isUsingItem()
                && ownedArrows(player).isEmpty()
                && cooldown(session, technique) == 0
                && player.getInventory().getItem(10).getCount() == 3,
            "Switching to " + special + " cancels Triple Shot and keeps the ordinary arrows");
        arrows = fullDraw(player, bow);
        require(
            arrows.size() == 1
                && player.getInventory().getItem(10).getCount() == 3
                && session.volleyLaunchPackets() == launches,
            "Manual special ammunition still releases one native arrow");
        arrows.forEach(AbstractArrow::discard);
      }
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      castBow(session, technique);
      bowTicks(player, BowItem.MAX_DRAW_DURATION);
      long release = clock.getGameTime();
      arrows = ownedArrows(player);
      require(
          arrows.size() == 1
              && session.volleyLaunchPackets() == launches + 1
              && player.getInventory().getItem(10).isEmpty(),
          "Automatic Triple Shot spends three physical arrows from an Infinity bow");
      arrows.forEach(AbstractArrow::discard);
      for (int tick = 1; tick <= 10; tick++) {
        clock.setGameTime(release + tick);
        MartialTechniques.tick(player);
      }
      require(
          session.volleyLaunchPackets() == launches + 3,
          "A paid Infinity volley still has exactly three release packets");
    } finally {
      ownedArrows(player).forEach(AbstractArrow::discard);
      MartialTechniques.reset(player);
      clock.setGameTime(start);
    }
  }

  private static ItemStack smokeAmmo() {
    var arrow = Items.ARROW.getDefaultInstance();
    CustomData.update(
        DataComponents.CUSTOM_DATA,
        arrow,
        data -> data.putBoolean("aeroscapes_reimagine:smoke_arrow", true));
    return arrow;
  }

  private static List<AbstractArrow> fullDraw(ServerPlayer player, ItemStack bow) {
    player.startUsingItem(InteractionHand.MAIN_HAND);
    Items.BOW.releaseUsing(
        bow, player.serverLevel(), player, bow.getUseDuration(player) - BowItem.MAX_DRAW_DURATION);
    player.stopUsingItem();
    return ownedArrows(player);
  }

  private static List<AbstractArrow> ownedArrows(ServerPlayer player) {
    return player
        .serverLevel()
        .getEntitiesOfClass(
            AbstractArrow.class,
            player.getBoundingBox().inflate(12),
            arrow -> arrow.getOwner() == player);
  }

  private static void verifySmokeArrow(ServerLevel level, Vec3 position) {
    try (var archer =
            TestShipPlayerSession.create(level, UUID.randomUUID(), "SmokeArcher", position, null);
        var retreat =
            TestShipPlayerSession.create(
                level, UUID.randomUUID(), "SmokeRetreat", position.add(1.5, 0, 0), null)) {
      verifySmokePursuit(archer, retreat);
    }
  }

  private static void verifySmokePursuit(
      TestShipPlayerSession archer, TestShipPlayerSession retreat) {
    var player = archer.player;
    var covered = retreat.player;
    var level = player.serverLevel();
    var clock = level.getServer().getWorldData().overworldData();
    long start = clock.getGameTime();
    Vec3 position = player.position();
    Vec3 behindSmoke = covered.position();
    var origin = player.blockPosition();
    Map<BlockPos, BlockState> restored = new HashMap<>();
    var skeleton = new Skeleton(EntityType.SKELETON, level);
    var zombie = new Zombie(EntityType.ZOMBIE, level);
    var bounds = player.getBoundingBox().inflate(14);
    try {
      clock.setGameTime(start + 20);
      for (int x = -1; x <= 9; x++) {
        for (int z = -1; z <= 9; z++) {
          setStone(level, origin.offset(x, -1, z), restored);
          for (int y = 0; y <= 2; y++) {
            BlockPos block = origin.offset(x, y, z);
            BlockState previous = level.getBlockState(block);
            if (!previous.isAir()) {
              restored.putIfAbsent(block, previous);
              level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            }
          }
        }
      }
      // The block catches the smoke arrow beside, rather than on, the mobs' sight lines.
      setStone(level, BlockPos.containing(player.getEyePosition().add(0, 0, 4)), restored);
      skeleton.setPos(position.add(1.25, 0, 8));
      skeleton.setItemInHand(InteractionHand.MAIN_HAND, Items.BOW.getDefaultInstance());
      skeleton.setTarget(covered);
      zombie.setPos(position.add(2, 0, 7));
      zombie.setTarget(covered);
      require(
          level.addFreshEntity(skeleton) && level.addFreshEntity(zombie), "Spawn pursuing mobs");
      // This fixture calls the goals directly before an entity tick sets ground contact.
      skeleton.setOnGround(true);
      zombie.setOnGround(true);
      var ranged = new RangedBowAttackGoal<>(skeleton, 1.0, 20, 15.0F);
      var melee = new MeleeAttackGoal(zombie, 1.0, true);
      require(
          skeleton.getSensing().hasLineOfSight(covered)
              && zombie.getSensing().hasLineOfSight(covered),
          "The retreating player is in clear sight before smoke forms");
      require(ranged.canUse(), "The armed skeleton starts its native ranged goal");
      require(melee.canUse(), "The grounded zombie can path to the retreating player");
      ranged.start();
      melee.start();
      ranged.tick();
      melee.tick();
      var oldPath = zombie.getNavigation().getPath();
      require(oldPath != null && !oldPath.isDone(), "The melee mob is already pursuing the player");
      skeleton.performRangedAttack(covered, 1);
      var inbound =
          level.getEntitiesOfClass(
              AbstractArrow.class, bounds, arrow -> arrow.getOwner() == skeleton);
      require(inbound.size() == 1, "One hostile arrow is already in flight");
      var hostileArrow = inbound.getFirst();
      skeleton.startUsingItem(InteractionHand.MAIN_HAND);
      double rangedAim = skeleton.getLookControl().getWantedX();
      double meleeAim = zombie.getLookControl().getWantedX();

      player.setItemInHand(InteractionHand.OFF_HAND, smokeAmmo());
      var bow = Items.BOW.getDefaultInstance();
      player.setItemInHand(InteractionHand.MAIN_HAND, bow);
      var outbound = fullDraw(player, bow);
      require(
          outbound.size() == 1 && player.getOffhandItem().isEmpty(),
          "Smoke spends one native arrow");
      var smoke = outbound.getFirst();
      for (int tick = 0; tick < 4 && !smoke.isRemoved(); tick++) smoke.tick();
      var archerSmoke = archer.takeSmokeParticles();
      var coveredSmoke = retreat.takeSmokeParticles();
      require(
          archerSmoke != null && coveredSmoke != null,
          "Native block impact sends smoke to both the shooting and retreating players");
      require(
          archerSmoke.getCount() == coveredSmoke.getCount()
              && archerSmoke.getX() == coveredSmoke.getX()
              && archerSmoke.getY() == coveredSmoke.getY()
              && archerSmoke.getZ() == coveredSmoke.getZ(),
          "Both server sessions receive the same smoke location and intensity");

      covered.setPos(behindSmoke.add(0.5, 0, 0));
      require(
          skeleton.hasLineOfSight(covered) && zombie.hasLineOfSight(covered),
          "Solid blocks do not interrupt the covered player's route");
      require(
          !skeleton.getSensing().hasLineOfSight(covered)
              && !zombie.getSensing().hasLineOfSight(covered),
          "Smoke interrupts even cached visibility");
      ranged.tick();
      melee.tick();
      require(!skeleton.isUsingItem(), "An already aiming skeleton lowers its bow");
      close(skeleton.getLookControl().getWantedX(), rangedAim, "Skeleton keeps its last known aim");
      close(zombie.getLookControl().getWantedX(), meleeAim, "Zombie keeps its last known aim");
      require(zombie.getNavigation().getPath() == oldPath, "Melee keeps its last known path");
      double smokePlane = archerSmoke.getZ();
      for (int tick = 0;
          tick < 7 && hostileArrow.getZ() > smokePlane && !hostileArrow.isRemoved();
          tick++) hostileArrow.tick();
      require(
          hostileArrow.getZ() < smokePlane && !hostileArrow.isRemoved(),
          "A hostile arrow already in flight crosses the smoke");

      covered.setPos(behindSmoke.add(6, 0, 0));
      require(
          skeleton.getSensing().hasLineOfSight(covered)
              && zombie.getSensing().hasLineOfSight(covered),
          "A sight line outside the cloud stays open while smoke is active");
      covered.setPos(behindSmoke.add(0.5, 0, 0));
      clock.setGameTime(start + 121);
      require(
          skeleton.getSensing().hasLineOfSight(covered)
              && zombie.getSensing().hasLineOfSight(covered),
          "Sight returns when the smoke expires");
      ranged.tick();
      melee.tick();
      close(skeleton.getLookControl().getWantedX(), covered.getX(), "Skeleton resumes tracking");
      close(zombie.getLookControl().getWantedX(), covered.getX(), "Zombie resumes tracking");
    } finally {
      clock.setGameTime(start + 121);
      NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, level));
      clock.setGameTime(start);
      level
          .getEntitiesOfClass(
              AbstractArrow.class,
              bounds,
              arrow -> arrow.getOwner() == player || arrow.getOwner() == skeleton)
          .forEach(AbstractArrow::discard);
      skeleton.discard();
      zombie.discard();
      restored.forEach((pos, previous) -> level.setBlockAndUpdate(pos, previous));
    }
  }

  private static void setStone(
      ServerLevel level, BlockPos pos, Map<BlockPos, BlockState> restored) {
    restored.putIfAbsent(pos, level.getBlockState(pos));
    level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
  }

  private static void verifyDuelistSidestep(ServerLevel level, Vec3 position) {
    var clock = level.getServer().getWorldData().overworldData();
    long initial = clock.getGameTime();
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "Duelist", position, null)) {
      var player = session.player;
      var origin = player.blockPosition();
      Map<BlockPos, BlockState> restored = new HashMap<>();
      var attacker = new Zombie(EntityType.ZOMBIE, level);
      ProgressionRegression.listPlayer(level, player, true);
      try {
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        var ranger = CharacterClass.RANGER;
        require(
            RaceIdentity.choose(
                player,
                Race.HUMAN,
                0,
                ranger,
                ranger.starters().getFirst(),
                Profession.BLACKSMITH,
                "material_repair"),
            "Choose the ordinary Ranger for the Duelist regression");
        for (int offset = -2; offset <= 2; offset++) {
          setStone(level, origin.offset(offset, -1, 0), restored);
        }
        for (int tick = 0; tick < 60; tick++) player.tick();
        attacker.setNoAi(true);
        attacker.setPos(position.add(0, 0, 1.8));
        require(level.addFreshEntity(attacker), "Spawn a real frontal Duelist opponent");
        var knife = item("farmersdelight:iron_knife");
        player.setItemInHand(InteractionHand.MAIN_HAND, knife);
        var parry = MartialTechnique.DUELIST_PARRY;
        prepare(player, 0, parry);
        PreparedAbilities.select(player, 0);
        setCombatTalent(player, "duelist_sidestep");

        float ordinary = duelistDamage(player, attacker);
        require(ordinary > 0, "A native frontal melee hit deals damage before parry");
        byte[] directions = {0, 1, -1, 1};
        for (int index = 0; index < directions.length; index++) {
          byte direction = directions[index];
          player.setPos(position);
          player.setYRot(0);
          player.setOnGround(true);
          player.setDeltaMovement(Vec3.ZERO);
          boolean obstructed = index == directions.length - 1;
          if (obstructed) {
            setStone(level, origin.east(), restored);
            setStone(level, origin.east().above(), restored);
          }
          if (direction == -1) {
            player.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_SWORD.getDefaultInstance());
            player.setItemInHand(InteractionHand.OFF_HAND, knife);
            player.doTick();
          } else {
            player.setItemInHand(InteractionHand.MAIN_HAND, knife);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.doTick();
          }
          session.moveOnGround(position);
          require(GroundFootwork.available(player), "The parry fixture starts on solid ground");
          require(GroundFootwork.learnedSidestep(player), "The Ranger sidestep talent is learned");
          verifyParryStep(session, attacker, ordinary, direction, obstructed);
          clock.setGameTime(clock.getGameTime() + parry.cooldownTicks() + 1);
        }
      } finally {
        MartialTechniques.reset(player);
        ProgressionRegression.listPlayer(level, player, false);
        attacker.discard();
        restored.forEach((pos, previous) -> level.setBlockAndUpdate(pos, previous));
      }
    } finally {
      clock.setGameTime(initial);
    }
  }

  private static void verifyParryStep(
      TestShipPlayerSession session,
      Zombie attacker,
      float ordinary,
      byte direction,
      boolean obstructed) {
    var player = session.player;
    var clock = player.serverLevel().getServer().getWorldData().overworldData();
    long now = clock.getGameTime();
    Vec3 start = player.position();
    var parry = MartialTechnique.DUELIST_PARRY;
    session.receivePlayPayload(new GroundFootwork.LateralInput(direction), "1");
    PreparedAbilities.cast(player);
    require(
        cooldown(session, parry) == parry.cooldownTicks(), "Parry keeps its activation cooldown");
    float attackerHealth = attacker.getHealth();
    close(
        duelistDamage(player, attacker), ordinary * 0.55F, "The frontal parry softens the attack");
    var reply = session.takePayload(SwingTempo.Window.class);
    require(
        reply.style() == SwingTempo.Style.PARRY && reply.target() == attacker.getId(),
        "Parry and sidestep leave the faster manual reply targeted at the attacker");
    MartialTechniques.tick(player);
    require(
        !session.hasFootwork() && player.position().equals(start),
        "The hit cannot teleport or immediately step the player");
    clock.setGameTime(now + 1);
    session.moveOnGround(start);
    MartialTechniques.tick(player);
    if (direction == 0 || obstructed) {
      require(
          !session.hasFootwork() && player.position().equals(start),
          "Neutral or blocked input keeps the parry in place");
    } else {
      followFootwork(session, start);
      close(
          player.getX() - start.x, direction * 1.5, "Sidestep follows the held lateral direction");
      close(player.getZ(), start.z, "Sidestep does not move in the facing direction");
      close(player.getYRot(), 0, "Sidestep preserves facing");
    }
    require(attacker.getHealth() == attackerHealth, "The reply never attacks automatically");
    PreparedAbilities.cast(player);
    require(
        cooldown(session, parry) == parry.cooldownTicks() - 1,
        "Sidestep never adds or refreshes the parry cooldown");
  }

  private static float duelistDamage(ServerPlayer player, Zombie attacker) {
    float health = player.getMaxHealth();
    player.setHealth(health);
    player.invulnerableTime = 0;
    require(player.hurt(player.damageSources().mobAttack(attacker), 4), "The melee hit lands");
    return health - player.getHealth();
  }

  private static void prepare(ServerPlayer player, int slot, MartialTechnique technique) {
    String node = ResourceLocation.parse(technique.id()).getPath();
    SkillsMod.getInstance()
        .tryUnlockSkill(player, CombatDirection.categoryForNode(node), node, true);
    PreparedAbilities.edit(player, slot, technique.id());
    require(
        PreparedAbilities.preparation(player).slots().get(slot).equals(technique.id()),
        "Prepare " + technique.id());
  }

  private static void verifyAbilities(ServerPlayer player) {
    var provider = SkillsMod.getInstance();
    provider.setPoints(
        player, COMBAT, ResourceLocation.parse("aeroscapes_reimagine:martial_fixture"), 6, true);
    provider.setPoints(
        player, RANGER, ResourceLocation.parse("aeroscapes_reimagine:martial_fixture"), 6, true);
    require(
        provider.getSkillState(player, RANGER, "bow_stride").orElseThrow() == Skill.State.UNLOCKED,
        "Chosen passive");
    for (String skill :
        List.of("quick_block", "shield_march", "steady_aim", "quick_recovery", "opening_strike")) {
      provider.tryUnlockSkill(player, CombatDirection.categoryForNode(skill), skill, false);
      require(
          provider
                  .getSkillState(player, CombatDirection.categoryForNode(skill), skill)
                  .orElseThrow()
              == Skill.State.UNLOCKED,
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
    APIUtils.setLevel("ranger", player, 20);
    APIUtils.setLevel("archery", player, 0);
    ProgressionBridge.sync(player);
    require(
        !learnHandling(player, "crossbow_handling"),
        "Ranger points do not replace ranged practice");
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
    provider.tryUnlockSkill(player, CombatDirection.categoryForNode(node), node, false);
    return provider.getSkillState(player, CombatDirection.categoryForNode(node), node).orElseThrow()
        == Skill.State.UNLOCKED;
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
