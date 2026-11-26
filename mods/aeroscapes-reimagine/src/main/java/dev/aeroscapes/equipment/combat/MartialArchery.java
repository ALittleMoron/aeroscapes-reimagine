package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import org.jetbrains.annotations.Nullable;

/** Associates one armed shot with the native arrow and owns each archer's follow-up mark. */
public final class MartialArchery {
  private static final int SHOT_LIFETIME = 200;
  private static final int MARK_LIFETIME = 140;
  private static final int SLOW_TICKS = 80;
  private static final Map<ServerPlayer, Archer> ARCHERS = new WeakHashMap<>();
  private static final Map<AbstractArrow, Shot> SHOTS = new WeakHashMap<>();

  private record ReadyShot(MartialTechnique technique, long end) {}

  private record Mark(UUID target, long end, long lastLaunched) {}

  private record Shot(Archer archer, long number, long end, @Nullable MartialTechnique technique) {}

  private static final class Archer {
    private long launched;
    private @Nullable ReadyShot ready;
    private @Nullable Mark mark;
  }

  private MartialArchery() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MartialArchery::draw);
  }

  static String equipmentReason(ServerPlayer player) {
    ItemStack bow =
        player.getUseItem().is(Items.BOW) ? player.getUseItem() : player.getMainHandItem();
    if (!bow.is(Items.BOW)) bow = player.getOffhandItem();
    if (!bow.is(Items.BOW)) return "technique_bow";
    return player.getProjectile(bow).is(Items.ARROW) ? "" : "technique_arrow";
  }

  static void prime(ServerPlayer player, MartialTechnique technique, long end) {
    ARCHERS.computeIfAbsent(player, ignored -> new Archer()).ready = new ReadyShot(technique, end);
  }

  static String status(ServerPlayer player, MartialTechnique technique) {
    Archer archer = ARCHERS.get(player);
    if (archer == null) return "";
    if (archer.ready != null
        && archer.ready.technique() == technique
        && archer.ready.end() > MartialTechniques.now(player)) {
      return MartialTechniques.PRIMED;
    }
    if (technique == MartialTechnique.MARKING_SHOT
        && archer.mark != null
        && archer.mark.end() > MartialTechniques.now(player)) {
      return MartialTechniques.MARKED;
    }
    return "";
  }

  private static void draw(ArrowLooseEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || !event.getBow().is(Items.BOW)
        || event.getCharge() <= 0
        || !player.getProjectile(event.getBow()).is(Items.ARROW)) return;
    var archer = ARCHERS.get(player);
    if (archer != null && ready(player, archer) == MartialTechnique.QUICK_SHOT) {
      event.setCharge(Math.round(event.getCharge() / MartialTechnique.QUICK_DRAW_TIME));
    }
  }

  @Nullable
  private static MartialTechnique ready(ServerPlayer player, Archer archer) {
    ReadyShot shot = archer.ready;
    return shot != null
            && shot.end() > MartialTechniques.now(player)
            && MartialTechniques.learned(player, shot.technique())
        ? shot.technique()
        : null;
  }

  public static void launched(Entity projectile, ItemStack weapon, ItemStack ammo) {
    if (!(projectile instanceof AbstractArrow arrow)
        || arrow.getType() != EntityType.ARROW
        || !(arrow.getOwner() instanceof ServerPlayer player)
        || !weapon.is(Items.BOW)
        || !ammo.is(Items.ARROW)) return;
    Archer archer = ARCHERS.get(player);
    if (archer == null) return;
    MartialTechnique technique = ready(player, archer);
    SHOTS.put(
        arrow,
        new Shot(
            archer, ++archer.launched, MartialTechniques.now(player) + SHOT_LIFETIME, technique));
    if (technique != null) {
      archer.ready = null;
      PreparedAbilities.sync(player, false);
    }
  }

  @Nullable
  private static Shot shot(ServerPlayer player, AbstractArrow arrow) {
    Shot shot = SHOTS.get(arrow);
    return shot != null
            && shot.archer() == ARCHERS.get(player)
            && shot.end() > MartialTechniques.now(player)
        ? shot
        : null;
  }

  private static boolean isFollowUp(ServerPlayer player, LivingEntity target, Shot shot) {
    Mark mark = shot.archer().mark;
    return mark != null
        && mark.end() > MartialTechniques.now(player)
        && MartialTechniques.learned(player, MartialTechnique.MARKING_SHOT)
        && shot.technique() != MartialTechnique.MARKING_SHOT
        && shot.number() > mark.lastLaunched()
        && mark.target().equals(target.getUUID());
  }

  public static float impactDamage(AbstractArrow arrow, LivingEntity target, float damage) {
    if (!(arrow.getOwner() instanceof ServerPlayer player)) return damage;
    Shot shot = shot(player, arrow);
    if (shot == null) return damage;
    float multiplier = shot.technique() == MartialTechnique.QUICK_SHOT ? 0.90F : 1.0F;
    if (isFollowUp(player, target, shot)) multiplier *= 1.70F;
    return damage * multiplier;
  }

  public static void landed(AbstractArrow arrow, LivingEntity target) {
    if (!(arrow.getOwner() instanceof ServerPlayer player)) return;
    Shot shot = shot(player, arrow);
    if (shot == null) return;
    SHOTS.remove(arrow);
    if (isFollowUp(player, target, shot)) {
      shot.archer().mark = null;
      player.playNotifySound(SoundEvents.PLAYER_ATTACK_CRIT, player.getSoundSource(), 0.55F, 1.4F);
      PreparedAbilities.sync(player, false);
    }
    if (!target.isAlive()
        || shot.technique() == null
        || !MartialTechniques.learned(player, shot.technique())) return;
    if (shot.technique() == MartialTechnique.SLOWING_SHOT) {
      target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 3), player);
    } else if (shot.technique() == MartialTechnique.MARKING_SHOT && player.hasLineOfSight(target)) {
      shot.archer().mark =
          new Mark(
              target.getUUID(),
              MartialTechniques.now(player) + MARK_LIFETIME,
              shot.archer().launched);
      SpellKnowledge.message(player, MartialTechniques.MARKED);
      showMark(player, target);
      PreparedAbilities.sync(player, false);
    }
  }

  static void tick(ServerPlayer player) {
    Archer archer = ARCHERS.get(player);
    if (archer == null || archer.mark == null) return;
    if (archer.mark.end() <= MartialTechniques.now(player)
        || !MartialTechniques.learned(player, MartialTechnique.MARKING_SHOT)
        || !(player.serverLevel().getEntity(archer.mark.target()) instanceof LivingEntity target)
        || !target.isAlive()) {
      archer.mark = null;
    } else if (player.tickCount % 8 == 0 && player.hasLineOfSight(target)) {
      showMark(player, target);
    }
  }

  private static void showMark(ServerPlayer player, LivingEntity target) {
    player
        .serverLevel()
        .sendParticles(
            player,
            ParticleTypes.WAX_OFF,
            false,
            target.getX(),
            target.getY(0.95) + 0.2,
            target.getZ(),
            6,
            0.16,
            0.10,
            0.16,
            0.05);
  }

  static void reconcile(ServerPlayer player, List<String> slots) {
    Archer archer = ARCHERS.get(player);
    if (archer != null && archer.ready != null && !slots.contains(archer.ready.technique().id()))
      archer.ready = null;
  }

  static void reset(ServerPlayer player) {
    ARCHERS.remove(player);
  }
}
