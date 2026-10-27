package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.payload.AuxiliaryLightDataPayload;

/** Native queue, acknowledgement and chunk/light bundle checks without a Minecraft client. */
public final class LocalChunkPacingRegression {
  private final ServerLevel level;
  private final ChunkPos center;
  private final long deadline = System.nanoTime() + 60_000_000_000L;
  private boolean finished;

  private LocalChunkPacingRegression(ServerLevel level) {
    this.level = level;
    center = new ChunkPos(level.getSharedSpawnPos());
  }

  static void start(ServerLevel level) {
    LocalChunkPacingRegression fixture = new LocalChunkPacingRegression(level);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, fixture.center, 3, fixture.center);
    NeoForge.EVENT_BUS.addListener(fixture::tick);
    NeoForge.EVENT_BUS.addListener(fixture::stopping);
  }

  private void tick(ServerTickEvent.Post event) {
    if (finished || event.getServer() != level.getServer()) return;
    if (System.nanoTime() > deadline)
      throw new IllegalStateException("Chunk pacing fixture timed out");
    if (!ready(level, center)) return;
    finished = true;
    try {
      verifyReady(level, center, 1.0F);
      LogUtils.getLogger()
          .info(
              "Local chunk pacing regression passed: local/remote quota, ACK, distance, drop and light bundles");
    } finally {
      releaseTicket();
    }
  }

  private void stopping(ServerStoppingEvent event) {
    if (event.getServer() == level.getServer()) releaseTicket();
  }

  private void releaseTicket() {
    level.getChunkSource().removeRegionTicket(TicketType.FORCED, center, 3, center);
  }

  /** Tests the active stack's public readiness boundary, including C2ME's installed redirect. */
  public static boolean ready(ServerLevel level, ChunkPos center) {
    for (int x = center.x - 2; x <= center.x + 2; x++) {
      for (int z = center.z - 2; z <= center.z + 2; z++) {
        if (level.getChunkSource().chunkMap.getChunkToSend(ChunkPos.asLong(x, z)) == null) {
          return false;
        }
      }
    }
    return true;
  }

  public static void verifyReady(ServerLevel level, ChunkPos center, float ackInputForOneChunk) {
    check(level.getServer().isSameThread(), "Run on the server thread");
    check(level.getServer().getPlayerList().getPlayerCount() == 0, "Disposable server only");
    check(ackInputForOneChunk > 0 && Float.isFinite(ackInputForOneChunk), "Invalid ACK input");
    List<LevelChunk> chunks = new ArrayList<>();
    for (int x = center.x - 2; x <= center.x + 2; x++) {
      for (int z = center.z - 2; z <= center.z + 2; z++) {
        LevelChunk chunk = level.getChunkSource().chunkMap.getChunkToSend(ChunkPos.asLong(x, z));
        check(chunk != null, "Not actually sendable: " + x + "," + z);
        chunks.add(chunk);
      }
    }
    // Both controls use the actual listener's final sender and native inbound ACK handler.
    List<Integer> local = verifyOne(level, center, chunks, true, ackInputForOneChunk);
    List<Integer> remote = verifyOne(level, center, chunks, false, ackInputForOneChunk);
    check(
        local.equals(remote), "Local/remote batch distribution differs: " + local + " / " + remote);
  }

  private static List<Integer> verifyOne(
      ServerLevel level,
      ChunkPos center,
      List<LevelChunk> chunks,
      boolean local,
      float ackInputForOneChunk) {
    MinecraftServer server = level.getServer();
    String name = local ? "AeroPaceLocal" : "AeroPaceRemote";
    UUID uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    ClientInformation info = ClientInformation.createDefault();
    ServerPlayer player = new ServerPlayer(server, level, new GameProfile(uuid, name), info);
    RecordingConnection connection = new RecordingConnection(local);
    CommonListenerCookie cookie =
        new CommonListenerCookie(player.getGameProfile(), 0, info, false, ConnectionType.NEOFORGE);
    RecordingListener listener = new RecordingListener(server, connection, player, cookie);
    PlayerChunkSender sender = listener.chunkSender;
    List<Integer> distribution = new ArrayList<>();
    try {
      // Set before registration; test performs no cold teleport or fake marker shortcut.
      player.absMoveTo(
          center.getMiddleBlockX(),
          level.getMaxBuildHeight() + 16.0,
          center.getMiddleBlockZ(),
          0,
          0);
      level.addNewPlayer(player);
      check(player.isAddedToLevel() && !player.isRemoved(), "Player was not added to level");
      check(level.getEntity(player.getId()) == player, "Entity registry does not contain player");
      check(level.players().contains(player), "Level player registry does not contain player");
      check(player.connection == listener, "Listener registration changed");
      check(connection.isMemoryConnection() == local, "Transport origin changed");

      // Level registration created a real tracking view/queue. Isolate it before adding the 25
      // targets.
      // This player is absent from the login roster; normal server-loop sending cannot interleave.
      player.getChunkTrackingView().forEach(position -> sender.dropChunk(player, position));
      connection.take();
      Set<ChunkPos> remaining = new LinkedHashSet<>();
      for (LevelChunk chunk : chunks) {
        remaining.add(chunk.getPos());
        sender.markChunkPendingToSend(chunk);
      }
      sender.markChunkPendingToSend(
          chunks.getFirst()); // Same pending chunk must not be duplicated.
      Set<ChunkPos> sent = new HashSet<>();

      sendBatch(player, connection, center, remaining, sent, distribution, 9);
      sendBatch(
          player, connection, center, remaining, sent, distribution, 0); // Initial ACK window 1.
      acknowledge(listener, ackInputForOneChunk);
      for (int index = 0; index < 10; index++) {
        sendBatch(player, connection, center, remaining, sent, distribution, 1);
      }
      sendBatch(
          player, connection, center, remaining, sent, distribution, 0); // Expanded window 10.
      check(remaining.size() == 6, "Unexpected initial backlog size");
      acknowledge(
          listener, ackInputForOneChunk); // One real ACK opens exactly one outstanding slot.
      sendBatch(player, connection, center, remaining, sent, distribution, 1);
      sendBatch(player, connection, center, remaining, sent, distribution, 0);

      ChunkPos dropped =
          remaining.stream().max(Comparator.comparingInt(center::distanceSquared)).orElseThrow();
      sender.dropChunk(player, dropped);
      check(!sender.isPending(dropped.toLong()), "Drop left pending chunk queued");
      check(connection.take().isEmpty(), "Dropping pending chunk sent a Forget packet");
      remaining.remove(dropped);
      ChunkPos previouslySent = sent.iterator().next();
      sender.dropChunk(player, previouslySent);
      List<Packet<?>> dropPackets = connection.take();
      check(
          dropPackets.size() == 1
              && dropPackets.getFirst() instanceof ClientboundForgetLevelChunkPacket forgotten
              && forgotten.pos().equals(previouslySent),
          "Dropping sent chunk did not send exactly its native Forget packet");

      // Ten actual batches are outstanding: never send more ACKs than previously emitted batches.
      for (int index = 0; index < 10; index++) acknowledge(listener, ackInputForOneChunk);
      while (!remaining.isEmpty()) {
        sendBatch(player, connection, center, remaining, sent, distribution, 1);
        acknowledge(listener, ackInputForOneChunk);
      }
      sendBatch(player, connection, center, remaining, sent, distribution, 0);
      check(
          sent.size() == 24 && !sent.contains(dropped),
          "Lost, duplicate, or dropped chunk delivered");
      for (LevelChunk chunk : chunks) {
        check(!sender.isPending(chunk.getPos().toLong()), "Final backlog did not drain");
      }
      return List.copyOf(distribution);
    } finally {
      try {
        if (player.isAddedToLevel() && !player.isRemoved()) {
          level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
      } finally {
        player.getTextFilter().leave();
        player.getAdvancements().stopListening();
      }
      check(level.getEntity(player.getId()) != player, "Fixture player remained registered");
      check(!level.players().contains(player), "Fixture player remained in level player list");
    }
  }

  private static void acknowledge(RecordingListener listener, float input) {
    listener.handleChunkBatchReceived(new ServerboundChunkBatchReceivedPacket(input));
  }

  private static void sendBatch(
      ServerPlayer player,
      RecordingConnection connection,
      ChunkPos center,
      Set<ChunkPos> remaining,
      Set<ChunkPos> sent,
      List<Integer> distribution,
      int expectedSize) {
    player.connection.chunkSender.sendNextChunks(player);
    List<Packet<?>> packets = connection.take();
    List<ChunkPos> actual = new ArrayList<>();
    boolean open = false;
    int starts = 0;
    int finishes = 0;
    for (Packet<?> packet : packets) {
      if (packet instanceof ClientboundChunkBatchStartPacket) {
        check(!open && starts++ == 0, "Nested/duplicate BatchStart");
        open = true;
      } else if (packet instanceof ClientboundChunkBatchFinishedPacket finish) {
        check(open && finishes++ == 0, "Missing start or duplicate BatchFinished");
        check(finish.batchSize() == actual.size(), "BatchFinished size mismatches terrain chunks");
        open = false;
      } else if (packet instanceof ClientboundBundlePacket bundle) {
        List<Packet<?>> contents = new ArrayList<>();
        bundle.subPackets().forEach(contents::add);
        if (contents.stream().anyMatch(ClientboundLevelChunkWithLightPacket.class::isInstance)) {
          check(open, "Terrain chunk outside batch envelope");
          check(contents.size() == 2, "Native chunk/light bundle was split or changed");
          check(
              contents.get(0) instanceof ClientboundLevelChunkWithLightPacket,
              "Chunk no longer first in native auxiliary-light bundle");
          ClientboundLevelChunkWithLightPacket chunk =
              (ClientboundLevelChunkWithLightPacket) contents.get(0);
          ChunkPos position = new ChunkPos(chunk.getX(), chunk.getZ());
          check(
              contents.get(1) instanceof ClientboundCustomPayloadPacket payload
                  && payload.payload() instanceof AuxiliaryLightDataPayload light
                  && light.pos().equals(position),
              "Missing/misordered auxiliary-light payload for " + position);
          actual.add(position);
        }
      } else {
        check(
            !(packet instanceof ClientboundLevelChunkWithLightPacket)
                && !(packet instanceof ClientboundForgetLevelChunkPacket),
            "Unexpected bare chunk or Forget during batch send");
        // Debug/event packets are not terrain chunks and need not be inside the envelope.
      }
    }
    check(!open, "Unclosed native chunk batch");
    check(actual.size() == expectedSize, "Expected batch " + expectedSize + ", got " + actual);
    check(
        starts == (expectedSize == 0 ? 0 : 1) && starts == finishes,
        "Batch envelope count changed");
    distribution.add(actual.size());
    int lastDistance = -1;
    for (ChunkPos position : actual) {
      check(remaining.contains(position), "Unexpected/duplicate chunk delivered: " + position);
      int nearest = remaining.stream().mapToInt(center::distanceSquared).min().orElseThrow();
      int distance = center.distanceSquared(position);
      check(distance == nearest && distance >= lastDistance, "Nearest-first priority changed");
      lastDistance = distance;
      remaining.remove(position);
      check(sent.add(position), "Chunk was delivered twice: " + position);
      check(
          !player.connection.chunkSender.isPending(position.toLong()),
          "Sent chunk remains pending");
    }
    for (ChunkPos position : remaining) {
      check(
          player.connection.chunkSender.isPending(position.toLong()),
          "Unsent chunk lost from queue");
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  /** Records the actual objects at the network boundary; no socket or channel is created. */
  private static final class RecordingConnection extends Connection {
    private final boolean memory;
    private final List<Packet<?>> packets = new ArrayList<>();

    private RecordingConnection(boolean memory) {
      super(PacketFlow.SERVERBOUND);
      this.memory = memory;
    }

    @Override
    public boolean isMemoryConnection() {
      return memory;
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
      packets.add(packet);
      if (listener != null) listener.onSuccess();
    }

    private List<Packet<?>> take() {
      List<Packet<?>> snapshot = List.copyOf(packets);
      packets.clear();
      return snapshot;
    }
  }

  /** Bypasses negotiation-only NetworkRegistry checks for this isolated protocol fixture. */
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
