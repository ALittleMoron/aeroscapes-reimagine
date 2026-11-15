package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import dev.aeroscapes.preflight.PreflightNetwork;
import dev.aeroscapes.preflight.PreflightReadings;
import dev.aeroscapes.preflight.PreflightSurvey;
import dev.aeroscapes.testship.TestAirshipBlueprint;
import dev.aeroscapes.testship.TestShips;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.joml.Vector3d;

public final class PreflightRegression {
  private PreflightRegression() {}

  public static void verify(ServerLevel level) throws IOException, ReflectiveOperationException {
    var blueprint = TestAirshipBlueprint.load(level);
    var container = SubLevelContainer.getContainer(level);
    Pose3d pose = new Pose3d();
    pose.position().set(level.getSharedSpawnPos().getX(), 80, level.getSharedSpawnPos().getZ());
    var root = (ServerSubLevel) container.allocateNewSubLevel(pose);
    var allocated = new HashSet<UUID>();
    allocated.add(root.getUniqueId());
    try {
      CompoundTag tag = new CompoundTag(), marker = new CompoundTag();
      marker.putInt("schema", 2);
      marker.putUUID("owner", UUID.randomUUID());
      marker.putUUID("ship", root.getUniqueId());
      tag.put(TestShips.MARKER, marker);
      root.setUserDataTag(tag);
      var chunks = new HashSet<ChunkPos>();
      for (var relative : blueprint.blocks().keySet())
        chunks.add(new ChunkPos(root.getPlot().getCenterBlock().offset(relative)));
      for (var chunk : chunks) root.getPlot().newEmptyChunk(chunk);
      blueprint.place(root);
      var local = root.getPlot().getCenterBlock();
      var center =
          new Vector3d(root.getMassTracker().getCenterOfMass())
              .sub(local.getX(), local.getY(), local.getZ())
              .add(pose.position());
      root.logicalPose().position().set(center);
      container.physicsSystem().getPipeline().teleport(root, center, pose.orientation());
      root.updateLastPose();
      root.updateBoundingBox();
      root.forceUpdateGlobalBounds();
      TestAirshipBlueprint.initialize(root, allocated);
      verifyPlayerSelection(level, root);
      require(allocated.size() == 2, "Native airship fixture includes its jointed steering body");
      var snapshot = survey(root, 31);
      require(
          snapshot.bodies() == 2 && snapshot.balloons() == 1 && !snapshot.partial(),
          "Server snapshot includes connected bodies and the single native balloon once");
      require(
          snapshot.members().containsAll(allocated),
          "Snapshot identifies every connected body so crossing a joint keeps vessel membership");
      double expectedMass = 0;
      ServerSubLevel child = null;
      for (UUID id : allocated) {
        var body = (ServerSubLevel) container.getSubLevel(id);
        expectedMass += body.getMassTracker().getMass();
        if (!id.equals(root.getUniqueId())) child = body;
      }
      require(
          Math.abs(snapshot.mass() - expectedMass) < 1.0E-6,
          "Snapshot reads actual native body mass including linked steering");
      require(
          child != null && survey(child, 32).bodies() == 2,
          "Starting from the child discovers incoming native joint connections");
      var burner =
          TestAirshipBlueprint.entity(
              root, TestAirshipBlueprint.BURNER, HotAirBurnerBlockEntity.class);
      require(burner.getBalloon() instanceof ServerBalloon, "Native burner formed a balloon");
      var balloon = (ServerBalloon) burner.getBalloon();
      var gasCenter = balloon.getCenter();
      var world =
          root.logicalPose().transformPosition(new Vector3d(gasCenter.x, gasCenter.y, gasCenter.z));
      double expectedSupport =
          balloon.getTotalLift() * DimensionPhysicsData.getAirPressure(level, world);
      require(
          Math.abs(snapshot.support() - expectedSupport) < 1.0E-6,
          "Snapshot applies native air pressure to balloon support, rather than labeling lift as force");
      require(
          snapshot.filled() == balloon.getTotalFilledVolume()
              && snapshot.target() == balloon.getTotalTargetVolume()
              && snapshot.capacity() == balloon.getCapacity()
              && snapshot.change() == balloon.getTotalVolumeChange(),
          "Snapshot reads actual volume, target, capacity and changing volume");
      require(
          snapshot.fuels().size() == 1
              && snapshot.fuels().getFirst().count() > 0
              && snapshot.fuels().getFirst().burningTicks() > 0,
          "Snapshot finds the native fueled portable engine without scanning world terrain");
      verifyCodec(level, snapshot);
      require(!snapshot.leaking(), "Intact native envelope has no leak");
      balloon.setLeaking();
      var leaking = survey(root, 33);
      require(
          leaking.leaking()
              && PreflightReadings.assessment(
                      leaking.mass(),
                      leaking.support(),
                      leaking.balloons(),
                      leaking.leaking(),
                      leaking.partial(),
                      leaking.consistentGravity())
                  == PreflightReadings.Assessment.LEAKING,
          "Server leak accessor and assessment expose actual native envelope failure");
    } finally {
      var map = BalloonMap.MAP.get(level);
      var owned = new ArrayList<dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon>();
      for (var balloon : map.getBalloons()) {
        var containing = Sable.HELPER.getContaining(level, balloon.getControllerPos());
        if (containing != null && allocated.contains(containing.getUniqueId())) owned.add(balloon);
      }
      owned.forEach(map::removeBalloon);
      List<UUID> cleanup = new ArrayList<>(allocated);
      cleanup.remove(root.getUniqueId());
      cleanup.add(root.getUniqueId());
      for (UUID id : cleanup) {
        var body = container.getSubLevel(id);
        if (body != null) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
      }
    }
  }

