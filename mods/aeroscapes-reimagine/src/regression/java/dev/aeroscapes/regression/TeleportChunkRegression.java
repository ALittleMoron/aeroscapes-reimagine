package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;

/** Real player tickets and terrain delivery to an ideal packet sink; no client or meshing. */
public final class TeleportChunkRegression {
  private static final ChunkPos ORIGIN = new ChunkPos(512, 512);
  private static final ChunkPos DESTINATION = new ChunkPos(768, 768);
  private static final int NEAR_RADIUS = 2;
  private static final int NEAR_COUNT = 25;
  private static final float ACK_CHUNKS_PER_TICK = 9.0F;
  private final ServerLevel level;
  private final long deadline = System.nanoTime() + 180_000_000_000L;
  private final RecordingConnection connection = new RecordingConnection();
  private final EmbeddedChannel channel = new EmbeddedChannel(connection);
  private final ServerPlayer player;
  private final RecordingListener listener;
  private Leg leg;
  private int phase;
  private boolean finished;

  private TeleportChunkRegression(ServerLevel level) {
    this.level = level;
    MinecraftServer server = level.getServer();
    ClientInformation defaults = ClientInformation.createDefault();
    ClientInformation information =
        new ClientInformation(
            defaults.language(),
            server.getPlayerList().getViewDistance(),
            defaults.chatVisibility(),
            defaults.chatColors(),
            defaults.modelCustomisation(),
            defaults.mainHand(),
            defaults.textFilteringEnabled(),
            defaults.allowsListing());
    player =
        new ServerPlayer(
            server,
            level,
            new GameProfile(
                UUID.fromString("b8fc31c9-93a1-4392-96ba-c584df1968b7"), "TerrainProbe"),
            information);
    ChannelAttributes.setConnectionType(connection, ConnectionType.NEOFORGE);
    ChannelAttributes.setPayloadSetup(connection, NetworkPayloadSetup.empty());
    listener =
        new RecordingListener(
            server,
            connection,
            player,
            new CommonListenerCookie(
                player.getGameProfile(), 0, information, false, ConnectionType.NEOFORGE));
    leg = new Leg("origin-new", ORIGIN, 0);
    connection.leg = leg;
    connection.player = player;
  }

  public static void start(ServerLevel level) {
    MinecraftServer server = level.getServer();
    require(server.isSameThread() && server.isDedicatedServer(), "Dedicated server thread only");
    require(server.getPlayerList().getPlayerCount() == 0, "Disposable server without players only");
    require(level.dimension().equals(Level.OVERWORLD), "Overworld fixture only");
    require(server.getPlayerList().getViewDistance() <= 10, "Fixture view distance must be <=10");
    require(server.getPlayerList().getSimulationDistance() <= 6, "Simulation distance must be <=6");
    requireFreshRegions(server.getWorldPath(LevelResource.ROOT).resolve("region"), ORIGIN);
    requireFreshRegions(server.getWorldPath(LevelResource.ROOT).resolve("region"), DESTINATION);
    TeleportChunkRegression fixture = new TeleportChunkRegression(level);
    try {
      require(fixture.countSendable(ORIGIN) == 0, "Origin was already sendable");
      fixture.player.absMoveTo(
          ORIGIN.getMiddleBlockX(),
          level.getMaxBuildHeight() + 32.0,
          ORIGIN.getMiddleBlockZ(),
          0,
          0);
      fixture.player.setNoGravity(true);
      level.addNewPlayer(fixture.player);
      fixture.player.setGameMode(GameType.CREATIVE);
      fixture.player.getAbilities().flying = true;
      require(fixture.player.isAddedToLevel(), "Fixture player was not registered");
      require(level.getPlayerByUUID(fixture.player.getUUID()) == fixture.player, "Missing player");
      LogUtils.getLogger()
          .info(
              "Teleport terrain fixture started: view={}, simulation={}, ACK={} chunks/tick; no client",
              server.getPlayerList().getViewDistance(),
              server.getPlayerList().getSimulationDistance(),
              ACK_CHUNKS_PER_TICK);
      NeoForge.EVENT_BUS.addListener(fixture::tick);
      NeoForge.EVENT_BUS.addListener(fixture::stopping);
    } catch (RuntimeException | Error failure) {
      fixture.close();
      throw failure;
    }
  }

