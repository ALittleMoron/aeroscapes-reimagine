package dev.aeroscapes.equipment.combat;

import com.herobrot.tieredneo.api.TieredNeoAPI;
import dev.aeroscapes.equipment.ReforgeProfiles;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class RangedReforges {
  private static final String SHOT_DATA = "aeroscapes_reimagine:reforged_shot";
  private static final String BOW_BIND = "bow_bind";
  private static final String SLINGSHOT_STAGGER = "slingshot_stagger";
  private static final String TRIDENT_TIDE = "trident_tide";

  private static final String SIGHTING = "bow_legendary_sighting";
  private static final String LINE_OF_FIRE = "crossbow_legendary_fire_line";
  private static final String RETURN_THRUST = "trident_legendary_return_thrust";
  private static final String RICOCHET = "slingshot_legendary_ricochet";
  private static final Map<Projectile, SightingShot> SIGHTING_SHOTS = new WeakHashMap<>();

  private record SightingShot(WeakReference<ItemStack> weapon, Vec3 origin) {}

  private RangedReforges() {}

  public interface FixedSpeedArrow {
    void aeroscapes$setSpeedMultiplier(float multiplier);
  }

  public static float bowDrawTime(ItemStack stack) {
    return switch (ReforgeProfiles.path(stack)) {
      case "ranged_poor_with_bow_drag" -> 1.15F;
      case "ranged_ordinary_with_bow_tempo" -> 0.95F;
      case "ranged_fine_with_bow_tempo" -> 0.90F;
      case "bow_exceptional_draw_with_tempo" -> 0.78F;
      case SIGHTING -> 0.90F;
      default ->
          switch (ReforgeProfiles.basePath(stack)) {
            case "bow_exceptional_draw" -> 0.85F;
            case "bow_epic", "bow_legendary_mark", "bow_legendary_bind" -> 0.90F;
            default -> 1.0F;
          };
    };
  }

  @SubscribeEvent
  public static void bowReleased(ArrowLooseEvent event) {
    if (event.getBow().getItem() instanceof BowItem && event.getCharge() > 0) {
      float draw = bowDrawTime(event.getBow());
      if (draw != 1.0F) {
        event.setCharge((int) (event.getCharge() / draw));
      }
    }
  }

  public static int slingshotCharge(ItemStack stack, int duration) {
    return switch (ReforgeProfiles.basePath(stack)) {
      case "slingshot_poor" -> scaleTicks(duration, 1.10F);
      case "slingshot_fine" -> scaleTicks(duration, 0.95F);
      case "slingshot_exceptional_draw" -> scaleTicks(duration, 0.88F);
      case "slingshot_epic", "slingshot_legendary_stagger", RICOCHET -> scaleTicks(duration, 0.92F);
      default -> duration;
    };
  }

  static int scaleTicks(int duration, float scale) {
    return Math.max(1, Math.round(duration * scale));
  }

  public static void launch(Projectile projectile, ItemStack weapon, ItemStack ammo, boolean full) {
    if (!(projectile.getOwner() instanceof ServerPlayer player)
        || !ModList.get().isLoaded("tieredneo")) {
      return;
    }
    String rank = ReforgeProfiles.path(weapon);
    if (projectile instanceof AbstractArrow
        && (weapon.getItem() instanceof BowItem
            || weapon.is(Items.CROSSBOW)
            || (!rank.isEmpty() && weapon.getItem() instanceof CrossbowItem))) {
      double velocity =
          switch (rank) {
            case "bow_epic_with_trajectory", "crossbow_epic_with_trajectory" -> 1.12;
            case SIGHTING -> full && StackCombatWindows.consume(player, weapon) ? 1.22 : 1.0;
            default ->
                "bow_exceptional_velocity".equals(ReforgeProfiles.basePath(rank)) ? 1.15 : 1.0;
          };
      if (SIGHTING.equals(rank) && full && velocity == 1.0) {
        SIGHTING_SHOTS.put(
            projectile, new SightingShot(new WeakReference<>(weapon), projectile.position()));
      }
      remember(projectile, rank, velocity, tierDamage(weapon, 1.0F), full);
    } else if (rank.startsWith("slingshot_")
        && (ammo.is(Items.STONE) || ammo.is(Items.COBBLESTONE) || ammo.is(Items.FLINT))) {
      double velocity =
          switch (rank) {
            case "slingshot_poor_with_drag" -> 0.92;
            case "slingshot_ordinary_with_trajectory" -> 1.03;
            case "slingshot_fine_with_trajectory" -> 1.06;
            default ->
                switch (ReforgeProfiles.basePath(rank)) {
                  case "slingshot_exceptional_velocity" -> 1.15;
                  case "slingshot_epic", "slingshot_legendary_stagger", RICOCHET -> 1.10;
                  default -> 1.0;
                };
          };
      remember(projectile, rank, velocity, 1.0F, false);
    }
  }

  public static void launchTrident(ThrownTrident projectile) {
    String rank = ReforgeProfiles.path(projectile.getWeaponItem());
    double velocity =
        switch (ReforgeProfiles.basePath(rank)) {
          case "trident_exceptional_velocity" -> 1.15;
          case "trident_epic" -> 1.08;
          default -> 1.0;
        };
    if (!rank.isEmpty()) {
      remember(projectile, rank, velocity, 1.0F, false);
    }
  }

  private static void remember(
      Projectile projectile, String rank, double velocity, float damage, boolean full) {
    var data = new CompoundTag();
    data.putString("rank", rank);
    data.putDouble("velocity", velocity);
    data.putFloat("damage", damage);
    data.putBoolean("full", full);
    if (projectile.getOwner() instanceof ServerPlayer player) {
      data.putUUID("shooter", player.getUUID());
    }
    projectile.getPersistentData().put(SHOT_DATA, data);
    if (velocity != 1.0) {
      if (projectile instanceof FixedSpeedArrow fixedSpeed) {
        fixedSpeed.aeroscapes$setSpeedMultiplier((float) velocity);
      } else {
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(velocity));
      }
    }
  }

  @SubscribeEvent
  public static void restoreFixedSpeed(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide()
        && event.getEntity() instanceof Projectile projectile
        && projectile instanceof FixedSpeedArrow fixedSpeed) {
      float velocity = shot(projectile).getFloat("velocity");
      if (velocity > 0.0F) {
        fixedSpeed.aeroscapes$setSpeedMultiplier(velocity);
      }
    }
  }

  public static boolean handled(Projectile projectile) {
    return projectile.getPersistentData().contains(SHOT_DATA, 10);
  }

  public static double arrowImpactDamage(AbstractArrow arrow, double damage) {
    var data = shot(arrow);
    if (data.isEmpty()) {
      return damage;
    }
    return Mth.clamp(
        damage * data.getFloat("damage") / data.getDouble("velocity"), 0.0, Integer.MAX_VALUE);
  }

  @SubscribeEvent
  public static void tridentDamage(LivingIncomingDamageEvent event) {
    if (event.getSource().getDirectEntity() instanceof ThrownTrident trident
        && trident.getOwner() instanceof ServerPlayer) {
      event.setAmount(tierDamage(trident.getWeaponItem(), event.getAmount()));
    }
  }

  private static float tierDamage(ItemStack weapon, float baseDamage) {
    if (ReforgeProfiles.path(weapon).isEmpty()) {
      return baseDamage;
    }
    var tier = TieredNeoAPI.getTierAttribute(TieredNeoAPI.getTierId(weapon));
    if (tier == null) {
      return baseDamage;
    }
    float damage = baseDamage;
    for (var attribute : tier.getAttributes()) {
      if (!"tieredneo:generic.range_attack_damage".equals(attribute.getAttributeTypeID())) {
        continue;
      }
      var modifier = attribute.getEntityAttributeModifier();
      damage =
          switch (modifier.operation()) {
            case ADD_VALUE -> damage + (float) modifier.amount();
            case ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL ->
                damage * (1.0F + (float) modifier.amount());
          };
    }
    return Math.max(0.0F, damage);
  }

  @SubscribeEvent
  public static void projectileHit(LivingDamageEvent.Post event) {
    if (!(event.getSource().getDirectEntity() instanceof AbstractArrow projectile)
        || !(projectile.getOwner() instanceof ServerPlayer player)
        || event.getNewDamage() <= 0.0F) {
      return;
    }
    var data = shot(projectile);
    if (data.isEmpty() || data.getBoolean("spent")) {
      return;
    }
    var target = event.getEntity();
    String rank = data.getString("rank");
    if (LINE_OF_FIRE.equals(rank)) {
      if (originalShooter(projectile, player)
          && projectile.getPierceLevel() > 0
          && MeleeReforges.hostile(player, target)) {
        piercingHit(data, target, player);
      }
      return;
    }
    data.putBoolean("spent", true);
    if (originalShooter(projectile, player) && MeleeReforges.hostile(player, target)) {
      if (SIGHTING.equals(rank)) {
        sightingHit(projectile, target, player);
      } else if (RETURN_THRUST.equals(rank) && projectile instanceof ThrownTrident) {
        data.putBoolean("return_hit", true);
      }
    }
    switch (ReforgeProfiles.basePath(rank)) {
      case "bow_legendary_mark" -> {
        if (data.getBoolean("full") && target.isAlive()) {
          CombatReforgeState.mark(target, player);
        }
      }
      case "bow_legendary_bind" -> {
        if (data.getBoolean("full") && target.isAlive()) {
          slowWithChance(player, target, BOW_BIND, 30, 120, 0.15F);
        }
      }
      case "crossbow_legendary_followup" -> CrossbowFollowup.arm(player);
      case "trident_legendary_tide" -> tide(player, target);
      default -> {}
    }
  }

  public static void slingshotHit(Projectile projectile, LivingEntity target, boolean landed) {
    if (!(projectile.getOwner() instanceof ServerPlayer player)
        || !(target instanceof Enemy)
        || !target.isAlive()
        || player.isAlliedTo(target)) {
      return;
    }
    var data = shot(projectile);
    if (data.getBoolean("spent")) {
      return;
    }
    switch (data.getString("rank")) {
      case "slingshot_exceptional_draw_with_drive", "slingshot_epic_with_drive" -> {
        if (landed && originalShooter(projectile, player)) {
          data.putBoolean("spent", true);
          double strength =
              "slingshot_epic_with_drive".equals(data.getString("rank")) ? 0.48 : 0.32;
          var velocity = projectile.getDeltaMovement();
          target.knockback(strength, -velocity.x, -velocity.z);
        }
      }
      case "slingshot_legendary_stagger" -> {
        data.putBoolean("spent", true);
        slowWithChance(player, target, SLINGSHOT_STAGGER, 25, 160, 1.0F);
      }
      default -> {}
    }
  }

  public static int tridentWindup(ItemStack weapon, int normal) {
    return switch (ReforgeProfiles.path(weapon)) {
      case "trident_poor_with_drag" -> normal + 2;
      case "trident_exceptional_velocity_with_tempo" -> normal - 2;
      case "trident_epic_with_tempo" -> normal - 3;
      default -> normal;
    };
  }

  public static boolean returningTrident(ThrownTrident trident, ServerPlayer player) {
    var data = shot(trident);
    return trident.isNoPhysics()
        && trident.pickup == AbstractArrow.Pickup.ALLOWED
        && trident.getOwner() == player
        && originalShooter(trident, player)
        && RETURN_THRUST.equals(data.getString("rank"))
        && data.getBoolean("return_hit")
        && !CombatReforgeState.active(player, RETURN_THRUST);
  }

  public static void armReturningTrident(ServerPlayer player, ItemStack weapon) {
    if (ReforgeProfiles.has(weapon, RETURN_THRUST)) {
      StackCombatWindows.start(player, weapon, 100, null);
      CombatReforgeState.activate(player, RETURN_THRUST, 160);
    }
  }

  public static double hurtKnockback(LivingEntity target, DamageSource source, double strength) {
    if (!(source.getDirectEntity() instanceof Projectile projectile)
        || !(projectile.getOwner() instanceof ServerPlayer player)
        || !MeleeReforges.hostile(player, target)
        || !originalShooter(projectile, player)) {
      return strength;
    }
    return "crossbow_exceptional_charge_with_drive".equals(shot(projectile).getString("rank"))
        ? strength + 0.24
        : strength;
  }

  public static boolean ricochet(Projectile projectile, BlockHitResult hit) {
    if (!(projectile.getOwner() instanceof ServerPlayer player)
        || !originalShooter(projectile, player)) {
      return false;
    }
    var data = shot(projectile);
    if (!RICOCHET.equals(data.getString("rank")) || data.getBoolean("bounced")) {
      return false;
    }
    var level = projectile.level();
    if (!level
        .getBlockState(hit.getBlockPos())
        .isFaceSturdy(level, hit.getBlockPos(), hit.getDirection())) {
      return false;
    }
    var normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
    var movement = projectile.getDeltaMovement();
    double inward = movement.dot(normal);
    if (inward >= -0.15) {
      return false;
    }
    data.putBoolean("bounced", true);
    projectile.setPos(hit.getLocation().add(normal.scale(projectile.getBbWidth() * 0.5 + 0.05)));
    projectile.setDeltaMovement(movement.subtract(normal.scale(2.0 * inward)).scale(0.65));
    projectile.hasImpulse = true;
    return true;
  }

  private static boolean originalShooter(Projectile projectile, ServerPlayer player) {
    var data = shot(projectile);
    return data.hasUUID("shooter") && data.getUUID("shooter").equals(player.getUUID());
  }

  private static void sightingHit(Projectile projectile, LivingEntity target, ServerPlayer player) {
    var sighting = SIGHTING_SHOTS.remove(projectile);
    if (sighting == null
        || sighting.origin().distanceToSqr(target.position()) < 400
        || CombatReforgeState.active(player, SIGHTING)) {
      return;
    }
    ItemStack bow = sighting.weapon().get();
    if (bow != null && ReforgeProfiles.has(bow, SIGHTING)) {
      StackCombatWindows.start(player, bow, 120, null);
      CombatReforgeState.activate(player, SIGHTING, 200);
    }
  }

  private static void piercingHit(CompoundTag data, LivingEntity target, ServerPlayer player) {
    if (!data.hasUUID("first_pierced")) {
      data.putUUID("first_pierced", target.getUUID());
    } else if (!target.getUUID().equals(data.getUUID("first_pierced"))) {
      data.putBoolean("spent", true);
      if (target.isAlive()) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40), player);
      }
    }
  }

  private static void slowWithChance(
      ServerPlayer player,
      LivingEntity target,
      String effect,
      int duration,
      int cooldown,
      float chance) {
    if (!target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
        && !CombatReforgeState.active(player, effect)
        && (chance >= 1.0F || player.getRandom().nextFloat() < chance)
        && target.addEffect(
            new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration), player)) {
      CombatReforgeState.activate(player, effect, cooldown);
    }
  }

  private static void tide(ServerPlayer player, LivingEntity target) {
    if (!target.isUnderWater() || CombatReforgeState.active(player, TRIDENT_TIDE)) {
      return;
    }
    var nearby =
        player
            .serverLevel()
            .getEntitiesOfClass(
                LivingEntity.class,
                target.getBoundingBox().inflate(2.0),
                entity ->
                    entity != target
                        && entity instanceof Enemy
                        && entity.isAlive()
                        && entity.isUnderWater()
                        && !player.isAlliedTo(entity)
                        && entity.distanceToSqr(target) <= 4.0);
    if (!nearby.isEmpty()) {
      CombatReforgeState.activate(player, TRIDENT_TIDE, 160);
      for (var entity : nearby) {
        entity.knockback(0.65, target.getX() - entity.getX(), target.getZ() - entity.getZ());
      }
    }
  }

  private static CompoundTag shot(Projectile projectile) {
    return projectile.getPersistentData().getCompound(SHOT_DATA);
  }
}