  private static void verifyPlayerSelection(ServerLevel level, ServerSubLevel root)
      throws ReflectiveOperationException {
    var player =
        new ServerPlayer(
            level.getServer(),
            level,
            new GameProfile(UUID.randomUUID(), "PreflightAudit"),
            ClientInformation.createDefault());
    var tracking = (EntityMovementExtension) player;
    var position = root.logicalPose().position();
    player.setPos(position.x(), position.y(), position.z());
    require(
        aboardBody(player) == null,
        "A nearby player cannot select arbitrary bodies without native tracking");
    tracking.sable$setTrackingSubLevel(root);
    try {
      require(
          aboardBody(player) == null,
          "A native association alone cannot expose a body not sent to this player");
      root.getTrackingPlayers().add(player.getUUID());
      require(
          aboardBody(player) == root,
          "A nearby player with the actual native body association can inspect it");
      player.setPos(position.x() + 10000, position.y(), position.z());
      require(
          aboardBody(player) == null,
          "A stale native association cannot inspect the ship from far away");
    } finally {
      root.getTrackingPlayers().remove(player.getUUID());
      tracking.sable$setTrackingSubLevel(null);
    }
  }

  private static PreflightNetwork.Snapshot survey(ServerSubLevel body, int sequence)
      throws ReflectiveOperationException {
    var method = PreflightSurvey.class.getDeclaredMethod("survey", ServerSubLevel.class, int.class);
    method.setAccessible(true);
    return PreflightNetwork.Snapshot.class.cast(method.invoke(null, body, sequence));
  }

  private static ServerSubLevel aboardBody(ServerPlayer player)
      throws ReflectiveOperationException {
    var method = PreflightSurvey.class.getDeclaredMethod("aboardBody", ServerPlayer.class);
    method.setAccessible(true);
    return ServerSubLevel.class.cast(method.invoke(null, player));
  }

  private static void verifyCodec(ServerLevel level, PreflightNetwork.Snapshot snapshot) {
    var buffer =
        new RegistryFriendlyByteBuf(
            Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
    try {
      PreflightNetwork.Request.CODEC.encode(buffer, new PreflightNetwork.Request(17));
      require(
          PreflightNetwork.Request.CODEC.decode(buffer).sequence() == 17 && !buffer.isReadable(),
          "Client requests contain only a sequence, with no target coordinates or body ID");
      buffer.clear();
      PreflightNetwork.Snapshot.CODEC.encode(buffer, snapshot);
      require(
          PreflightNetwork.Snapshot.CODEC.decode(buffer).equals(snapshot) && !buffer.isReadable(),
          "Bounded server snapshot and engine readings survive the actual network codec");
      buffer.clear();
      var invalid =
          new PreflightNetwork.Snapshot(
              snapshot.sequence(),
              snapshot.dimension(),
              snapshot.body(),
              snapshot.name(),
              java.util.Collections.nCopies(65, snapshot.body()),
              snapshot.balloons(),
              snapshot.mass(),
              snapshot.support(),
              snapshot.filled(),
              snapshot.target(),
              snapshot.capacity(),
              snapshot.change(),
              snapshot.leaking(),
              snapshot.partial(),
              snapshot.consistentGravity(),
              snapshot.fuels());
      PreflightNetwork.Snapshot.CODEC.encode(buffer, invalid);
      try {
        PreflightNetwork.Snapshot.CODEC.decode(buffer);
        throw new IllegalStateException("Snapshot decoder accepted too many bodies");
      } catch (DecoderException expected) {
        require(expected.getMessage().contains("bounds"), "Invalid body count reports bounds");
      }
    } finally {
      buffer.release();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
