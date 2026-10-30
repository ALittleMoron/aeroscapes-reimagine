package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import dev.aeroscapes.testship.TestShipNetwork;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFinalizeSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;

/** A registered server-side player with bounded packet capture; no client or socket. */
final class TestShipPlayerSession implements AutoCloseable {
  final ServerPlayer player;
  private final RecordingConnection connection;
  private final RecordingListener listener;
  private final EmbeddedChannel channel;
  private boolean closed;

  private TestShipPlayerSession(
      ServerPlayer player,
      RecordingConnection connection,
      RecordingListener listener,
      EmbeddedChannel channel) {
    this.player = player;
    this.connection = connection;
    this.listener = listener;
    this.channel = channel;
  }

  static TestShipPlayerSession create(
      ServerLevel level,
      UUID uuid,
      String name,
      Vec3 position,
      @Nullable net.minecraft.nbt.CompoundTag saved) {
    MinecraftServer server = level.getServer();
    require(server.isSameThread(), "Create fixture on server thread");
    require(server.getPlayerList().getPlayerCount() == 0, "Disposable server only");
    require(level.getPlayerByUUID(uuid) == null, "Fixture UUID already registered in level");
    require(server.getPlayerList().getPlayer(uuid) == null, "Fixture UUID already logged in");
    ClientInformation information = ClientInformation.createDefault();
    ServerPlayer player = new ServerPlayer(server, level, new GameProfile(uuid, name), information);
    RecordingConnection connection = new RecordingConnection();
    EmbeddedChannel channel = new EmbeddedChannel(connection);
    ChannelAttributes.setConnectionType(connection, ConnectionType.NEOFORGE);
    ChannelAttributes.setPayloadSetup(connection, NetworkPayloadSetup.empty());
    try {
      CommonListenerCookie cookie =
          new CommonListenerCookie(
              player.getGameProfile(), 0, information, false, ConnectionType.NEOFORGE);
      RecordingListener listener = new RecordingListener(server, connection, player, cookie);
      if (saved != null) player.load(saved);
      player.absMoveTo(position.x, position.y, position.z, 0, 0);
      level.addNewPlayer(player);
      require(player.connection == listener, "Listener changed during level registration");
      require(player.isAddedToLevel() && !player.isRemoved(), "Player was not registered");
      require(level.getEntity(player.getId()) == player, "Entity registry misses fixture");
      require(level.getPlayerByUUID(uuid) == player, "UUID player lookup misses fixture");
      require(level.players().contains(player), "Player list misses fixture");
      return new TestShipPlayerSession(player, connection, listener, channel);
    } catch (RuntimeException | Error failure) {
      try {
        cleanup(player);
      } finally {
        channel.finishAndReleaseAll();
      }
      throw failure;
    }
  }

  // Explicit native ACK is useful when checking the teleport protocol, but server position has
  // already changed inside listener.teleport before the recorded packet is sent.
  void acknowledgeTeleport() {
    require(player.serverLevel().getServer().isSameThread(), "Acknowledge on server thread");
    ClientboundPlayerPositionPacket teleport = connection.teleport;
    if (teleport == null) throw new IllegalStateException("No recorded teleport to acknowledge");
    connection.teleport = null;
    listener.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleport.getId()));
  }

  @Nullable
  TestShipNetwork.Check takeBoardingCheck() {
    TestShipNetwork.Check check = connection.boardingCheck;
    connection.boardingCheck = null;
    return check;
  }

  boolean receivedShipParts(TestShipNetwork.Check check) {
    return connection.completedParts.containsAll(check.members());
  }

  void tick() {
    require(
        !closed && player.serverLevel().getServer().isSameThread(),
        "Tick live fixture on server thread");
    listener.tick();
    if (connection.teleport != null) acknowledgeTeleport();
    if (connection.keepAlive != null) {
      long challenge = connection.keepAlive;
      connection.keepAlive = null;
      listener.handleKeepAlive(new ServerboundKeepAlivePacket(challenge));
    }
    listener.handleMovePlayer(
        new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(), player.onGround()));
  }

  @Override
  public void close() {
    if (closed) return;
    require(player.serverLevel().getServer().isSameThread(), "Close fixture on server thread");
    closed = true;
    ServerLevel level = player.serverLevel();
    try {
      cleanup(player);
    } finally {
      channel.finishAndReleaseAll();
    }
    require(level.getEntity(player.getId()) != player, "Fixture remains in entity registry");
    require(level.getPlayerByUUID(player.getUUID()) != player, "Fixture remains in UUID lookup");
    require(!level.players().contains(player), "Fixture remains in level player list");
  }

  private static void cleanup(ServerPlayer player) {
    try {
      player.stopRiding();
      if (player.isAddedToLevel() && !player.isRemoved()) {
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
      }
    } finally {
      try {
        player.getTextFilter().leave();
      } finally {
        player.getAdvancements().stopListening();
      }
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class RecordingConnection extends Connection {
    @Nullable private ClientboundPlayerPositionPacket teleport;
    @Nullable private Long keepAlive;
    @Nullable private TestShipNetwork.Check boardingCheck;
    private final Set<UUID> completedParts = new HashSet<>();

    private RecordingConnection() {
      super(PacketFlow.SERVERBOUND);
    }

    @Override
    public boolean isMemoryConnection() {
      return true;
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
      if (packet instanceof ClientboundPlayerPositionPacket position) teleport = position;
      if (packet instanceof ClientboundKeepAlivePacket challenge) keepAlive = challenge.getId();
      if (packet instanceof ClientboundCustomPayloadPacket payload
          && payload.payload() instanceof TestShipNetwork.Check check) boardingCheck = check;
      if (packet instanceof ClientboundBundlePacket bundle) {
        UUID started = null;
        for (var child : bundle.subPackets()) {
          if (child instanceof ClientboundCustomPayloadPacket payload) {
            if (payload.payload() instanceof ClientboundStartTrackingSubLevelPacket start)
              started = start.subLevelID();
            if (payload.payload() instanceof ClientboundFinalizeSubLevelPacket && started != null)
              completedParts.add(started);
          }
        }
      }
      if (listener != null) listener.onSuccess();
    }
  }

  private static final class RecordingListener extends ServerGamePacketListenerImpl {
    private final RecordingConnection recording;

    private RecordingListener(
        MinecraftServer server,
        RecordingConnection connection,
        ServerPlayer player,
        CommonListenerCookie cookie) {
      super(server, connection, player, cookie);
      recording = connection;
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
      recording.send(packet, listener, true);
    }
  }
}
