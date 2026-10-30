package dev.aeroscapes.regression;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.aeroscapes.testship.TestAirshipBlueprint;
import dev.aeroscapes.testship.TestShipContainer;
import dev.aeroscapes.testship.TestShips;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlockEntity;
import dev.simulated_team.simulated.network.packets.SteeringWheelPacket;
import foundry.veil.api.network.handler.ServerPacketContext;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

/** Complete downloaded ship, real native controls, editable hull and save/reload; no client. */
public final class TestShipRegression {
  private static final UUID OWNER = UUID.fromString("5e26b1e7-647c-4b6a-8386-5b2f0594151b");
  private static final BlockPos ADDED = new BlockPos(5, 1, 2);
  private final ServerLevel level;
  private final boolean reload;
  private TestShipPlayerSession session;
  private ServerSubLevel ship;
  private int phase, elapsed;
  private Vector3d start;
  private double previous, mass;
  private UUID originalChild;
  private boolean finished;

  private TestShipRegression(ServerLevel level, boolean reload) {
    this.level = level;
    this.reload = reload;
  }

  public static void start(ServerLevel level, String mode) {
    require(mode.equals("prepare") || mode.equals("reload"), "unknown ship mode");
    TestShipRegression fixture = new TestShipRegression(level, mode.equals("reload"));
    for (int x = -4; x <= 5; x++)
      for (int z = -4; z <= 5; z++) {
        level.getChunk(x, z);
        level.setChunkForced(x, z, true);
      }
    level.setBlockAndUpdate(new BlockPos(8, 79, 8), Blocks.STONE.defaultBlockState());
    CompoundTag saved = null;
    if (fixture.reload)
      try {
        saved =
            NbtIo.readCompressed(
                level
                    .getServer()
                    .getWorldPath(LevelResource.ROOT)
                    .resolve("airship-fixture-player.dat"),
                NbtAccounter.create(1048576));
      } catch (IOException failure) {
        throw new IllegalStateException("Cannot restore fixture player", failure);
      }
    fixture.session =
        TestShipPlayerSession.create(level, OWNER, "WeatherPilot", new Vec3(8.5, 80, 8.5), saved);
    NeoForge.EVENT_BUS.addListener(fixture::tick);
    NeoForge.EVENT_BUS.addListener(fixture::stop);
  }

  private void tick(ServerTickEvent.Post event) {
    if (finished || event.getServer() != level.getServer()) return;
    if (++elapsed > 600) throw new IllegalStateException("Airship phase timed out: " + phase);
    try {
      session.tick();
      advance();
    } catch (CommandSyntaxException | IOException failure) {
      throw new IllegalStateException("Airship phase " + phase, failure);
    }
  }

