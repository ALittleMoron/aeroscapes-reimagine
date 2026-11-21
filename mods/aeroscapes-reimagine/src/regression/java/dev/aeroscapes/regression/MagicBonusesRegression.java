package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.progression.ProgressionBridge;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.firebolt.FireboltProjectile;
import io.redspace.ironsspellbooks.entity.spells.magma_ball.FireField;
import io.redspace.ironsspellbooks.entity.spells.wall_of_fire.WallOfFireEntity;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.spells.ice.BlizzardSpell;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

/** Exercises transformed native calculations and the native event bus without a client. */
public final class MagicBonusesRegression {
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
  private static final Set<String> AREA_NODES =
      Set.of("magic_destruction_blast_geometry", "magic_destruction_winter_front");

  private MagicBonusesRegression() {}

  public static void verify(ServerLevel level) {
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.randomUUID(),
            "MagicBonusFixture",
            Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(12, 32, 0),
            null)) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        MagicData magic = MagicData.getPlayerMagicData(player);
        magic.setServerPlayer(player);
        magic.getSyncedData();
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        MagicPractice.synchronize(player);
        ProgressionBridge.sync(player);
        verifyCalculations(player);
        verifyMana(player);
        verifyCooldowns(player);
        verifyConditionalDamage(level, player);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    LogUtils.getLogger().info("Magic bonuses native regression passed");
  }

  private static void verifyCalculations(ServerPlayer player) {
    AbstractSpell firebolt = spell("firebolt");
    AbstractSpell heal = spell("heal");
    AbstractSpell fireball = spell("fireball");
    AbstractSpell siphoning = spell("ray_of_siphoning");
    AbstractSpell recall = spell("recall");
    AbstractSpell blizzard = spell("blizzard");
    float firePower = firebolt.getSpellPower(1, player);
    float fireMultiplier = firebolt.getEntityPowerMultiplier(player);
    float healingPower = heal.getSpellPower(1, player);
    float healingMultiplier = heal.getEntityPowerMultiplier(player);
    int ordinaryTime = fireball.getEffectiveCastTime(1, player);
    int continuousTime = siphoning.getEffectiveCastTime(1, player);
    int fixedTime = recall.getEffectiveCastTime(1, player);
    for (var node : MagicTalents.definitions().values()) {
      if (!node.direction().isEmpty()
          && !node.effects().isEmpty()
          && !AREA_NODES.contains(node.id())) {
        SkillsMod.getInstance().unlockSkill(player, MAGIC, node.id());
      }
    }
    close(firebolt.getSpellPower(1, player), firePower * 1.05f, "One Destruction power multiplier");
    close(
        firebolt.getEntityPowerMultiplier(player),
        fireMultiplier * 1.05f,
        "Independent native multiplier gains the same single bonus");
    close(
        heal.getSpellPower(1, player),
        healingPower * 1.13f,
        "Compatible Restoration power talents");
    close(
        heal.getEntityPowerMultiplier(player),
        healingMultiplier * 1.13f,
        "Restoration multiplier is not squared");
    require(
        fireball.getEffectiveCastTime(1, player) == Math.max(1, Math.round(ordinaryTime * 0.96f)),
        "Native ordinary preparation becomes shorter");
    require(
        siphoning.getEffectiveCastTime(1, player)
            == Math.max(1, Math.round(continuousTime * 1.04f)),
        "Native continuous cast lasts longer");
    require(
        recall.getEffectiveCastTime(1, player) == fixedTime, "Native fixed Recall time remains");
    int fireballRadius =
        ((io.redspace.ironsspellbooks.spells.fire.FireballSpell) fireball).getRadius(1, player);
    float blizzardRadius = blizzardRadius(blizzard, player);
    SkillsMod.getInstance().unlockSkill(player, MAGIC, "magic_destruction_blast_geometry");
    require(
        ((io.redspace.ironsspellbooks.spells.fire.FireballSpell) fireball).getRadius(1, player)
            == fireballRadius + 1,
        "Fireball gains one actual radius block after its power calculation");
    close(
        blizzardRadius(blizzard, player),
        blizzardRadius,
        "Fireball's area talent does not enlarge Blizzard");
    SkillsMod.getInstance().unlockSkill(player, MAGIC, "magic_destruction_winter_front");
    close(
        blizzardRadius(blizzard, player),
        blizzardRadius * 1.15f,
        "Blizzard gains its separate radius multiplier after its power calculation");
  }

  private static void verifyMana(ServerPlayer player) {
    AbstractSpell firebolt = spell("firebolt");
    MagicData magic = MagicData.getPlayerMagicData(player);
    int raw = firebolt.getManaCost(1);
    int expected = Math.max(1, (int) (raw * 0.96));
    require(expected < raw, "Fixture has a meaningful integer mana discount");
    magic.setMana(expected);
    require(
        firebolt.canBeCastedBy(1, CastSource.SPELLBOOK, magic, player).isSuccess(),
        "Native affordability accepts discounted mana below the raw price");
    magic.setMana(expected - 1);
    require(
        !firebolt.canBeCastedBy(1, CastSource.SPELLBOOK, magic, player).isSuccess(),
        "Native affordability still rejects insufficient discounted mana");
    var cast =
        new SpellOnCastEvent(
            player, firebolt.getSpellId(), 1, raw, firebolt.getSchoolType(), CastSource.SPELLBOOK);
    NeoForge.EVENT_BUS.post(cast);
    require(
        cast.getManaCost() == expected && cast.getOriginalManaCost() == raw,
        "The real cast event carries the discounted cost while retaining its original price");
    var scroll =
        new SpellOnCastEvent(
            player, firebolt.getSpellId(), 1, raw, firebolt.getSchoolType(), CastSource.SCROLL);
    NeoForge.EVENT_BUS.post(scroll);
    require(scroll.getManaCost() == raw, "Scroll events keep their native mana metadata");
    magic.setMana(100);
  }

  private static void verifyCooldowns(ServerPlayer player) {
    MagicData magic = MagicData.getPlayerMagicData(player);
    MagicManager manager = new MagicManager();
    AbstractSpell firebolt = spell("firebolt");
    int ordinary = MagicManager.getEffectiveSpellCooldown(firebolt, player, CastSource.SPELLBOOK);
    manager.addCooldown(player, firebolt, CastSource.SPELLBOOK);
    require(
        remaining(magic, firebolt) == Math.max(1, (int) (ordinary * 0.96)),
        "Native cooldown storage receives the discounted existing effective value");
    manager.addCooldown(player, spell("angel_wing"), CastSource.SPELLBOOK);
    manager.addCooldown(player, spell("portal"), CastSource.SPELLBOOK);
    require(remaining(magic, spell("angel_wing")) >= 2400, "Wings retain the native travel floor");
    require(remaining(magic, spell("portal")) >= 3600, "Portal retains the native travel floor");
    magic.getPlayerCooldowns().clearCooldowns();
  }

  private static void verifyConditionalDamage(ServerLevel level, ServerPlayer player) {
    FireboltProjectile projectile = new FireboltProjectile(level, player);
    FireField field = new FireField(level);
    field.setOwner(player);
    WallOfFireEntity wall =
        new WallOfFireEntity(
            level, player, List.of(player.position(), player.position().add(3, 0, 0)), 10);
    try {
      conditionalHit(level, player, projectile, false, false, 10);
      conditionalHit(level, player, projectile, true, false, 11);
      conditionalHit(level, player, projectile, false, true, 11);
      conditionalHit(level, player, projectile, true, true, 12);
      conditionalHit(level, player, field, true, true, 10);
      conditionalHit(level, player, wall, true, true, 10);
    } finally {
      projectile.discard();
      field.discard();
      wall.discard();
    }
  }

  private static void conditionalHit(
      ServerLevel level,
      ServerPlayer player,
      Entity direct,
      boolean chilled,
      boolean poisoned,
      float expected) {
    LivingEntity target = EntityType.COW.create(level);
    require(target != null, "Native cow target");
    if (chilled) target.addEffect(new MobEffectInstance(MobEffectRegistry.CHILLED, 100));
    if (poisoned) target.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
    List<Float> amounts = new ArrayList<>();
    Consumer<SpellDamageEvent> observe =
        event -> {
          if (event.getEntity() == target) amounts.add(event.getAmount());
        };
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, observe);
    try {
      AbstractSpell spell = spell(direct instanceof WallOfFireEntity ? "wall_of_fire" : "firebolt");
      require(
          DamageSources.applyDamage(target, 10, spell.getDamageSource(direct, player)),
          "Native spell damage was accepted");
      require(amounts.size() == 1, "Exactly one native spell-damage event");
      close(amounts.getFirst(), expected, "Conditional damage and persistent-area exclusion");
    } finally {
      NeoForge.EVENT_BUS.unregister(observe);
      target.discard();
    }
  }

  private static float blizzardRadius(AbstractSpell spell, ServerPlayer player) {
    try {
      Method radius =
          BlizzardSpell.class.getDeclaredMethod("getRadius", int.class, LivingEntity.class);
      radius.setAccessible(true);
      return ((Number) radius.invoke(spell, 1, player)).floatValue();
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
      throw new IllegalStateException("Cannot exercise native Blizzard radius", failure);
    }
  }

  private static int remaining(MagicData magic, AbstractSpell spell) {
    return magic
        .getPlayerCooldowns()
        .getSpellCooldowns()
        .get(spell.getSpellId())
        .getCooldownRemaining();
  }

  private static AbstractSpell spell(String id) {
    AbstractSpell spell =
        SpellRegistry.getSpell(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", id));
    require(spell != SpellRegistry.none(), "Registered fixture spell: " + id);
    return spell;
  }

  private static void close(float actual, float expected, String message) {
    require(Math.abs(actual - expected) < 0.0001f, message + ": " + actual + " != " + expected);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
