package dev.aeroscapes.equipment.combat;

import com.herobrot.tieredneo.api.TieredNeoAPI;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

  private RangedReforges() {}

  public interface FixedSpeedArrow {
    void aeroscapes$setSpeedMultiplier(float multiplier);
  }

  public static float bowDrawTime(ItemStack stack) {
    return switch (ReforgeProfiles.path(stack)) {
      case "bow_exceptional_draw" -> 0.85F;
      case "bow_epic", "bow_legendary_mark", "bow_legendary_bind" -> 0.90F;
      default -> 1.0F;
    };
  }

  @SubscribeEvent
  public static void bowReleased(ArrowLooseEvent event) {
    if (event.getBow().getItem() instanceof BowItem && event.getCharge() > 0) {
      float draw = bowDrawTime(event.getBow());
      if (draw < 1.0F) {
        event.setCharge((int) (event.getCharge() / draw));
      }
    }
  }

  public static int slingshotCharge(ItemStack stack, int duration) {
    return switch (ReforgeProfiles.path(stack)) {
      case "slingshot_poor" -> scaleTicks(duration, 1.10F);
      case "slingshot_fine" -> scaleTicks(duration, 0.95F);
      case "slingshot_exceptional_draw" -> scaleTicks(duration, 0.88F);
      case "slingshot_epic", "slingshot_legendary_stagger" -> scaleTicks(duration, 0.92F);
      default -> duration;
    };
  }

  static int scaleTicks(int duration, float scale) {
    return Math.max(1, Math.round(duration * scale));
  }

  public static void launch(Projectile projectile, ItemStack weapon, ItemStack ammo, boolean full) {
    if (!(projectile.getOwner() instanceof ServerPlayer) || !ModList.get().isLoaded("tieredneo")) {
      return;
    }
    String rank = ReforgeProfiles.path(weapon);
    if (projectile instanceof AbstractArrow
        && (weapon.getItem() instanceof BowItem || weapon.is(Items.CROSSBOW))) {
      double velocity = "bow_exceptional_velocity".equals(rank) ? 1.15 : 1.0;
      remember(projectile, rank, velocity, tierDamage(weapon, 1.0F), full);
    } else if (rank.startsWith("slingshot_")
        && (ammo.is(Items.STONE) || ammo.is(Items.COBBLESTONE) || ammo.is(Items.FLINT))) {
      double velocity =
          switch (rank) {
            case "slingshot_exceptional_velocity" -> 1.15;
            case "slingshot_epic", "slingshot_legendary_stagger" -> 1.10;
            default -> 1.0;
          };
      remember(projectile, rank, velocity, 1.0F, false);
    }
  }

  public static void launchTrident(ThrownTrident projectile) {
    String rank = ReforgeProfiles.path(projectile.getWeaponItem());
    double velocity =
        switch (rank) {
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
    data.putBoolean("spent", true);
    var target = event.getEntity();
    switch (data.getString("rank")) {
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

  public static void slingshotHit(Projectile projectile, LivingEntity target) {
    if (!(projectile.getOwner() instanceof ServerPlayer player)
        || !(target instanceof Enemy)
        || !target.isAlive()
        || player.isAlliedTo(target)) {
      return;
    }
    var data = shot(projectile);
    if (!"slingshot_legendary_stagger".equals(data.getString("rank")) || data.getBoolean("spent")) {
      return;
    }
    data.putBoolean("spent", true);
    slowWithChance(player, target, SLINGSHOT_STAGGER, 25, 160, 1.0F);
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
