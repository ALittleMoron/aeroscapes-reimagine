package dev.aeroscapes.magic;

import dev.aeroscapes.progression.SpellPractice;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Credits the defender whose Fortify hearts absorbed a hostile hit on a wounded ally. */
public final class GuardianAid {
  private static final String TALENT = "magic_restoration_guardian_aid";
  private static final Map<LivingEntity, Ward> WARDS = new WeakHashMap<>();
  private static final Map<ServerPlayer, Aid> READY = new WeakHashMap<>();

  private record Ward(UUID defender, MobEffectInstance effect, float lower, float upper) {}

  private record Aid(UUID ally, long expires) {}

  private GuardianAid() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(GuardianAid::absorbed);
  }

  public static void cast(
      AbstractSpell spell, ServerPlayer player, MagicData magic, float paid, Runnable nativeCast) {
    double rate =
        MagicTalents.bonus(SpellKnowledge.unlocked(player), "restoration", "guardian_refund");
    if (spell == SpellRegistry.FORTIFY_SPELL.get()) {
      if (rate > 0 && paid > 0) fortify(player, nativeCast);
      else nativeCast.run();
    } else {
      float refund =
          bless(
              player,
              magic,
              Math.min((int) (paid * rate), Math.max(0, (int) paid - 1)),
              nativeCast);
      MagicPractice.award(player, SpellPractice.experience((int) (paid - refund)));
    }
  }

  private static void fortify(ServerPlayer player, Runnable nativeCast) {
    Map<LivingEntity, Float> before = new HashMap<>();
    for (LivingEntity ally :
        player
            .serverLevel()
            .getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8))) {
      if (ally != player
          && ally.isAlive()
          && ally.getHealth() < ally.getMaxHealth()
          && isAlly(player, ally)) before.put(ally, ally.getAbsorptionAmount());
    }
    nativeCast.run();
    before.forEach(
        (ally, previous) -> {
          var effect = ally.getEffect(MobEffectRegistry.FORTIFY);
          float current = ally.getAbsorptionAmount();
          if (effect != null && current > previous) {
            WARDS.put(ally, new Ward(player.getUUID(), effect, previous, current));
          }
        });
  }

  private static void absorbed(LivingDamageEvent.Post event) {
    LivingEntity ally = event.getEntity();
    Ward ward = WARDS.get(ally);
    if (ward == null || !(ally.level() instanceof ServerLevel level)) return;
    if (ally.getEffect(MobEffectRegistry.FORTIFY) != ward.effect()) {
      WARDS.remove(ally);
      return;
    }
    float absorbed = event.getReduction(DamageContainer.Reduction.ABSORPTION);
    float remaining = ally.getAbsorptionAmount();
    // Only the absorption range contributed by this cast belongs to its defender.
    if (absorbed <= 0 || remaining >= ward.upper() || remaining + absorbed <= ward.lower()) return;
    if (remaining <= ward.lower()) WARDS.remove(ally);
    else WARDS.put(ally, new Ward(ward.defender(), ward.effect(), ward.lower(), remaining));
    ServerPlayer player = level.getServer().getPlayerList().getPlayer(ward.defender());
    if (player == null
        || player.level() != level
        || !player.isAlive()
        || !ally.isAlive()
        || ally.getHealth() + event.getNewDamage() >= ally.getMaxHealth()
        || !(event.getSource().getEntity() instanceof LivingEntity attacker)
        || !(attacker instanceof Enemy)
        || isAlly(player, attacker)
        || !isAlly(player, ally)
        || !SpellKnowledge.unlocked(player).contains(TALENT)) return;
    WARDS.remove(ally);
    READY.put(player, new Aid(ally.getUUID(), level.getGameTime() + 200));
    player.displayClientMessage(
        Component.translatable(
            "aeroscapes_reimagine.magic_tree.guardian_ready", ally.getDisplayName()),
        true);
  }

  private static float bless(
      ServerPlayer player, MagicData magic, int refund, Runnable nativeCast) {
    Aid aid = READY.get(player);
    if (aid != null && player.level().getGameTime() >= aid.expires()) {
      READY.remove(player);
      aid = null;
    }
    if (refund <= 0 || aid == null) {
      nativeCast.run();
      return 0;
    }
    LivingEntity ally =
        magic.getAdditionalCastData() instanceof TargetEntityCastData data
            ? data.getTarget(player.serverLevel())
            : null;
    if (ally == null
        || ally == player
        || !aid.ally().equals(ally.getUUID())
        || !isAlly(player, ally)) {
      nativeCast.run();
      return 0;
    }
    float health = ally.getHealth();
    nativeCast.run();
    if (ally.getHealth() <= health || !READY.remove(player, aid)) return 0;
    float previous = magic.getMana();
    magic.addMana(refund);
    float restored = Math.clamp(magic.getMana() - previous, 0, refund);
    if (restored > 0) {
      PacketDistributor.sendToPlayer(player, new SyncManaPacket(magic));
      player.displayClientMessage(
          Component.translatable("aeroscapes_reimagine.magic_tree.guardian_refund", restored),
          true);
    }
    return restored;
  }

  private static boolean isAlly(ServerPlayer player, Entity target) {
    return Utils.shouldHealEntity(player, target);
  }
}
