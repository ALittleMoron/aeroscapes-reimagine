package dev.aeroscapes.testship;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Keeps the player in world space until the client confirms Sable's complete ship. */
public final class TestShipBoarding {
  private final ServerSubLevelContainer container;
  private final Map<UUID, Pending> pending = new HashMap<>();

  TestShipBoarding(ServerSubLevelContainer container) {
    this.container = container;
  }

  void request(ServerPlayer player, ServerSubLevel ship) {
    pending.put(
        player.getUUID(),
        new Pending(player, ship.getUniqueId(), UUID.randomUUID(), player.position()));
  }

  void cancel(ServerPlayer player) {
    pending.remove(player.getUUID());
  }

  void tick() {
    for (Pending request : List.copyOf(pending.values())) {
      ServerPlayer player = request.player;
      if (container.getLevel().getPlayerByUUID(player.getUUID()) != player || player.isRemoved()) {
        cancel(player);
        continue;
      }
      if (++request.ticks > 200 || player.position().distanceToSqr(request.start) > 256) {
        cancel(player);
        player.sendSystemMessage(
            Component.translatable("message.aeroscapes_reimagine.test_ship.sync_failed"));
        continue;
      }
      if (request.ticks % 10 != 0) continue;
      try {
        ServerSubLevel ship = TestShips.ship(player);
        if (!ship.getUniqueId().equals(request.ship)) {
          cancel(player);
          continue;
        }
        List<ServerSubLevel> members = TestShips.family(ship);
        if (members.stream()
            .allMatch(part -> part.getTrackingPlayers().contains(player.getUUID()))) {
          List<UUID> ids = members.stream().map(ServerSubLevel::getUniqueId).toList();
          if (!ids.equals(request.members)) request.token = UUID.randomUUID();
          request.members = ids;
          player.connection.send(
              new ClientboundCustomPayloadPacket(new TestShipNetwork.Check(request.token, ids)));
        }
      } catch (CommandSyntaxException failure) {
        cancel(player);
        player.sendSystemMessage(
            Component.translatable("message.aeroscapes_reimagine.test_ship.sync_failed"));
      }
    }
  }

  public void confirm(ServerPlayer player, UUID token) {
    Pending request = pending.get(player.getUUID());
    if (request == null
        || request.player != player
        || !request.token.equals(token)
        || request.members == null) return;
    try {
      ServerSubLevel ship = TestShips.ship(player);
      List<ServerSubLevel> members = TestShips.family(ship);
      if (!ship.getUniqueId().equals(request.ship)
          || !members.stream().map(ServerSubLevel::getUniqueId).toList().equals(request.members)
          || members.stream()
              .anyMatch(part -> !part.getTrackingPlayers().contains(player.getUUID()))
          || player.position().distanceToSqr(request.start) > 256) return;
      cancel(player);
      TestShips.finishBoard(player);
      player.sendSystemMessage(
          Component.translatable("message.aeroscapes_reimagine.test_ship.board_done"));
    } catch (CommandSyntaxException failure) {
      cancel(player);
      player.sendSystemMessage(Component.literal(failure.getMessage()));
    } catch (RuntimeException failure) {
      cancel(player);
      LogUtils.getLogger().error("Cannot board test ship {}", request.ship, failure);
      player.sendSystemMessage(
          Component.translatable("message.aeroscapes_reimagine.test_ship.sync_failed"));
    }
  }

  private static final class Pending {
    final ServerPlayer player;
    final UUID ship;
    UUID token;
    final Vec3 start;
    int ticks;
    List<UUID> members;

    Pending(ServerPlayer player, UUID ship, UUID token, Vec3 start) {
      this.player = player;
      this.ship = ship;
      this.token = token;
      this.start = start;
    }
  }
}
