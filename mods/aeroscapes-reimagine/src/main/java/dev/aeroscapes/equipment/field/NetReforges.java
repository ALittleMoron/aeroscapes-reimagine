package dev.aeroscapes.equipment.field;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;

public final class NetReforges {
  private static final Map<ServerPlayer, Focus> FOCUS = new WeakHashMap<>();

  private NetReforges() {}

  public static InteractionResult captureNearAim(ServerPlayer player, InteractionHand hand) {
    ItemStack net = player.getItemInHand(hand);
    String profile = ReforgeProfiles.path(net);
    double correction =
        switch (profile) {
          case "hand_net_fine_aim" -> 0.08;
          case "hand_net_exceptional_aim" -> 0.15;
          case "hand_net_epic_aim" -> 0.18;
          case "hand_net_legendary_recover" -> focused(player, net) ? 0.26 : 0.15;
          default -> 0;
        };
    if (player.isSpectator() || correction == 0) {
      missed(player, net);
      return InteractionResult.PASS;
    }
    double reach = Math.min(4.0, player.entityInteractionRange());
    var eye = player.getEyePosition();
    var aim = player.getLookAngle().scale(reach);
    var hit =
        ProjectileUtil.getEntityHitResult(
            player.level(),
            player,
            eye,
            eye.add(aim),
            player.getBoundingBox().expandTowards(aim).inflate(correction),
            entity ->
                entity instanceof LivingEntity
                    && entity.isAlive()
                    && !entity.isSpectator()
                    && entity.isPickable()
                    && player.canInteractWithEntity(entity, 0.0)
                    && player.hasLineOfSight(entity),
            (float) correction);
    if (hit != null && hit.getEntity() instanceof LivingEntity target) {
      if (CommonHooks.onInteractEntity(player, target, hand) != null) return InteractionResult.PASS;
      ItemStack before = net.copy();
      InteractionResult result =
          (player.hasInfiniteMaterials() ? before : net).interactLivingEntity(player, target, hand);
      if (result.consumesAction()) {
        player
            .level()
            .gameEvent(GameEvent.ENTITY_INTERACT, target.position(), GameEvent.Context.of(player));
        if (net.isEmpty() && !player.hasInfiniteMaterials())
          EventHooks.onPlayerDestroyItem(player, before, hand);
        landed(player, net);
        return result;
      }
    }
    missed(player, net);
    return InteractionResult.PASS;
  }

  public static void missed(ServerPlayer player, ItemStack net) {
    String profile = ReforgeProfiles.path(net);
    if ("hand_net_poor_stiff".equals(profile) || "cast_net_poor_stiff".equals(profile)) {
      player.getCooldowns().addCooldown(net.getItem(), 8);
    }
    if (!profile.equals("hand_net_legendary_recover")
        && !profile.equals("cast_net_legendary_recover")) return;
    long now = player.level().getGameTime();
    var prior = FOCUS.get(player);
    if (prior == null || prior.readyAgain() <= now) {
      FOCUS.put(player, new Focus(new WeakReference<>(net), now + 40, now + 100));
    }
  }

  public static void landed(ServerPlayer player, ItemStack net) {
    var focus = FOCUS.get(player);
    if (focus != null && focus.net().get() == net) {
      FOCUS.put(player, new Focus(focus.net(), 0, focus.readyAgain()));
    }
  }

  public static double castAim(ServerPlayer player, ItemStack net, double nativeAim) {
    return switch (ReforgeProfiles.path(net)) {
      case "cast_net_fine_aim" -> nativeAim - 0.015;
      case "cast_net_exceptional_aim" -> nativeAim - 0.03;
      case "cast_net_epic_aim" -> nativeAim - 0.045;
      case "cast_net_legendary_recover" -> nativeAim - (focused(player, net) ? 0.065 : 0.03);
      default -> nativeAim;
    };
  }

  private static boolean focused(ServerPlayer player, ItemStack net) {
    var focus = FOCUS.get(player);
    return focus != null
        && focus.net().get() == net
        && focus.expires() > player.level().getGameTime();
  }

  private record Focus(WeakReference<ItemStack> net, long expires, long readyAgain) {}
}
