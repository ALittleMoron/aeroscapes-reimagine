package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.magic.AbilityNetwork;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.races.ClassProgression;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Server-owned short windows. The ordinary attack, block and release paths still perform the
 * action.
 */
public final class MartialTechniques {
  public static final String PRIMED = "technique_primed";
  public static final String MARKED = "technique_marked";
  private static final int RECENT_BLOCK_TICKS = 50;
  private static final String COOLDOWNS = "aeroscapes_reimagine:technique_cooldowns";
  private static final TagKey<Item> HEAVY_WEAPONS = weapons("heavy_technique_weapons");
  private static final TagKey<Item> LIGHT_WEAPONS = weapons("light_weapons");
  private static final AttributeModifier BRACED_MOVEMENT =
      new AttributeModifier(
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "shield_brace"),
          -0.25,
          AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
  private static final Map<ServerPlayer, Stance> STANCES = new WeakHashMap<>();

  private record MeleeWindow(MartialTechnique technique, long end, @Nullable UUID target) {}

  private record Brace(long end, ItemStack shield) {}

  private record RecentBlock(UUID attacker, long end) {}

  private static final class Stance {
    private @Nullable MeleeWindow melee;
    private @Nullable Brace brace;
    private long motionEnd;
    private long dashEnd;
    private long disengageEnd;
    private @Nullable RecentBlock recentBlock;
    private @Nullable WeakReference<DamageSource> bracedBlock;
  }

  private MartialTechniques() {}

  public static void register() {
    MartialArchery.register();
    NeoForge.EVENT_BUS.addListener(MartialTechniques::protectDisengage);
  }

