package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class InstalledWeaponReforges {
  private static final String COUNTERBRACE = "cardboard_legendary_counterbrace";
  private static final String COUNTERBRACE_COOLDOWN = "cardboard_counterbrace";
  private static final String CANNON_BRACE = "potato_cannon_legendary_brace";
  private static final String BATTLE_RETURN = "pickarang_legendary_rebound";
  private static final String BATTLE_HIT = "aeroscapes_reimagine:pickarang_hit";
  private static final String DELIBERATE_BUBBLE = "crab_claw_legendary_deliberate";
  private static final int BRACE_TICKS = 20;
  private static final double STANCE_DISTANCE_SQR = 0.0025;
  private static final Map<ItemStack, CannonStance> CANNON_STANCES = new WeakHashMap<>();

  private record CannonStance(
      UUID owner, ResourceKey<Level> dimension, Vec3 anchor, long lastTick, int ticks) {
    boolean matches(ServerPlayer player, long now) {
      return owner.equals(player.getUUID())
          && dimension.equals(player.level().dimension())
          && now >= lastTick
          && now - lastTick <= 1
          && anchor.distanceToSqr(player.position()) <= STANCE_DISTANCE_SQR;
    }
  }

  private InstalledWeaponReforges() {}

  public static double cardboardPush(Player player, double strength) {
    return strength
        * switch (ReforgeProfiles.path(player.getMainHandItem())) {
          case "cardboard_poor_soft" -> 0.88;
          case "cardboard_fine_firm" -> 1.08;
          case "cardboard_exceptional_drive" -> player.onGround() ? 1.16 : 1.0;
          case "cardboard_epic_drive", COUNTERBRACE -> player.onGround() ? 1.20 : 1.12;
          default -> 1.0;
        };
  }

  public static void cardboardHit(Player attacker, LivingEntity target) {
    if (!(attacker instanceof ServerPlayer player)
        || !MeleeReforges.hostile(player, target)
        || CombatReforgeState.active(player, COUNTERBRACE_COOLDOWN)) {
      return;
    }
    ItemStack weapon = player.getMainHandItem();
    if (!ReforgeProfiles.has(weapon, COUNTERBRACE)) {
      return;
    }
    var previous = StackCombatWindows.get(player, weapon);
    if (previous == null) {
      StackCombatWindows.start(player, weapon, 60, target.getUUID());
    } else if (!target.getUUID().equals(previous.firstTarget())) {
      StackCombatWindows.start(player, weapon, 40, null);
      CombatReforgeState.activate(player, COUNTERBRACE_COOLDOWN, 160);
    }
  }

  public static boolean cardboardGuarding(ServerPlayer player) {
    ItemStack weapon = player.getMainHandItem();
    if (!ReforgeProfiles.has(weapon, COUNTERBRACE)) {
      return false;
    }
    var window = StackCombatWindows.get(player, weapon);
    return window != null && window.firstTarget() == null;
  }

  @SubscribeEvent
  public static void observeCannonStance(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      observeCannonStance(player, player.getMainHandItem());
      observeCannonStance(player, player.getOffhandItem());
    }
  }

  private static void observeCannonStance(ServerPlayer player, ItemStack stack) {
    if (!ReforgeProfiles.has(stack, CANNON_BRACE)) {
      return;
    }
    if (!bracing(player)) {
      CANNON_STANCES.remove(stack);
      return;
    }
    long now = player.level().getGameTime();
    var previous = CANNON_STANCES.get(stack);
    boolean continuous = previous != null && previous.matches(player, now);
    int ticks = continuous ? Math.min(BRACE_TICKS, previous.ticks() + 1) : 1;
    var anchor = continuous ? previous.anchor() : player.position();
    CANNON_STANCES.put(
        stack, new CannonStance(player.getUUID(), player.level().dimension(), anchor, now, ticks));
  }

  private static boolean bracing(ServerPlayer player) {
    return player.onGround() && player.isShiftKeyDown() && !player.isPassenger();
  }

  public static double cannonSpread(ItemStack stack, Player player, double original) {
    return original
        * switch (ReforgeProfiles.path(stack)) {
          case "potato_cannon_fine_aim" -> 0.85;
          case "potato_cannon_epic_aim" -> 0.75;
          case CANNON_BRACE -> {
            var stance = CANNON_STANCES.remove(stack);
            yield player instanceof ServerPlayer serverPlayer
                    && stance != null
                    && stance.ticks() >= BRACE_TICKS
                    && bracing(serverPlayer)
                    && stance.matches(serverPlayer, player.level().getGameTime())
                ? 0.50
                : 0.75;
          }
          default -> 1.0;
        };
  }

  public static int cannonReload(ItemStack stack, int original) {
    if (original <= 0) {
      return original;
    }
    return switch (ReforgeProfiles.path(stack)) {
      case "potato_cannon_poor_recovery" -> RangedReforges.scaleTicks(original, 1.15F);
      case "potato_cannon_exceptional_recovery", "potato_cannon_epic_aim", CANNON_BRACE ->
          RangedReforges.scaleTicks(original, 0.85F);
      default -> original;
    };
  }

  public static float pickarangLaunch(ItemStack stack, float speed) {
    return speed
        * switch (ReforgeProfiles.path(stack)) {
          case "pickarang_poor_drag" -> 0.92F;
          case "pickarang_fine_throw" -> 1.06F;
          case "pickarang_epic_trajectory", BATTLE_RETURN -> 1.10F;
          default -> 1.0F;
        };
  }

  public static void pickarangHit(Projectile projectile, ItemStack stack, Entity target) {
    if (ReforgeProfiles.has(stack, BATTLE_RETURN)
        && projectile.getOwner() instanceof ServerPlayer player
        && target instanceof LivingEntity living
        && MeleeReforges.hostile(player, living)) {
      projectile.getPersistentData().putBoolean(BATTLE_HIT, true);
    }
  }

  public static Vec3 pickarangReturn(Projectile projectile, ItemStack stack, Vec3 original) {
    double multiplier =
        switch (ReforgeProfiles.path(stack)) {
          case "pickarang_exceptional_return", "pickarang_epic_trajectory" -> 1.12;
          case BATTLE_RETURN -> projectile.getPersistentData().getBoolean(BATTLE_HIT) ? 1.30 : 1.12;
          default -> 1.0;
        };
    if (multiplier == 1.0 || !(projectile.getOwner() instanceof Player player)) {
      return original;
    }
    double distance = player.position().add(0.0, 1.0, 0.0).distanceTo(projectile.position());
    double speed = original.length();
    // Don't skip past Quark's native catch point, even with high Efficiency.
    return speed > 0.0
        ? original.scale(Math.max(1.0, Math.min(multiplier, distance / speed)))
        : original;
  }

  public static int clawWarmup(ItemStack stack, int original) {
    return switch (ReforgeProfiles.path(stack)) {
      case "crab_claw_poor_draw" -> original + 4;
      case "crab_claw_fine_draw" -> original - 2;
      case "crab_claw_epic_pressure", DELIBERATE_BUBBLE -> original - 4;
      default -> original;
    };
  }

  public static float clawSpeed(LivingEntity shooter, float original) {
    return deliberateBubble(shooter) ? original * 1.15F : original;
  }

  public static float clawSpread(LivingEntity shooter, float original) {
    return original
        * switch (ReforgeProfiles.path(shooter.getUseItem())) {
          case "crab_claw_exceptional_aim" -> 0.80F;
          case "crab_claw_epic_pressure" -> 0.75F;
          case DELIBERATE_BUBBLE -> deliberateBubble(shooter) ? 0.50F : 0.75F;
          default -> 1.0F;
        };
  }

  private static boolean deliberateBubble(LivingEntity shooter) {
    return ReforgeProfiles.has(shooter.getUseItem(), DELIBERATE_BUBBLE)
        && shooter.getTicksUsingItem() >= 30;
  }
}
