package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import dev.aeroscapes.magic.AbilityNetwork;
import dev.aeroscapes.testship.TestShipNetwork;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFinalizeSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkChannel;
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

  int volleyLaunchPackets() {
    return connection.volleyLaunchPackets;
  }

  int volleyTrailPackets() {
    return connection.volleyTrailPackets;
  }

  @Nullable
  ClientboundLevelParticlesPacket takeSmokeParticles() {
    var packet = connection.smokeParticles;
    connection.smokeParticles = null;
    return packet;
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

  Vec3 footworkTravel() {
    require(connection.teleport == null, "Footwork must not teleport the player");
    var footwork = java.util.Objects.requireNonNull(connection.footwork, "Missing footwork motion");
    connection.footwork = null;
    return footwork.travel();
  }

  boolean hasFootwork() {
    return connection.footwork != null;
  }

  void receivePlayPayload(CustomPacketPayload payload, String version) {
    var id = payload.type().id();
    ChannelAttributes.setPayloadSetup(
        connection,
        new NetworkPayloadSetup(
            Map.of(ConnectionProtocol.PLAY, Map.of(id, new NetworkChannel(id, version)))));
    listener.handleCustomPayload(new ServerboundCustomPayloadPacket(payload));
  }

  void releaseUsingItem() {
    receivePlayerAction(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM);
  }

  void swapHands() {
    receivePlayerAction(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
  }

  private void receivePlayerAction(ServerboundPlayerActionPacket.Action action) {
    listener.handlePlayerAction(
        new ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN));
  }

  void selectHotbarSlot(int slot) {
    listener.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(slot));
  }

  void look(float yaw, float pitch) {
    listener.handleMovePlayer(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround()));
  }

  void moveOnGround(Vec3 position) {
    listener.handleMovePlayer(
        new ServerboundMovePlayerPacket.Pos(position.x, position.y, position.z, true));
    require(connection.teleport == null, "Server corrected footwork movement");
  }

  @Nullable
  TestShipNetwork.Check takeBoardingCheck() {
    TestShipNetwork.Check check = connection.boardingCheck;
    connection.boardingCheck = null;
    return check;
  }

  AbilityNetwork.Snapshot abilities() {
    return java.util.Objects.requireNonNull(connection.abilities, "Missing ability snapshot");
  }

  @Nullable
  TranslatableContents takeStudyProgress() {
    var progress = connection.studyProgress;
    connection.studyProgress = null;
    return progress;
  }

  @Nullable
  AbilityNetwork.StudyCompleted takeStudyCompleted() {
    var result = connection.studyCompleted;
    connection.studyCompleted = null;
    return result;
  }

  @Nullable
  String takeAbilityReason() {
    String reason = connection.abilityReason;
    connection.abilityReason = null;
    return reason;
  }

  int jellyShelterHints() {
    return connection.jellyShelterHints;
  }

  @Nullable
  Component takeHexReport() {
    Component report = connection.hexReport;
    connection.hexReport = null;
    return report;
  }

  <T extends CustomPacketPayload> T takePayload(Class<T> type) {
    var payload = connection.lastPayload;
    connection.lastPayload = null;
    require(type.isInstance(payload), "Missing outgoing " + type.getSimpleName());
    return type.cast(payload);
  }

  Map<Holder<MobEffect>, Packet<?>> takeEffectChanges() {
    var changes = Map.copyOf(connection.effectChanges);
    connection.effectChanges.clear();
    return changes;
  }

  boolean receivedShipParts(TestShipNetwork.Check check) {
    return connection.completedParts.containsAll(check.members());
  }

  @Nullable
  net.puffish.skillsmod.server.network.packets.out.PointsUpdateOutPacket talentPoints(
      String category) {
    return connection.talentPoints.get(category);
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
    private static final String ABILITY_REASON = "aeroscapes_reimagine.abilities.reason.";
    @Nullable private Component hexReport;
    @Nullable private String abilityReason;
    private int jellyShelterHints;
    private int volleyLaunchPackets;
    private int volleyTrailPackets;
    @Nullable private ClientboundLevelParticlesPacket smokeParticles;
    @Nullable private CustomPacketPayload lastPayload;
    private final Map<Holder<MobEffect>, Packet<?>> effectChanges = new HashMap<>();
    @Nullable private AbilityNetwork.Snapshot abilities;
    @Nullable private TranslatableContents studyProgress;
    @Nullable private AbilityNetwork.StudyCompleted studyCompleted;
    private final Map<
            String, net.puffish.skillsmod.server.network.packets.out.PointsUpdateOutPacket>
        talentPoints = new HashMap<>();
    @Nullable private ClientboundPlayerPositionPacket teleport;
    @Nullable private AbilityNetwork.Footwork footwork;
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
      if (packet instanceof ClientboundLevelParticlesPacket particles) {
        if (particles.getParticle() == ParticleTypes.CRIT && particles.getCount() == 5)
          volleyLaunchPackets++;
        if (particles.getParticle() == ParticleTypes.CRIT && particles.getCount() == 3)
          volleyTrailPackets++;
        if (particles.getParticle() == ParticleTypes.LARGE_SMOKE) smokeParticles = particles;
      }
      if (packet instanceof ClientboundSystemChatPacket chat
          && chat.content().getContents() instanceof TranslatableContents text
          && text.getKey().startsWith("aeroscapes_reimagine.hex_quality."))
        hexReport = chat.content();
      if (packet instanceof ClientboundSystemChatPacket chat
          && chat.overlay()
          && chat.content().getContents() instanceof TranslatableContents text
          && text.getKey().equals("message.aeroscapes_reimagine.cloud_jelly.shelter"))
        jellyShelterHints++;
      if (packet instanceof ClientboundSystemChatPacket chat
          && chat.overlay()
          && chat.content().getContents() instanceof TranslatableContents text
          && text.getKey().startsWith(ABILITY_REASON))
        abilityReason = text.getKey().substring(ABILITY_REASON.length());
      if (packet instanceof ClientboundSystemChatPacket chat
          && chat.overlay()
          && chat.content().getContents() instanceof TranslatableContents text
          && text.getKey().equals("aeroscapes_reimagine.abilities.study.notice.progress"))
        studyProgress = text;
      if (packet instanceof ClientboundCustomPayloadPacket message) lastPayload = message.payload();
      if (packet instanceof ClientboundUpdateMobEffectPacket effect)
        effectChanges.put(effect.getEffect(), effect);
      if (packet instanceof ClientboundRemoveMobEffectPacket effect)
        effectChanges.put(effect.effect(), effect);
      if (packet instanceof ClientboundPlayerPositionPacket position) teleport = position;
      if (packet instanceof ClientboundKeepAlivePacket challenge) keepAlive = challenge.getId();
      if (packet instanceof ClientboundCustomPayloadPacket message
          && message.payload() instanceof AbilityNetwork.Snapshot snapshot) abilities = snapshot;
      if (packet instanceof ClientboundCustomPayloadPacket message
          && message.payload() instanceof AbilityNetwork.StudyCompleted result)
        studyCompleted = result;
      if (packet instanceof ClientboundCustomPayloadPacket message
          && message.payload() instanceof AbilityNetwork.Footwork cue) footwork = cue;
      if (packet instanceof ClientboundCustomPayloadPacket payload
          && payload.payload() instanceof TestShipNetwork.Check check) boardingCheck = check;
      if (packet instanceof ClientboundCustomPayloadPacket payload
          && payload.payload().type().id().toString().equals("puffish_skills:points_update")) {
        try {
          // The pinned Fabric transport wraps the common OutPacket in InOutPayload.
          Object out =
              payload.payload().getClass().getMethod("outPacket").invoke(payload.payload());
          if (out
              instanceof
              net.puffish.skillsmod.server.network.packets.out.PointsUpdateOutPacket points)
            talentPoints.put(points.categoryId().toString(), points);
          else throw new IllegalStateException("Unexpected talent point payload " + out);
        } catch (ReflectiveOperationException exception) {
          throw new IllegalStateException("Cannot inspect the pinned Fabric transport", exception);
        }
      }
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