  private static TagKey<Item> weapons(String name) {
    return TagKey.create(
        Registries.ITEM, ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", name));
  }

  static long now(ServerPlayer player) {
    return player.server.overworld().getGameTime();
  }

  public static boolean learned(ServerPlayer player, MartialTechnique technique) {
    return ClassProgression.hasCombatTalent(player, technique.talent());
  }

  private static CompoundTag cooldowns(ServerPlayer player) {
    return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(COOLDOWNS);
  }

  private static int cooldown(ServerPlayer player, MartialTechnique technique) {
    CompoundTag cooldowns = cooldowns(player);
    long deadline = cooldowns.getLong(technique.cooldownKey());
    // A 0.9.0 save recorded one deadline without the identity of the technique used.
    if (technique.footwork()) deadline = Math.max(deadline, cooldowns.getLong("footwork"));
    return Math.clamp(deadline - now(player), 0, technique.cooldownTicks());
  }

  private static void startCooldown(ServerPlayer player, MartialTechnique technique) {
    CompoundTag cooldowns = cooldowns(player);
    cooldowns.putLong(technique.cooldownKey(), now(player) + technique.cooldownTicks());
    var persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    persistent.put(COOLDOWNS, cooldowns);
    player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
  }

  public static void catalogue(ServerPlayer player, List<AbilityNetwork.Entry> entries) {
    for (var technique : MartialTechnique.values()) {
      boolean learned = learned(player, technique);
      int remaining = cooldown(player, technique);
      String reason = reason(player, technique, learned, remaining);
      String status = learned ? status(player, technique) : "";
      entries.add(
          new AbilityNetwork.Entry(
              technique.id(),
              MartialTechnique.GROUP,
              learned ? 1 : 0,
              1,
              1,
              reason.isEmpty(),
              status.isEmpty() ? reason : status,
              remaining,
              0));
    }
  }

  public static void activate(ServerPlayer player, MartialTechnique technique) {
    String reason =
        reason(player, technique, learned(player, technique), cooldown(player, technique));
    if (!reason.isEmpty()) {
      SpellKnowledge.message(player, reason);
      return;
    }
    long end = now(player) + technique.windowTicks();
    switch (technique) {
      case DASH, DISENGAGE -> {
        if (!step(player, technique == MartialTechnique.DASH ? 6.0 : -5.0)) {
          SpellKnowledge.message(player, "technique_blocked");
          return;
        }
        Stance stance = STANCES.computeIfAbsent(player, ignored -> new Stance());
        stance.motionEnd = now(player) + GroundFootwork.DURATION;
        if (technique == MartialTechnique.DASH) stance.dashEnd = end;
        else stance.disengageEnd = end;
      }
      case SLOWING_SHOT, MARKING_SHOT, QUICK_SHOT -> MartialArchery.prime(player, technique, end);
      case SHIELD_BRACE -> {
        Stance stance = STANCES.computeIfAbsent(player, ignored -> new Stance());
        stance.brace = new Brace(end, player.getUseItem());
        braceMovement(player, true);
        player.playNotifySound(SoundEvents.SHIELD_BLOCK, player.getSoundSource(), 0.55F, 0.78F);
      }
      case COUNTERATTACK, FLANK_STRIKE, HEAVY_STRIKE -> primeMelee(player, technique, end);
    }
    startCooldown(player, technique);
    if (technique.windowTicks() > 0) SpellKnowledge.message(player, PRIMED);
  }

  private static void primeMelee(ServerPlayer player, MartialTechnique technique, long end) {
    Stance stance = STANCES.computeIfAbsent(player, ignored -> new Stance());
    UUID target = null;
    if (technique == MartialTechnique.COUNTERATTACK && stance.recentBlock != null) {
      target = stance.recentBlock.attacker();
      stance.recentBlock = null;
    }
    stance.melee = new MeleeWindow(technique, end, target);
  }

  private static String reason(
      ServerPlayer player, MartialTechnique technique, boolean learned, int remaining) {
    if (!learned) return "technique_unlearned";
    if (!player.isAlive() || player.isSpectator() || player.isSleeping())
      return "technique_unavailable";
    if (remaining > 0) return "cooldown";
    return switch (technique) {
      case SHIELD_BRACE -> player.isBlocking() ? "" : "technique_shield";
      case COUNTERATTACK -> recentBlock(player) ? "" : "technique_block_first";
      case HEAVY_STRIKE -> wields(player, HEAVY_WEAPONS) ? "" : "technique_heavy";
      case FLANK_STRIKE -> wields(player, LIGHT_WEAPONS) ? "" : "technique_light";
      case SLOWING_SHOT, MARKING_SHOT, QUICK_SHOT -> MartialArchery.equipmentReason(player);
      case DASH, DISENGAGE -> footworkReason(player);
    };
  }

  private static String footworkReason(ServerPlayer player) {
    var stance = STANCES.get(player);
    if (stance != null && stance.motionEnd > now(player)) return "technique_moving";
    return GroundFootwork.available(player) ? "" : "technique_ground";
  }

  private static boolean wields(ServerPlayer player, TagKey<Item> weapons) {
    return player.getMainHandItem().is(weapons)
        || player.getOffhandItem().is(weapons) && WeaponProficiency.supportsOffhandAttack(player);
  }

  private static String status(ServerPlayer player, MartialTechnique technique) {
    return switch (technique) {
      case DASH, DISENGAGE -> footworkActive(player, technique) ? PRIMED : "";
      case SLOWING_SHOT, MARKING_SHOT, QUICK_SHOT -> MartialArchery.status(player, technique);
      case SHIELD_BRACE -> braced(player) ? PRIMED : "";
      case COUNTERATTACK, FLANK_STRIKE, HEAVY_STRIKE -> {
        Stance stance = STANCES.get(player);
        yield stance != null
                && stance.melee != null
                && stance.melee.technique() == technique
                && stance.melee.end() > now(player)
            ? PRIMED
            : "";
      }
    };
  }

  private static boolean recentBlock(ServerPlayer player) {
    Stance stance = STANCES.get(player);
    return stance != null
        && stance.recentBlock != null
        && stance.recentBlock.end() > now(player)
        && player.serverLevel().getEntity(stance.recentBlock.attacker())
            instanceof LivingEntity attacker
        && attacker.isAlive();
  }

  private static boolean braced(ServerPlayer player) {
    Stance stance = STANCES.get(player);
    return stance != null
        && stance.brace != null
        && stance.brace.end() > now(player)
        && stance.brace.shield() == player.getUseItem()
        && player.isBlocking()
        && learned(player, MartialTechnique.SHIELD_BRACE);
  }

  private static void braceMovement(ServerPlayer player, boolean active) {
    var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
    if (movement == null || movement.hasModifier(BRACED_MOVEMENT.id()) == active) return;
    if (active) movement.addTransientModifier(BRACED_MOVEMENT);
    else movement.removeModifier(BRACED_MOVEMENT.id());
  }

  public static void observeBlock(LivingShieldBlockEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || event.isCanceled()
        || !event.getOriginalBlock()
        || !event.getBlocked()
        || event.getBlockedDamage() <= 0.0F) return;
    if (braced(player)) {
      STANCES.get(player).bracedBlock = new WeakReference<>(event.getDamageSource());
      event.setShieldDamage(event.shieldDamage() * 0.35F);
    }
    if (learned(player, MartialTechnique.COUNTERATTACK)
        && event.getDamageSource().getEntity() instanceof LivingEntity attacker
        && attacker != player) {
      STANCES.computeIfAbsent(player, ignored -> new Stance()).recentBlock =
          new RecentBlock(attacker.getUUID(), now(player) + RECENT_BLOCK_TICKS);
    }
  }

