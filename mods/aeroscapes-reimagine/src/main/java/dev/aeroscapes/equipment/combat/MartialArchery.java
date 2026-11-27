package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

/** Runs native bow use for prepared shots and tracks their projectile effects. */
public final class MartialArchery {
  public static final String DRAWING = "technique_drawing";
  private static final int SHOT_LIFETIME = 200;
  private static final int MARK_LIFETIME = 140;
  private static final int SLOW_TICKS = 80;
  private static final int VOLLEY_INTERVAL = 5;
  private static final Map<ServerPlayer, Archer> ARCHERS = new WeakHashMap<>();
  private static final Map<AbstractArrow, Shot> SHOTS = new WeakHashMap<>();

  private record Drawing(
      MartialTechnique technique, InteractionHand hand, ItemStack bow, ServerLevel level) {}

  private record Mark(UUID target, long end, long lastLaunched) {}

  private record Shot(
      Archer archer,
      long number,
      long end,
      @Nullable MartialTechnique technique,
      @Nullable Volley volley) {}

  private record Volley(
      ServerLevel level,
      Vec3 origin,
      Vec3 velocity,
      ItemStack bow,
      ArrayDeque<ItemStack> remaining,
      Set<UUID> hitTargets) {}

  private static final class Archer {
    private long launched;
    private @Nullable Drawing drawing;
    private boolean releasing;
    private @Nullable Mark mark;
    private List<ItemStack> stagedArrows = List.of();
    private @Nullable Volley volley;
    private long nextVolleyShot;
  }