  private void advance() throws CommandSyntaxException, IOException {
    var container = SubLevelContainer.getContainer(level);
    if (phase == 0) {
      if (elapsed < 40) return;
      if (reload) {
        ship = TestShips.ship(session.player);
        if (!(TestAirshipBlueprint.entity(
                    ship, TestAirshipBlueprint.BURNER, HotAirBurnerBlockEntity.class)
                .getBalloon()
            instanceof ServerBalloon)) return;
        require(
            barrel().getItem(0).is(net.minecraft.world.item.Items.DIAMOND)
                && barrel().getItem(0).getCount() == 7,
            "owner container and contents persist across native subassembly and restart");
        require(
            TestAirshipBlueprint.entity(
                        ship, TestAirshipBlueprint.ENGINE, PortableEngineBlockEntity.class)
                    .inventory
                    .getItem(0)
                    .getCount()
                > 0,
            "fuel persists");
        require(balloon().getTotalFilledVolume() > 1, "native gas persists");
        require(
            bearing().getSubLevelID() != null
                && container.getSubLevel(bearing().getSubLevelID()) != null,
            "steering child persists");
        TestShips.board(session.player);
        require(!session.player.isPassenger(), "reboarding waits for client confirmation");
        phase = 91;
        elapsed = 0;
        return;
      }
      for (ServerSubLevel legacy : java.util.List.copyOf(container.getAllSubLevels())) {
        if (marker(legacy).getInt("schema") != 1 || !OWNER.equals(marker(legacy).getUUID("owner")))
          continue;
        require(
            ((TestShipContainer) container).aeroscapes$testShips().get(legacy.getUniqueId())
                != null,
            "old platform safe on upgrade");
        reference(legacy);
        TestShips.remove(session.player);
        require(legacy.isRemoved(), "old platform removable");
        LogUtils.getLogger().info("Legacy platform upgrade regression passed");
      }
      int count = container.getAllSubLevels().size();
      reject("aeroscapes_test_ship", session.player.createCommandSourceStack().withPermission(0));
      reject("aeroscapes_test_ship", level.getServer().createCommandSourceStack());
      require(container.getAllSubLevels().size() == count, "invalid source allocates no plot");
      BlockPos obstruction = new BlockPos(8, 104, 28);
      level.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
      try {
        TestShips.spawn(session.player);
        throw new IllegalStateException("Blocked spawn accepted");
      } catch (CommandSyntaxException expected) {
      }
      require(
          count == container.getAllSubLevels().size()
              && level.getBlockState(obstruction).is(Blocks.STONE),
          "blocked spawn preserves terrain");
      level.setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState());
      level
          .getServer()
          .getCommands()
          .getDispatcher()
          .execute(
              "aeroscapes_test_ship", session.player.createCommandSourceStack().withPermission(2));
      ship = TestShips.ship(session.player);
      require(
          !session.player.isPassenger()
              && session.player.position().distanceToSqr(new Vec3(8.5, 80, 8.5)) < .01,
          "creation keeps player safe until the client receives the ship");
      require(
          container.getAllSubLevels().size() == count + 2,
          "native parent and steering child created");
      mass = ship.getMassTracker().getMass();
      phase = 89;
      elapsed = 0;
      return;
    }
    if (phase == 89) {
      require(!session.player.isPassenger(), "no boarding without client confirmation");
      if (elapsed < 220) return;
      require(
          session.player.position().distanceToSqr(new Vec3(8.5, 80, 8.5)) < .01,
          "timeout leaves player at safe starting point");
      session.takeBoardingCheck();
      TestShips.board(session.player);
      phase = 90;
      elapsed = 0;
      return;
    }
    if (phase == 90 || phase == 91) {
      var check = session.takeBoardingCheck();
      if (check == null) {
        require(!session.player.isPassenger(), "no boarding before client confirmation");
        return;
      }
      require(
          check.members().contains(ship.getUniqueId()) && check.members().size() >= 2,
          "client check includes ship and steering child");
      require(session.receivedShipParts(check), "all ship parts are sent before the client check");
      var boarding = ((TestShipContainer) container).aeroscapes$testShips().boarding();
      boarding.confirm(session.player, UUID.randomUUID());
      require(!session.player.isPassenger(), "unrelated confirmation cannot board player");
      boarding.confirm(session.player, check.token());
      session.acknowledgeTeleport();
      require(session.player.isPassenger(), "confirmed player boards ship");
      if (phase == 91) lever(TestAirshipBlueprint.DRIVE_LEVER).setSignal(13);
      start = new Vector3d(ship.logicalPose().position());
      phase = phase == 90 ? 1 : 20;
      elapsed = 0;
      return;
    }
    require(
        container.getSubLevel(ship.getUniqueId()) == ship,
        "native vessel stays loaded around pilot");
    var pipeline = container.physicsSystem().getPipeline();
    Vector3d velocity = pipeline.getLinearVelocity(ship, new Vector3d());
    require(
        !ship.isRemoved()
            && velocity.isFinite()
            && new Vector3d(0, 1, 0).rotate(ship.logicalPose().orientation()).y > .65,
        "ship remains finite and upright");
    require(
        ((TestShipContainer) container).aeroscapes$testShips().get(ship.getUniqueId()) == null,
        "no artificial hold");
    if (elapsed % 40 == 0)
      LogUtils.getLogger()
          .info(
              "Downloaded flight phase={} tick={} position={} velocity={} prop={} gas={} lift={} wheel={} bearing={} pilot={}",
              phase,
              elapsed,
              ship.logicalPose().position(),
              velocity,
              propeller().getSpeed(),
              balloon().getTotalFilledVolume(),
              lever(TestAirshipBlueprint.LIFT_LEVER).getState(),
              wheel().getAngle(),
              bearing().getTargetAngleDegrees(),
              session.player.position());
    if (phase == 1 && elapsed >= 80) {
      require(
          Math.abs(ship.logicalPose().position().y() - start.y) < 5,
          "native balloon supports ship");
      lever(TestAirshipBlueprint.DRIVE_LEVER).setSignal(13);
      start = new Vector3d(ship.logicalPose().position());
      phase = 2;
      elapsed = 0;
    } else if (phase == 2 && elapsed >= 120) {
      require(
          Math.abs(propeller().getSpeed()) >= 200, "native author transmission drives propeller");
      require(
          ship.logicalPose().position().distanceSquared(start) > 1,
          "downloaded ship actually flies");
      new SteeringWheelPacket(false, 30f, wheel().getBlockPos()).handle(context());
      phase = 3;
      elapsed = 0;
    } else if (phase == 3 && elapsed >= 80) {
      require(
          Math.abs(wheel().getAngle()) > 20 && Math.abs(bearing().getTargetAngleDegrees()) > 20,
          "native wheel drives steering bearing");
      previous = bearing().getTargetAngleDegrees();
      new SteeringWheelPacket(false, -30f, wheel().getBlockPos()).handle(context());
      phase = 4;
      elapsed = 0;
    } else if (phase == 4 && elapsed >= 80) {
      require(previous * bearing().getTargetAngleDegrees() < 0, "steering reverses");
      new SteeringWheelPacket(false, 0f, wheel().getBlockPos()).handle(context());
      lever(TestAirshipBlueprint.DRIVE_LEVER).setSignal(15);
      phase = 5;
      elapsed = 0;
    } else if (phase == 5 && elapsed >= 40) {
      new SteeringWheelPacket(true, 0f, wheel().getBlockPos()).handle(context());
      require(Math.abs(propeller().getSpeed()) < .1, "native lever stops drive");
      previous = balloon().getTotalFilledVolume();
      start = new Vector3d(ship.logicalPose().position());
      lever(TestAirshipBlueprint.LIFT_LEVER).setSignal(10);
      phase = 6;
      elapsed = 0;
    } else if (phase == 6 && elapsed >= 200) {
      require(
          balloon().getTotalFilledVolume() > previous + .5
              && ship.logicalPose().position().y() > start.y + .2,
          "native lift lever produces ascent");
      previous = balloon().getTotalFilledVolume();
      lever(TestAirshipBlueprint.LIFT_LEVER).setSignal(7);
      phase = 7;
      elapsed = 0;
    } else if (phase == 7 && elapsed >= 200) {
      require(balloon().getTotalFilledVolume() < previous - .5, "native gas decreases");
      originalChild = bearing().getSubLevelID();
      bearing().disassemble();
      phase = 8;
      elapsed = 0;
    } else if (phase == 8 && elapsed >= 20) {
      require(
          container.getSubLevel(originalChild) == null,
          "disassembly removes original steering body");
      bearing().assemble();
      phase = 9;
      elapsed = 0;
    } else if (phase == 9 && elapsed >= 20) {
      require(
          bearing().isAssembled() && !originalChild.equals(bearing().getSubLevelID()),
          "native steering can be rebuilt");
      var child = (ServerSubLevel) container.getSubLevel(bearing().getSubLevelID());
      require(
          marker(child).getUUID("root").equals(ship.getUniqueId()),
          "rebuilt steering ownership captured");
      for (int x = 1; x <= 5; x++) {
        BlockPos support = ship.getPlot().getCenterBlock().offset(x, 0, 2);
        if (level.getBlockState(support).isAir())
          level.setBlockAndUpdate(support, Blocks.SPRUCE_PLANKS.defaultBlockState());
      }
      level.setBlockAndUpdate(
          ship.getPlot().getCenterBlock().offset(ADDED), Blocks.BARREL.defaultBlockState());
      ((net.minecraft.world.level.block.entity.BarrelBlockEntity)
              level.getBlockEntity(ship.getPlot().getCenterBlock().offset(ADDED)))
          .setItem(
              0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
      require(ship.getMassTracker().getMass() > mass, "owner can extend hull");
      BlockPos extra = ship.getPlot().getCenterBlock().offset(4, 1, 2);
      var state =
          net.minecraft.core.registries.BuiltInRegistries.BLOCK
              .get(net.minecraft.resources.ResourceLocation.parse("simulated:swivel_bearing"))
              .defaultBlockState()
              .setValue(
                  net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                  net.minecraft.core.Direction.UP);
      level.setBlockAndUpdate(extra, state);
      level.setBlockAndUpdate(extra.above(), Blocks.SPRUCE_PLANKS.defaultBlockState());
      ((SwivelBearingBlockEntity) level.getBlockEntity(extra)).assemble();
      require(
          marker(ship).getList("children", net.minecraft.nbt.Tag.TAG_INT_ARRAY).size() == 2,
          "added native mechanism is explicitly owned");
      require(
          barrel().getItem(0).getCount() == 7,
          "native assembly preserves owner container without duplication");
      try (var other =
          TestShipPlayerSession.create(
              level,
              UUID.fromString("8619e837-52a0-4be3-a1e4-99a8f7d669d0"),
              "OtherPilot",
              new Vec3(8.5, 80, 8.5),
              null)) {
        TestShips.copyReference(session.player, other.player);
        try {
          TestShips.remove(other.player);
          throw new IllegalStateException("Foreign owner removed vessel");
        } catch (CommandSyntaxException expected) {
        }
        var bounds = child.boundingBox().toMojang();
        other.player.absMoveTo(
            (bounds.minX + bounds.maxX) / 2,
            (bounds.minY + bounds.maxY) / 2,
            (bounds.minZ + bounds.maxZ) / 2,
            0,
            0);
        try {
          TestShips.remove(session.player);
          throw new IllegalStateException("Occupied child removed");
        } catch (CommandSyntaxException expected) {
        }
        require(!ship.isRemoved() && !child.isRemoved(), "occupied family remains intact");
      }
      phase = 10;
      elapsed = 0;
    } else if (phase == 10 && elapsed >= 20) {
      require(barrel().getItem(0).getCount() == 7, "container survives normal post-assembly ticks");
      for (var id : marker(ship).getList("children", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
        var body = (ServerSubLevel) container.getSubLevel(net.minecraft.nbt.NbtUtils.loadUUID(id));
        LogUtils.getLogger()
            .info(
                "Saved owned body id={} bounds={}",
                body.getUniqueId(),
                body.getPlot().getBoundingBox());
      }
      int checkpointX = ((int) Math.floor(ship.logicalPose().position().x())) >> 4;
      int checkpointZ = ((int) Math.floor(ship.logicalPose().position().z())) >> 4;
      for (int x = checkpointX - 1; x <= checkpointX + 1; x++)
        for (int z = checkpointZ - 1; z <= checkpointZ + 1; z++) {
          level.getChunk(x, z);
          level.setChunkForced(x, z, true);
        }
      TestShips.leave(session.player);
      session.acknowledgeTeleport();
      require(
          session.player.position().distanceToSqr(new Vec3(8.5, 80, 8.5)) < .01
              && !session.player.isPassenger(),
          "real player returns home");
      NbtIo.writeCompressed(
          session.player.saveWithoutId(new CompoundTag()),
          level.getServer().getWorldPath(LevelResource.ROOT).resolve("airship-fixture-player.dat"));
      session.close();
      session = null;
      level.getServer().saveEverything(false, true, true);
      LogUtils.getLogger().info("Downloaded airship prepare regression passed");
      finished = true;
    } else if (phase == 20 && elapsed >= 120) {
      require(
          Math.abs(propeller().getSpeed()) >= 200
              && ship.logicalPose().position().distanceSquared(start) > 1,
          "flight resumes after restart");
      TestShips.leave(session.player);
      session.acknowledgeTeleport();
      var children = new java.util.ArrayList<UUID>();
      for (var value : marker(ship).getList("children", net.minecraft.nbt.Tag.TAG_INT_ARRAY))
        children.add(net.minecraft.nbt.NbtUtils.loadUUID(value));
      require(children.size() == 2, "rebuilt and added mechanisms persist");
      TestShips.remove(session.player);
      require(
          ship.isRemoved() && children.stream().allMatch(id -> container.getSubLevel(id) == null),
          "edited root and every owned child removed");
      for (int x = -4; x <= 5; x++) for (int z = -4; z <= 5; z++) level.setChunkForced(x, z, false);
      session.close();
      session = null;
      LogUtils.getLogger().info("Downloaded airship reload regression passed");
      finished = true;
    }
  }

  private net.minecraft.world.level.block.entity.BarrelBlockEntity barrel() {
    var bodies = new java.util.ArrayList<ServerSubLevel>();
    bodies.add(ship);
    var container = SubLevelContainer.getContainer(level);
    for (var id : marker(ship).getList("children", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
      var found = container.getSubLevel(net.minecraft.nbt.NbtUtils.loadUUID(id));
      require(found instanceof ServerSubLevel, "owned mechanism loaded");
      bodies.add((ServerSubLevel) found);
    }
    net.minecraft.world.level.block.entity.BarrelBlockEntity result = null;
    for (var body : bodies) {
      var bounds = body.getPlot().getBoundingBox();
      for (BlockPos position :
          BlockPos.betweenClosed(
              bounds.minX(),
              bounds.minY(),
              bounds.minZ(),
              bounds.maxX(),
              bounds.maxY(),
              bounds.maxZ())) {
        if (level.getBlockEntity(position)
            instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity found) {
          require(result == null, "owner container is not duplicated");
          result = found;
          LogUtils.getLogger()
              .info(
                  "Owner container phase={} body={} position={} count={}",
                  phase,
                  body.getUniqueId(),
                  position,
                  found.getItem(0).getCount());
        }
      }
    }
    require(result != null, "owner container exists in vessel family");
    return result;
  }

  private ServerBalloon balloon() {
    return (ServerBalloon)
        TestAirshipBlueprint.entity(
                ship, TestAirshipBlueprint.BURNER, HotAirBurnerBlockEntity.class)
            .getBalloon();
  }

  private ThrottleLeverBlockEntity lever(BlockPos pos) {
    return TestAirshipBlueprint.entity(ship, pos, ThrottleLeverBlockEntity.class);
  }

  private KineticBlockEntity propeller() {
    return TestAirshipBlueprint.entity(
        ship, TestAirshipBlueprint.FORWARD_PROPELLER, KineticBlockEntity.class);
  }

  private SwivelBearingBlockEntity bearing() {
    return TestAirshipBlueprint.entity(
        ship, TestAirshipBlueprint.BEARING, SwivelBearingBlockEntity.class);
  }

  private SteeringWheelBlockEntity wheel() {
    return TestAirshipBlueprint.entity(
        ship, TestAirshipBlueprint.WHEEL, SteeringWheelBlockEntity.class);
  }

  private ServerPacketContext context() {
    ServerPlayer player = session.player;
    return new ServerPacketContext() {
      @Override
      public ServerPlayer player() {
        return player;
      }

      @Override
      public Packet<?> createPacket(CustomPacketPayload payload) {
        return new ClientboundCustomPayloadPacket(payload);
      }

      @Override
      public void sendPacket(Packet<?> packet, PacketSendListener listener) {
        player.connection.send(packet, listener);
      }

      @Override
      public void disconnect(Component reason) {
        throw new IllegalStateException("Native input disconnected pilot: " + reason.getString());
      }
    };
  }

  private static CompoundTag marker(ServerSubLevel ship) {
    CompoundTag tag = ship.getUserDataTag();
    return tag == null ? new CompoundTag() : tag.getCompound(TestShips.MARKER);
  }

  private void reference(ServerSubLevel target) {
    CompoundTag tag = new CompoundTag();
    tag.putUUID("ship", target.getUniqueId());
    tag.putDouble("homeX", 8.5);
    tag.putDouble("homeY", 80);
    tag.putDouble("homeZ", 8.5);
    CompoundTag persisted = new CompoundTag();
    persisted.put(TestShips.MARKER, tag);
    session.player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
  }

  private void reject(String command, net.minecraft.commands.CommandSourceStack source) {
    try {
      level.getServer().getCommands().getDispatcher().execute(command, source);
      throw new IllegalStateException("Invalid source accepted");
    } catch (CommandSyntaxException expected) {
      /* Native Brigadier permissions/player requirement. */
    }
  }

  private void stop(ServerStoppingEvent event) {
    if (event.getServer() == level.getServer() && session != null) {
      session.close();
      session = null;
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