  public static double braceKnockback(LivingEntity entity, DamageSource source, double strength) {
    if (!(entity instanceof ServerPlayer player)) return strength;
    Stance stance = STANCES.get(player);
    if (stance == null || stance.bracedBlock == null || stance.bracedBlock.get() != source)
      return strength;
    stance.bracedBlock = null;
    return strength * 0.10;
  }

  @Nullable
  public static MartialTechnique meleeTechnique(ServerPlayer player, LivingEntity target) {
    Stance stance = STANCES.get(player);
    if (stance == null
        || stance.melee == null
        || stance.melee.end() <= now(player)
        || player.isAutoSpinAttack()
        || !learned(player, stance.melee.technique())) return null;
    // Better Combat swaps the swinging offhand stack into the selected inventory slot for attack.
    ItemStack weapon = player.getWeaponItem();
    return switch (stance.melee.technique()) {
      case COUNTERATTACK ->
          target.getUUID().equals(stance.melee.target()) ? MartialTechnique.COUNTERATTACK : null;
      case HEAVY_STRIKE -> weapon.is(HEAVY_WEAPONS) ? MartialTechnique.HEAVY_STRIKE : null;
      case FLANK_STRIKE ->
          weapon.is(LIGHT_WEAPONS) && flanking(player, target)
              ? MartialTechnique.FLANK_STRIKE
              : null;
      default -> null;
    };
  }

  public static float meleeMultiplier(MartialTechnique technique) {
    return switch (technique) {
      case COUNTERATTACK -> 1.70F;
      case HEAVY_STRIKE -> 1.50F;
      case FLANK_STRIKE -> 1.85F;
      default -> 1.0F;
    };
  }

  private static boolean footworkActive(ServerPlayer player, MartialTechnique technique) {
    Stance stance = STANCES.get(player);
    return stance != null
        && (technique == MartialTechnique.DASH ? stance.dashEnd : stance.disengageEnd) > now(player)
        && learned(player, technique);
  }

  public static boolean dashAttack(ServerPlayer player) {
    return footworkActive(player, MartialTechnique.DASH)
        && !player.isAutoSpinAttack()
        && player.getWeaponItem().is(LIGHT_WEAPONS);
  }

  public static void dashLanded(ServerPlayer player) {
    STANCES.get(player).dashEnd = 0;
    player.playNotifySound(SoundEvents.PLAYER_ATTACK_CRIT, player.getSoundSource(), 0.65F, 1.25F);
  }

