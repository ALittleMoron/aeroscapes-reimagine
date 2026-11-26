package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.races.ClassProgression;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class BerserkerTechniques {
  public static final float LAST_STAND_DAMAGE_BONUS = 0.25F;
  private static final String LAST_STAND = "last_stand";
  private static final float LOW_HEALTH_FRACTION = 0.35F;
  private static final float STANCE_DAMAGE_FACTOR = 1.25F;
  private static final int REPLY_TICKS = 80;
  private static final int RECOVERY_TICKS = 240;
  private static final Map<ServerPlayer, LastStand> LAST_STANDS = new WeakHashMap<>();

  private record LastStand(long replyEnd, long recoveryEnd) {}

  private BerserkerTechniques() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(BerserkerTechniques::expose);
    NeoForge.EVENT_BUS.addListener(BerserkerTechniques::observeDamage);
  }

  private static void expose(LivingDamageEvent.Pre event) {
    if (event.getEntity() instanceof ServerPlayer player
        && MartialTechniques.offensiveStance(player)
        && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
      event.setNewDamage(event.getNewDamage() * STANCE_DAMAGE_FACTOR);
    }
  }

  private static void observeDamage(LivingDamageEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || !player.isAlive()
        || player.isSpectator()
        || event.getNewDamage() <= 0.0F
        || !(event.getSource().getEntity() instanceof LivingEntity attacker)
        || attacker == player
        || player.isAlliedTo(attacker)
        || !ClassProgression.hasCombatTalent(player, LAST_STAND)) return;
    float health = player.getHealth();
    float threshold = player.getMaxHealth() * LOW_HEALTH_FRACTION;
    if (health >= threshold || health + event.getNewDamage() <= threshold) return;
    long now = MartialTechniques.now(player);
    var previous = LAST_STANDS.get(player);
    if (previous != null && previous.recoveryEnd() > now) return;
    LAST_STANDS.put(player, new LastStand(now + REPLY_TICKS, now + RECOVERY_TICKS));
    SpellKnowledge.message(player, "technique_last_stand_ready");
    player.playNotifySound(SoundEvents.PLAYER_ATTACK_STRONG, player.getSoundSource(), 0.8F, 0.75F);
  }

  public static boolean lastStandReady(ServerPlayer player) {
    var lastStand = LAST_STANDS.get(player);
    return lastStand != null
        && lastStand.replyEnd() > MartialTechniques.now(player)
        && ClassProgression.hasCombatTalent(player, LAST_STAND);
  }

  public static void consumeLastStand(ServerPlayer player) {
    var active = LAST_STANDS.get(player);
    if (active != null) LAST_STANDS.put(player, new LastStand(0, active.recoveryEnd()));
  }

  public static boolean stanceAttack(ServerPlayer player) {
    return MartialTechniques.offensiveSwingEnd(player) > MartialTechniques.now(player)
        && MartialTechniques.isHeavyWeapon(player.getWeaponItem());
  }

  public static void stanceLanded(ServerPlayer player) {
    SwingTempo.grantHeavy(player, MartialTechniques.offensiveSwingEnd(player));
  }

  static void reset(ServerPlayer player) {
    LAST_STANDS.remove(player);
  }
}
