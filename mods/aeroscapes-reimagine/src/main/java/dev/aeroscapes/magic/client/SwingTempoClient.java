package dev.aeroscapes.magic.client;

import dev.aeroscapes.equipment.combat.SwingTempo;
import java.util.EnumMap;
import java.util.List;
import net.bettercombat.api.AttackHand;
import net.bettercombat.client.collision.TargetFinder;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public final class SwingTempoClient {
  private static final int MAX_WINDUP_TICKS = 24;
  private static final EnumMap<SwingTempo.Style, Available> WINDOWS =
      new EnumMap<>(SwingTempo.Style.class);
  private static @Nullable LocalPlayer subject;
  private static @Nullable ClientLevel level;
  private static @Nullable Committed committed;

  public record Available(SwingTempo.Window window, int end) {}

  private record Committed(SwingTempo.Window window, ItemStack weapon, int end) {}

  private SwingTempoClient() {}

  private static @Nullable LocalPlayer currentPlayer(Minecraft client) {
    if (subject != client.player || level != client.level) {
      WINDOWS.clear();
      committed = null;
      subject = client.player;
      level = client.level;
    }
    return subject;
  }

  public static void accept(SwingTempo.Window window) {
    LocalPlayer player = currentPlayer(Minecraft.getInstance());
    if (player == null) return;
    if (window.ticks() > 0) {
      var info = player.connection.getPlayerInfo(player.getUUID());
      int roundTripTicks = info == null ? 0 : Math.ceilDiv(Math.max(0, info.getLatency()), 50);
      WINDOWS.put(
          window.style(),
          new Available(window, player.tickCount + window.ticks() - roundTripTicks));
    } else {
      Available ready = WINDOWS.get(window.style());
      if (ready != null && ready.window().token() == window.token()) WINDOWS.remove(window.style());
    }
  }

  public static @Nullable Available available(Minecraft client, AttackHand hand) {
    LocalPlayer player = currentPlayer(client);
    if (player == null || !player.isAlive()) return null;
    if (committed != null && player.tickCount >= committed.end()) finish(client);
    if (committed != null) return null;
    ItemStack weapon = hand.itemStack();
    var parry = WINDOWS.get(SwingTempo.Style.PARRY);
    if (parry != null
        && player.tickCount < parry.end()
        && parry.window().style().accepts(weapon)
        && nativeParryTarget(client, player, parry.window(), hand)) return parry;
    var heavy = WINDOWS.get(SwingTempo.Style.HEAVY);
    return heavy != null && player.tickCount < heavy.end() && heavy.window().style().accepts(weapon)
        ? heavy
        : null;
  }

  public static void commit(Minecraft client, Available available, ItemStack weapon) {
    LocalPlayer player = currentPlayer(client);
    if (player == null || WINDOWS.get(available.window().style()) != available) return;
    var window = available.window();
    committed = new Committed(window, weapon.copy(), player.tickCount + MAX_WINDUP_TICKS);
    WINDOWS.remove(window.style());
    PacketDistributor.sendToServer(new SwingTempo.Attempt(window.token(), true));
  }

  public static boolean inSwing(Minecraft client) {
    return currentPlayer(client) != null && committed != null;
  }

  public static List<Entity> keepTargets(Minecraft client, ItemStack weapon, List<Entity> targets) {
    if (!inSwing(client)) return targets;
    var swing = committed;
    if (!ItemStack.matches(swing.weapon(), weapon)) return List.of();
    var window = swing.window();
    if (window.style() != SwingTempo.Style.PARRY) return targets;
    Entity target = parryTarget(window.target(), cursorTarget(client), targets);
    return target == null ? List.of() : List.of(target);
  }

  public static void finish(Minecraft client) {
    if (!inSwing(client)) return;
    int token = committed.window().token();
    committed = null;
    PacketDistributor.sendToServer(new SwingTempo.Attempt(token, false));
  }

  private static boolean nativeParryTarget(
      Minecraft client, LocalPlayer player, SwingTempo.Window window, AttackHand hand) {
    Entity cursor = cursorTarget(client);
    if (cursor != null && cursor.getId() != window.target()) return false;
    double reach =
        PlayerAttackHelper.getRangeForItem(player, hand.itemStack())
            * hand.attack().rangeMultiplier();
    List<Entity> targets = TargetFinder.findAttackTargets(player, cursor, hand.attack(), reach);
    return parryTarget(window.target(), cursor, targets) != null;
  }

  private static @Nullable Entity cursorTarget(Minecraft client) {
    return client.hitResult instanceof EntityHitResult result ? result.getEntity() : null;
  }

  private static @Nullable Entity parryTarget(
      int attackerId, @Nullable Entity cursor, List<Entity> targets) {
    // Better Combat's cursor uses main-hand reach, while its hitbox uses the attack hand.
    if (cursor == null) {
      if (targets.size() != 1) return null;
      Entity soleTarget = targets.getFirst();
      return soleTarget.isAlive() && soleTarget.getId() == attackerId ? soleTarget : null;
    }
    return cursor.isAlive() && cursor.getId() == attackerId && targets.contains(cursor)
        ? cursor
        : null;
  }
}