  private static void protectDisengage(LivingDamageEvent.Pre event) {
    var source = event.getSource();
    if (event.getEntity() instanceof ServerPlayer player
        && event.getNewDamage() > 0.0F
        && !source.is(DamageTypeTags.BYPASSES_ARMOR)
        && source.getEntity() instanceof LivingEntity attacker
        && attacker != player
        && source.getDirectEntity() == attacker
        && footworkActive(player, MartialTechnique.DISENGAGE)) {
      STANCES.get(player).disengageEnd = 0;
      event.setNewDamage(event.getNewDamage() * 0.5F);
      player.playNotifySound(
          SoundEvents.PLAYER_ATTACK_NODAMAGE, player.getSoundSource(), 0.55F, 1.5F);
    }
  }

  private static boolean flanking(ServerPlayer player, LivingEntity target) {
    Vec3 offset = player.position().subtract(target.position());
    Vec3 horizontal = new Vec3(offset.x, 0.0, offset.z);
    return horizontal.horizontalDistanceSqr() > 1.0E-5
        && horizontal.normalize().dot(Vec3.directionFromRotation(0.0F, target.getYHeadRot()))
            <= 0.26;
  }

  public static void meleeLanded(
      ServerPlayer player, LivingEntity target, MartialTechnique technique, boolean bonus) {
    Stance stance = STANCES.get(player);
    if (stance != null) stance.melee = null;
    if (technique == MartialTechnique.HEAVY_STRIKE && interrupt(target)) {
      player.playNotifySound(SoundEvents.SHIELD_BLOCK, player.getSoundSource(), 0.65F, 1.5F);
      SpellKnowledge.message(player, "technique_interrupted");
    }
    if (bonus) {
      player
          .serverLevel()
          .sendParticles(
              ParticleTypes.CRIT,
              target.getX(),
              target.getY(0.55),
              target.getZ(),
              9,
              0.25,
              0.25,
              0.25,
              0.25);
    }
  }

  private static boolean interrupt(LivingEntity target) {
    if (!target.isAlive() || !target.isUsingItem()) return false;
    // These native goals restart their draw after the held item is released without firing.
    EntityType<?> type = target.getType();
    boolean skeletonDraw =
        (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED)
            && target.getUseItem().is(Items.BOW);
    if (type == EntityType.PILLAGER
        && target instanceof Pillager pillager
        && !pillager.isCaptain()
        && pillager.isChargingCrossbow()
        && target.getUseItem().is(Items.CROSSBOW)) {
      target.stopUsingItem();
      pillager.setChargingCrossbow(false);
      return true;
    }
    if (skeletonDraw) target.stopUsingItem();
    return skeletonDraw;
  }

  private static boolean step(ServerPlayer player, double distance) {
    Vec3 direction = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(distance);
    Vec3 travel = GroundFootwork.supportedTravel(player, direction);
    if (travel.lengthSqr() < 1.0E-10) return false;
    PacketDistributor.sendToPlayer(player, new AbilityNetwork.Footwork(travel));
    player
        .serverLevel()
        .sendParticles(
            ParticleTypes.DUST_PLUME,
            player.getX(),
            player.getY() + 0.1,
            player.getZ(),
            5,
            0.12,
            0.04,
            0.12,
            0.02);
    player.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 0.35F, 1.4F);
    return true;
  }

  public static void tick(ServerPlayer player) {
    Stance stance = STANCES.get(player);
    if (stance != null && stance.brace != null && !braced(player)) {
      stance.brace = null;
      braceMovement(player, false);
    }
    MartialArchery.tick(player);
  }

  public static void reconcile(ServerPlayer player, List<String> slots) {
    Stance stance = STANCES.get(player);
    if (stance != null) {
      if (stance.melee != null && !slots.contains(stance.melee.technique().id()))
        stance.melee = null;
      if (!slots.contains(MartialTechnique.DASH.id())) stance.dashEnd = 0;
      if (!slots.contains(MartialTechnique.DISENGAGE.id())) stance.disengageEnd = 0;
      if (stance.brace != null && !slots.contains(MartialTechnique.SHIELD_BRACE.id())) {
        stance.brace = null;
        braceMovement(player, false);
      }
    }
    MartialArchery.reconcile(player, slots);
  }

  public static void reset(ServerPlayer player) {
    if (STANCES.remove(player) != null) braceMovement(player, false);
    MartialArchery.reset(player);
  }
}
