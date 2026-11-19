package dev.aeroscapes.magic;

import dev.aeroscapes.magic.travel.TravelMagicPolicy;
import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import io.redspace.ironsspellbooks.entity.spells.wall_of_fire.WallOfFireEntity;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

/** Functional-direction effects that are not already applied by native attribute rewards. */
public final class MagicBonuses {
  private static volatile Set<String> clientSkills = Set.of();

  private MagicBonuses() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOW, MagicBonuses::discountMana);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MagicBonuses::discountCooldown);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MagicBonuses::conditionalDamage);
  }

  public static void setClientSkills(Set<String> skills) {
    clientSkills = Set.copyOf(skills);
  }

  public static void clearClientSkills() {
    clientSkills = Set.of();
  }

  public static float power(AbstractSpell spell, Entity caster, float raw) {
    return raw * (float) (1 + bonus(spell, caster, "power"));
  }

  public static int manaCost(
      AbstractSpell spell, int level, Player caster, CastSource source, int raw) {
    if (level < 1 || !source.consumesMana()) return raw;
    return reduced(raw, bonus(spell, caster, "mana"));
  }

  public static int cooldown(AbstractSpell spell, Player caster, int raw) {
    int result = reduced(raw, bonus(spell, caster, "cooldown"));
    return switch (spell.getSpellId()) {
      case "irons_spellbooks:angel_wing" ->
          Math.max(result, TravelMagicPolicy.WINGS_COOLDOWN_TICKS);
      case "irons_spellbooks:portal" -> Math.max(result, TravelMagicPolicy.PORTAL_COOLDOWN_TICKS);
      default -> result;
    };
  }

  public static int castTime(AbstractSpell spell, LivingEntity caster, int raw) {
    if (raw <= 0) return raw;
    double amount = Math.clamp(bonus(spell, caster, "cast_speed"), 0, 0.5);
    // Native cast-speed bonuses extend channels rather than shortening their useful duration.
    double multiplier = spell.getCastType() == CastType.CONTINUOUS ? 1 + amount : 1 - amount;
    return Math.max(1, Math.round(raw * (float) multiplier));
  }

  public static int fireballRadius(AbstractSpell spell, LivingEntity caster, int raw) {
    return raw + (int) Math.round(bonus(spell, caster, "fireball_area"));
  }

  public static float blizzardRadius(AbstractSpell spell, LivingEntity caster, float raw) {
    return raw * (float) (1 + bonus(spell, caster, "blizzard_area"));
  }

  private static int reduced(int raw, double amount) {
    if (raw <= 0) return raw;
    return Math.max(1, (int) (raw * (1 - Math.clamp(amount, 0, 0.5))));
  }

  private static Set<String> skills(Entity caster) {
    if (caster instanceof ServerPlayer player) return SpellKnowledge.unlocked(player);
    if (caster instanceof Player player && player.level().isClientSide() && player.isLocalPlayer())
      return clientSkills;
    return Set.of();
  }

  private static double bonus(AbstractSpell spell, Entity caster, String effect) {
    MagicTalents.Spell route = MagicTalents.spell(spell.getSpellId());
    return route == null ? 0 : MagicTalents.bonus(skills(caster), route.direction(), effect);
  }

  private static void discountMana(SpellOnCastEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      event.setManaCost(
          manaCost(
              SpellKnowledge.spell(event.getSpellId()),
              event.getSpellLevel(),
              player,
              event.getCastSource(),
              event.getManaCost()));
    }
  }

  private static void discountCooldown(SpellCooldownAddedEvent.Pre event) {
    if (event.getEntity() instanceof ServerPlayer player
        && event.getCastSource() != CastSource.SCROLL) {
      event.setEffectiveCooldown(cooldown(event.getSpell(), player, event.getEffectiveCooldown()));
    }
  }

  private static void conditionalDamage(SpellDamageEvent event) {
    var source = event.getSpellDamageSource();
    if (!(source.getEntity() instanceof ServerPlayer player)
        || source.getDirectEntity() instanceof AoeEntity
        || source.getDirectEntity() instanceof WallOfFireEntity) return;
    AbstractSpell spell = source.spell();
    LivingEntity target = event.getEntity();
    double amount = 0;
    if (target.hasEffect(MobEffectRegistry.CHILLED)) amount += bonus(spell, player, "frozen_bonus");
    if (target.hasEffect(MobEffects.POISON)) amount += bonus(spell, player, "poison_bonus");
    if (amount > 0) event.setAmount(event.getAmount() * (float) (1 + amount));
  }
}