  private static void requireFreshRegions(Path directory, ChunkPos center) {
    for (int x = (center.x >> 5) - 1; x <= (center.x >> 5) + 1; x++)
      for (int z = (center.z >> 5) - 1; z <= (center.z >> 5) + 1; z++)
        require(
            !Files.exists(directory.resolve("r." + x + "." + z + ".mca")), "Fresh world required");
  }

  private void tick(ServerTickEvent.Post event) {
    if (finished || event.getServer() != level.getServer()) return;
    try {
      require(System.nanoTime() < deadline, "Teleport terrain timeout: " + leg.description());
      require(
          level.getServer().getPlayerList().getPlayerCount() == 0, "Real player joined fixture");
      acknowledge();
      listener.tick();
      listener.handleMovePlayer(
          new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(), false));
      require(
          connection.teleport == null, "Unexpected corrective teleport during fixture movement");
      require(player.chunkPosition().equals(leg.center), "Player left the measured destination");
      require(
          player.getLastSectionPos().chunk().equals(leg.center),
          "Native movement did not update player tickets to the measured destination");
      if (leg.sendableNanos < 0 && countSendable(leg.center) == NEAR_COUNT)
        leg.sendableNanos = System.nanoTime() - leg.started;
      // This player is level registered but absent from login/network rosters. Drive the native
      // sender exactly once per tick; ChunkMap and C2ME alone populate its real pending set.
      listener.chunkSender.sendNextChunks(player);
      require(!connection.batchOpen, "Unfinished chunk batch");
      if (leg.nearbyReceived.size() != NEAR_COUNT || leg.sendableNanos < 0) return;
      report();
      if (phase == 0) {
        phase = 1;
        teleport("destination-new", DESTINATION);
      } else if (phase == 1) {
        phase = 2;
        teleport("origin-generated-return", ORIGIN);
      } else {
        close();
        LogUtils.getLogger()
            .info(
                "Teleport terrain regression passed: new destination and generated return delivered; server only");
      }
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  private void acknowledge() {
    if (connection.teleport != null) {
      int id = connection.teleport.getId();
      connection.teleport = null;
      listener.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id));
    }
    if (connection.keepAlive != null) {
      long challenge = connection.keepAlive;
      connection.keepAlive = null;
      listener.handleKeepAlive(new ServerboundKeepAlivePacket(challenge));
    }
    int batches = connection.awaitingAck;
    connection.awaitingAck = 0;
    for (int index = 0; index < batches; index++)
      listener.handleChunkBatchReceived(
          new ServerboundChunkBatchReceivedPacket(ACK_CHUNKS_PER_TICK));
  }

  private void teleport(String name, ChunkPos center) {
    Leg next = new Leg(name, center, countSendable(center));
    if (phase == 1) require(next.residentBefore == 0, "Cold destination was already sendable");
    connection.leg = next;
    leg = next;
    require(
        player.teleportTo(
            level,
            center.getMiddleBlockX(),
            level.getMaxBuildHeight() + 32.0,
            center.getMiddleBlockZ(),
            Set.of(),
            0,
            0),
        "Teleport failed");
    // The next normal fixture tick acknowledges this packet, then listener.tick resets the native
    // movement baselines before the first synthetic movement packet at the new position.
    require(player.chunkPosition().equals(center), "Teleport destination mismatch");
  }

  private int countSendable(ChunkPos center) {
    int count = 0;
    for (int x = center.x - NEAR_RADIUS; x <= center.x + NEAR_RADIUS; x++)
      for (int z = center.z - NEAR_RADIUS; z <= center.z + NEAR_RADIUS; z++)
        if (level.getChunkSource().chunkMap.getChunkToSend(ChunkPos.asLong(x, z)) != null) count++;
    return count;
  }

  private void report() {
    int pendingSendable = 0;
    int unsendable = 0;
    int radius = player.requestedViewDistance();
    for (int x = leg.center.x - radius; x <= leg.center.x + radius; x++)
      for (int z = leg.center.z - radius; z <= leg.center.z + radius; z++) {
        if (!player.getChunkTrackingView().contains(x, z)) continue;
        long position = ChunkPos.asLong(x, z);
        if (listener.chunkSender.isPending(position)) pendingSendable++;
        if (level.getChunkSource().chunkMap.getChunkToSend(position) == null) unsendable++;
      }
    LogUtils.getLogger()
        .info(
            "Teleport terrain leg: {}, firstPacketMs={}, near25SendableMs={}, near25DeliveredMs={}, residentNearBefore={}, totalTerrainPackets={}, uniqueTerrainChunks={}, resendPackets={}, nearResendPackets={}, firstResend={}, pendingSender={}, unsendableView={}",
            leg.name,
            leg.firstPacketNanos / 1_000_000.0,
            leg.sendableNanos / 1_000_000.0,
            leg.nearbyNanos / 1_000_000.0,
            leg.residentBefore,
            leg.packets,
            leg.received.size(),
            leg.resends,
            leg.nearResends,
            leg.firstResend,
            pendingSendable,
            unsendable);
  }

  private void stopping(ServerStoppingEvent event) {
    if (event.getServer() == level.getServer()) close();
  }

  private void close() {
    if (finished) return;
    finished = true;
    try {
      if (player.isAddedToLevel() && !player.isRemoved())
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    } finally {
      try {
        level
            .getChunkSource()
            .removeRegionTicket(TicketType.POST_TELEPORT, ORIGIN, 1, player.getId());
        level
            .getChunkSource()
            .removeRegionTicket(TicketType.POST_TELEPORT, DESTINATION, 1, player.getId());
        player.getTextFilter().leave();
        player.getAdvancements().stopListening();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
    require(level.getPlayerByUUID(player.getUUID()) == null, "Fixture player remains registered");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class Leg {
    private final String name;
    private final ChunkPos center;
    private final int residentBefore;
    private final long started = System.nanoTime();
    private final Set<Long> received = new HashSet<>();
    private final Set<Long> nearbyReceived = new HashSet<>();
    private int packets;
    private int resends;
    private int nearResends;
    private String firstResend = "none";
    private long firstPacketNanos = -1;
    private long sendableNanos = -1;
    private long nearbyNanos = -1;

    private Leg(String name, ChunkPos center, int residentBefore) {
      this.name = name;
      this.center = center;
      this.residentBefore = residentBefore;
    }

    private String description() {
      return name + ", packets=" + packets + ", near=" + nearbyReceived.size();
    }
  }

  private static final class RecordingConnection extends Connection {
    @Nullable private ClientboundPlayerPositionPacket teleport;
    @Nullable private Long keepAlive;
    @Nullable private Leg leg;
    @Nullable private ServerPlayer player;
    private boolean batchOpen;
    private int batchChunks;
    private int awaitingAck;

    private RecordingConnection() {
      super(PacketFlow.SERVERBOUND);
    }

    @Override
    public boolean isMemoryConnection() {
      return true;
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
      record(packet);
      if (listener != null) listener.onSuccess();
    }

    private void record(Packet<?> packet) {
      if (packet instanceof ClientboundPlayerPositionPacket position) teleport = position;
      if (packet instanceof ClientboundKeepAlivePacket challenge) keepAlive = challenge.getId();
      if (packet instanceof ClientboundBundlePacket bundle) {
        bundle.subPackets().forEach(this::record);
      } else if (packet instanceof ClientboundChunkBatchStartPacket) {
        require(!batchOpen, "Nested chunk batch");
        batchOpen = true;
        batchChunks = 0;
      } else if (packet instanceof ClientboundChunkBatchFinishedPacket complete) {
        require(batchOpen && complete.batchSize() == batchChunks, "Chunk batch size mismatch");
        batchOpen = false;
        awaitingAck++;
      } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk && leg != null) {
        require(batchOpen, "Chunk delivered outside batch");
        require(
            player != null && player.getChunkTrackingView().contains(chunk.getX(), chunk.getZ()),
            "Chunk from obsolete tracking view delivered after teleport");
        batchChunks++;
        require(++leg.packets <= 4096, "Unbounded fixture terrain delivery");
        long position = ChunkPos.asLong(chunk.getX(), chunk.getZ());
        if (!leg.received.add(position)) {
          leg.resends++;
          if (leg.resends == 1) leg.firstResend = chunk.getX() + "," + chunk.getZ();
        }
        long elapsed = System.nanoTime() - leg.started;
        if (leg.firstPacketNanos < 0) leg.firstPacketNanos = elapsed;
        if (Math.abs(chunk.getX() - leg.center.x) <= NEAR_RADIUS
            && Math.abs(chunk.getZ() - leg.center.z) <= NEAR_RADIUS) {
          // A ready/tracking callback may re-mark a delivered chunk. Native client chunk-cache
          // replacement accepts these updates; only the first delivery advances coverage/timing.
          if (!leg.nearbyReceived.add(position)) leg.nearResends++;
          else if (leg.nearbyReceived.size() == NEAR_COUNT) leg.nearbyNanos = elapsed;
        }
      }
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