  private MartialArchery() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MartialArchery::draw);
    NeoForge.EVENT_BUS.addListener(MartialArchery::showVolleyTrail);
  }

  private static InteractionHand bowHand(ServerPlayer player) {
    if (player.getUseItem().is(Items.BOW)) return player.getUsedItemHand();
    return player.getMainHandItem().is(Items.BOW)
        ? InteractionHand.MAIN_HAND
        : InteractionHand.OFF_HAND;
  }

  static String reason(ServerPlayer player, MartialTechnique technique) {
    Archer archer = ARCHERS.get(player);
    if (archer != null && archer.drawing != null && usingBow(player, archer.drawing)) {
      return archer.drawing.technique() == technique ? DRAWING : "technique_bow_busy";
    }
    var bow = player.getItemInHand(bowHand(player));
    if (player.isUsingItem() && player.getUseItem() != bow) return "technique_bow_busy";
    return ammunitionReason(player, technique, bow);
  }

  private static String ammunitionReason(
      ServerPlayer player, MartialTechnique technique, ItemStack bow) {
    if (!bow.is(Items.BOW)) return "technique_bow";
    var projectile = player.getProjectile(bow);
    if (technique == MartialTechnique.TRIPLE_SHOT) {
      return tripleSources(player, projectile).isEmpty() ? "technique_three_arrows" : "";
    }
    return ordinaryArrow(projectile) ? "" : "technique_arrow";
  }

  static void beginDraw(ServerPlayer player, MartialTechnique technique) {
    InteractionHand hand = bowHand(player);
    ItemStack bow = player.getItemInHand(hand);
    var drawing = new Drawing(technique, hand, bow, player.serverLevel());
    if (!player.isUsingItem()) player.gameMode.useItem(player, drawing.level(), bow, hand);
    if (!usingBow(player, drawing) || player.getItemInHand(hand) != bow) {
      SpellKnowledge.message(player, "technique_unavailable");
      return;
    }
    ARCHERS.computeIfAbsent(player, ignored -> new Archer()).drawing = drawing;
  }

  private static boolean usingBow(ServerPlayer player, Drawing drawing) {
    return player.isUsingItem()
        && player.getUsedItemHand() == drawing.hand()
        && player.getUseItem() == drawing.bow();
  }

  public static boolean ownsBowRelease(ServerPlayer player) {
    Archer archer = ARCHERS.get(player);
    return archer != null && archer.drawing != null && usingBow(player, archer.drawing);
  }

  public static void interrupted(ServerPlayer player) {
    Archer archer = ARCHERS.get(player);
    if (archer != null && archer.drawing != null && !usingBow(player, archer.drawing)) {
      archer.drawing = null;
      PreparedAbilities.sync(player, false);
    }
  }

  private static List<ItemStack> tripleSources(ServerPlayer player, ItemStack selected) {
    if (!ordinaryArrow(selected)) return List.of();
    var inventory = player.getInventory();
    var sources = new ArrayList<ItemStack>(3);
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      if (inventory.getItem(slot) == selected) {
        addArrowSources(sources, selected);
        break;
      }
    }
    if (sources.isEmpty()) return List.of();
    for (int slot = 0; slot < inventory.getContainerSize() && sources.size() < 3; slot++) {
      ItemStack stack = inventory.getItem(slot);
      if (stack != selected && ordinaryArrow(stack)) addArrowSources(sources, stack);
    }
    return sources.size() == 3 ? sources : List.of();
  }

  private static boolean ordinaryArrow(ItemStack stack) {
    return stack.is(Items.ARROW) && !SmokeArrows.isSmoke(stack);
  }

  private static void addArrowSources(List<ItemStack> sources, ItemStack stack) {
    int count = Math.min(stack.getCount(), 3 - sources.size());
    for (int index = 0; index < count; index++) sources.add(stack);
  }

  /** Returns the first reserved arrow so the bow still handles the ordinary release. */
  public static ItemStack drawTriple(ServerPlayer player, ItemStack bow, ItemStack selected) {
    Archer archer = ARCHERS.get(player);
    if (archer == null || releasing(archer, bow) != MartialTechnique.TRIPLE_SHOT)
      return ItemStack.EMPTY;
    var sources = tripleSources(player, selected);
    if (sources.isEmpty()) return ItemStack.EMPTY;
    var arrows = new ArrayList<ItemStack>(3);
    for (var stack : sources) arrows.add(stack.split(1));
    player.getInventory().setChanged();
    archer.stagedArrows = List.copyOf(arrows.subList(1, arrows.size()));
    return arrows.getFirst();
  }

  public static void finishRelease(ServerPlayer player) {
    Archer archer = ARCHERS.get(player);
    if (archer != null) archer.stagedArrows = List.of();
  }

  static String status(ServerPlayer player, MartialTechnique technique) {
    Archer archer = ARCHERS.get(player);
    if (archer == null) return "";
    if (archer.drawing != null
        && archer.drawing.technique() == technique
        && usingBow(player, archer.drawing)) {
      return DRAWING;
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
        || !ordinaryArrow(player.getProjectile(event.getBow()))) return;
    var archer = ARCHERS.get(player);
    if (archer != null && releasing(archer, event.getBow()) == MartialTechnique.QUICK_SHOT) {
      event.setCharge(quickCharge(event.getCharge()));
    }
  }

  private static int quickCharge(int charge) {
    return Math.round(charge / MartialTechnique.QUICK_DRAW_TIME);
  }

  @Nullable
  private static MartialTechnique releasing(Archer archer, ItemStack bow) {
    Drawing drawing = archer.drawing;
    return archer.releasing && drawing != null && drawing.bow() == bow ? drawing.technique() : null;
  }

  public static void launched(Entity projectile, ItemStack weapon, ItemStack ammo) {
    if (!(projectile instanceof AbstractArrow arrow)
        || arrow.getType() != EntityType.ARROW
        || !(arrow.getOwner() instanceof ServerPlayer player)
        || !weapon.is(Items.BOW)
        || !ammo.is(Items.ARROW)) return;
    if (SmokeArrows.isSmoke(ammo)) {
      SmokeArrows.launched(arrow, ammo);
      return;
    }
    Archer archer = ARCHERS.get(player);
    if (archer == null) return;
    MartialTechnique technique = releasing(archer, weapon);
    Volley volley = null;
    if (technique == MartialTechnique.TRIPLE_SHOT) {
      if (archer.stagedArrows.isEmpty()) {
        technique = null;
      } else {
        volley =
            new Volley(
                player.serverLevel(),
                arrow.position(),
                arrow.getDeltaMovement(),
                weapon.copy(),
                new ArrayDeque<>(archer.stagedArrows),
                new HashSet<>());
        archer.stagedArrows = List.of();
        archer.volley = volley;
        archer.nextVolleyShot = MartialTechniques.now(player) + VOLLEY_INTERVAL;
        showVolleyRelease(volley.level(), arrow);
      }
    }
    SHOTS.put(
        arrow,
        new Shot(
            archer,
            ++archer.launched,
            MartialTechniques.now(player) + SHOT_LIFETIME,
            technique,
            volley));
    if (technique != null) MartialTechniques.startCooldown(player, technique);
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

  public static boolean landedInVolley(AbstractArrow arrow, LivingEntity target) {
    if (!(arrow.getOwner() instanceof ServerPlayer player)) return false;
    Shot shot = shot(player, arrow);
    return shot != null
        && shot.volley() != null
        && shot.volley().hitTargets().contains(target.getUUID());
  }

  public static void landed(AbstractArrow arrow, LivingEntity target, boolean damaged) {
    if (!(arrow.getOwner() instanceof ServerPlayer player)) return;
    Shot shot = shot(player, arrow);
    if (shot == null) return;
    SHOTS.remove(arrow);
    if (damaged && shot.volley() != null) shot.volley().hitTargets().add(target.getUUID());
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
    if (archer == null) return;
    if (archer.drawing != null) tickDraw(player, archer, archer.drawing);
    tickVolley(player, archer);
    if (archer.mark == null) return;
    if (archer.mark.end() <= MartialTechniques.now(player)
        || !MartialTechniques.learned(player, MartialTechnique.MARKING_SHOT)
        || !(player.serverLevel().getEntity(archer.mark.target()) instanceof LivingEntity target)
        || !target.isAlive()) {
      archer.mark = null;
    } else if (player.tickCount % 8 == 0 && player.hasLineOfSight(target)) {
      showMark(player, target);
    }
  }

  private static void tickDraw(ServerPlayer player, Archer archer, Drawing drawing) {
    var prepared = PreparedAbilities.preparation(player);
    if (player.serverLevel() != drawing.level()
        || !player.isAlive()
        || player.isSpectator()
        || player.isSleeping()
        || !usingBow(player, drawing)
        || player.getItemInHand(drawing.hand()) != drawing.bow()
        || !prepared.slots().get(prepared.selected()).equals(drawing.technique().id())
        || !MartialTechniques.learned(player, drawing.technique())
        || !ammunitionReason(player, drawing.technique(), drawing.bow()).isEmpty()) {
      cancelDraw(player, archer);
      PreparedAbilities.sync(player, false);
      return;
    }
    int charge =
        (int) (player.getTicksUsingItem() / RangedReforges.bowDrawTime(drawing.bow(), player));
    if (drawing.technique() == MartialTechnique.QUICK_SHOT) charge = quickCharge(charge);
    if (BowItem.getPowerForTime(charge) < 1.0F) return;
    archer.releasing = true;
    try {
      player.releaseUsingItem();
    } finally {
      archer.releasing = false;
      archer.drawing = null;
      PreparedAbilities.sync(player, false);
    }
  }

  private static void cancelDraw(ServerPlayer player, Archer archer) {
    Drawing drawing = archer.drawing;
    archer.drawing = null;
    if (drawing != null && usingBow(player, drawing)) player.stopUsingItem();
  }

  private static void tickVolley(ServerPlayer player, Archer archer) {
    Volley volley = archer.volley;
    if (volley == null) return;
    if (player.serverLevel() != volley.level() || !player.isAlive() || player.isSpectator()) {
      archer.volley = null;
      return;
    }
    long now = MartialTechniques.now(player);
    if (now < archer.nextVolleyShot) return;
    archer.nextVolleyShot = now + VOLLEY_INTERVAL;
    ItemStack ammo = volley.remaining().removeFirst();
    Vec3 speed = volley.velocity();
    var arrow =
        ((ArrowItem) ammo.getItem()).createArrow(volley.level(), ammo, player, volley.bow());
    arrow.setPos(volley.origin());
    arrow.shoot(speed.x, speed.y, speed.z, (float) speed.length(), 0.0F);
    arrow.setBaseDamage(arrow.getBaseDamage() * 0.55);
    if (volley.level().addFreshEntity(arrow)) {
      SHOTS.put(
          arrow,
          new Shot(
              archer,
              ++archer.launched,
              now + SHOT_LIFETIME,
              MartialTechnique.TRIPLE_SHOT,
              volley));
      showVolleyRelease(volley.level(), arrow);
      volley
          .level()
          .playSound(
              null,
              arrow.getX(),
              arrow.getY(),
              arrow.getZ(),
              SoundEvents.ARROW_SHOOT,
              player.getSoundSource(),
              0.85F,
              1.3F);
    }
    if (volley.remaining().isEmpty()) archer.volley = null;
  }

  private static void showVolleyRelease(ServerLevel level, AbstractArrow arrow) {
    Vec3 point = arrow.position().add(arrow.getDeltaMovement().normalize().scale(1.0));
    level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 5, 0.11, 0.11, 0.11, 0.04);
  }

  private static void showVolleyTrail(EntityTickEvent.Post event) {
    if (!(event.getEntity() instanceof AbstractArrow arrow)
        || arrow.tickCount > 3
        || arrow.isCritArrow()
        || arrow.isRemoved()
        || arrow.getDeltaMovement().lengthSqr() < 0.5
        || !(arrow.level() instanceof ServerLevel level)) return;
    Shot shot = SHOTS.get(arrow);
    if (shot == null || shot.volley() == null) return;
    level.sendParticles(
        ParticleTypes.CRIT, arrow.getX(), arrow.getY(), arrow.getZ(), 3, 0.08, 0.08, 0.08, 0.01);
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
    if (archer != null
        && archer.drawing != null
        && !slots.contains(archer.drawing.technique().id())) cancelDraw(player, archer);
  }

  static void reset(ServerPlayer player) {
    Archer archer = ARCHERS.remove(player);
    if (archer != null) cancelDraw(player, archer);
  }
}
