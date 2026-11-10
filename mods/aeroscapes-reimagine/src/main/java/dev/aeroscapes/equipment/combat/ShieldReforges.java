package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ShieldReforges {
  private static final Map<ServerPlayer, Block> BLOCKS = new WeakHashMap<>();
  private static final String INTERCEPT = "shield_legendary_intercept";

  private record Block(WeakReference<DamageSource> source, boolean anchored) {}

  private ShieldReforges() {}

  public static int wear(Player player, int damage) {
    ItemStack shield = player.getUseItem();
    float multiplier =
        ReforgeProfiles.has(shield, "shield_epic_with_anchored")
            ? 0.83F
            : switch (ReforgeProfiles.basePath(shield)) {
              case "shield_poor" -> 1.10F;
              case "shield_fine" -> 0.94F;
              case "shield_exceptional" -> 0.85F;
              case "shield_epic", "shield_legendary_riposte", INTERCEPT -> 0.88F;
              default -> 1.0F;
            };
    if (multiplier == 1.0F) {
      return damage;
    }
    float scaled = damage * multiplier;
    int whole = Mth.floor(scaled);
    return whole + (player.getRandom().nextFloat() < scaled - whole ? 1 : 0);
  }

  static boolean nimble(Player player) {
    if (!player.isBlocking()) {
      return false;
    }
    var rank = ReforgeProfiles.basePath(player.getUseItem());
    return "shield_epic".equals(rank)
        || "shield_legendary_riposte".equals(rank)
        || INTERCEPT.equals(rank);
  }

  public static int raiseTicks(ItemStack shield, int normal) {
    return switch (ReforgeProfiles.path(shield)) {
      case "shield_poor_with_drag" -> normal + 2;
      case "shield_fine_with_tempo" -> Math.max(1, normal - 1);
      default -> normal;
    };
  }

  public static int disabledTicks(ItemStack shield, int normal) {
    return switch (ReforgeProfiles.path(shield)) {
      case "shield_ordinary_with_recovery" -> normal - 10;
      case "shield_exceptional_with_recovery" -> normal - 20;
      default -> normal;
    };
  }

  public static double hurtKnockback(LivingEntity entity, DamageSource source, double original) {
    if (!(entity instanceof ServerPlayer player) || original <= 0.0) {
      return original;
    }
    var block = BLOCKS.get(player);
    boolean sameBlock = block != null && block.source().get() == source;
    double knockback =
        MeleeReforges.guarding(player) || InstalledWeaponReforges.cardboardGuarding(player)
            ? original * 0.70
            : original;
    if (sameBlock) {
      BLOCKS.remove(player);
      return block.anchored() ? knockback * 0.65 : knockback;
    }
    if (intercept(player, player.getMainHandItem()) || intercept(player, player.getOffhandItem())) {
      return knockback * 0.50;
    }
    return knockback;
  }

  private static boolean intercept(ServerPlayer player, ItemStack shield) {
    return ReforgeProfiles.has(shield, INTERCEPT) && StackCombatWindows.consume(player, shield);
  }

  public static void observeBlock(LivingShieldBlockEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) {
      return;
    }
    if (event.isCanceled()
        || !event.getOriginalBlock()
        || !event.getBlocked()
        || event.getBlockedDamage() <= 0.0F) {
      BLOCKS.remove(player);
      return;
    }
    ItemStack shield = player.getUseItem();
    String rank = ReforgeProfiles.path(shield);
    if ("shield_epic_with_anchored".equals(rank) || INTERCEPT.equals(rank)) {
      BLOCKS.put(
          player,
          new Block(
              new WeakReference<>(event.getDamageSource()),
              "shield_epic_with_anchored".equals(rank)));
    }
    if (INTERCEPT.equals(rank)
        && player.getTicksUsingItem() <= 15
        && !CombatReforgeState.active(player, INTERCEPT)) {
      StackCombatWindows.start(player, shield, 40, null);
      CombatReforgeState.activate(player, INTERCEPT, 120);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void block(LivingShieldBlockEvent event) {
    if (event.getEntity() instanceof ServerPlayer player
        && event.getOriginalBlock()
        && event.getBlocked()
        && event.getBlockedDamage() > 0.0F
        && "shield_legendary_riposte".equals(ReforgeProfiles.basePath(player.getUseItem()))) {
      MeleeReforges.armRiposte(player);
    }
  }
}
