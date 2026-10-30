package dev.aeroscapes.testship;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Explicit administrative creation/boarding/removal; native blocks alone control flight. */
public final class TestShips {
  public static final String MARKER = "aeroscapes_reimagine:test_ship";
  private static final DynamicCommandExceptionType ERROR =
      new DynamicCommandExceptionType(value -> (Component) value);

  private TestShips() {}

  public static ServerSubLevel spawn(ServerPlayer player) throws CommandSyntaxException {
    ServerLevel level = player.serverLevel();
    require(level.dimension() == Level.OVERWORLD, "overworld");
    clearRemovedReference(player);
    require(!reference(player).hasUUID("ship"), "exists");
    require(safeStanding(level, player, player.position()), "stand");
    Direction direction = player.getDirection();
    BlockPos anchor =
        player.blockPosition().offset(direction.getStepX() * 20, 24, direction.getStepZ() * 20);
    AABB clearance =
        new AABB(
            anchor.getX() - 7,
            anchor.getY() - 3,
            anchor.getZ() - 13,
            anchor.getX() + 8,
            anchor.getY() + 15,
            anchor.getZ() + 11);
    preflight(level, clearance);
    TestAirshipBlueprint blueprint;
    try {
      blueprint = TestAirshipBlueprint.load(level);
    } catch (IOException failure) {
      LogUtils.getLogger().error("Cannot load downloaded test-airship schematic", failure);
      throw ERROR.create(
          Component.translatable("message.aeroscapes_reimagine.test_ship.schematic"));
    }
    var blocks = blueprint.blocks();
    var container = SubLevelContainer.getContainer(level);
    require(container != null, "unavailable");
    Pose3d pose = new Pose3d();
    pose.position().set(anchor.getX(), anchor.getY(), anchor.getZ());
    ServerSubLevel ship = (ServerSubLevel) container.allocateNewSubLevel(pose);
    Vec3 home = player.position();
    var allocated = new HashSet<UUID>();
    allocated.add(ship.getUniqueId());
    try {
      CompoundTag tag = new CompoundTag(), marker = new CompoundTag();
      marker.putInt("schema", 2);
      marker.putUUID("owner", player.getUUID());
      marker.putUUID("ship", ship.getUniqueId());
      tag.put(MARKER, marker);
      ship.setUserDataTag(tag);
      BlockPos local = ship.getPlot().getCenterBlock();
      var chunks = new HashSet<ChunkPos>();
      for (BlockPos position : blocks.keySet()) chunks.add(new ChunkPos(local.offset(position)));
      for (ChunkPos chunk : chunks) ship.getPlot().newEmptyChunk(chunk);
      blueprint.place(ship);
      Vector3d center =
          new Vector3d(ship.getMassTracker().getCenterOfMass())
              .sub(local.getX(), local.getY(), local.getZ())
              .add(anchor.getX(), anchor.getY(), anchor.getZ());
      ship.logicalPose().position().set(center);
      container.physicsSystem().getPipeline().teleport(ship, center, pose.orientation());
      ship.updateLastPose();
      ship.updateBoundingBox();
      ship.forceUpdateGlobalBounds();
      TestAirshipBlueprint.initialize(ship, allocated);
      CompoundTag saved = new CompoundTag();
      saved.putUUID("ship", ship.getUniqueId());
      saved.putDouble("homeX", home.x);
      saved.putDouble("homeY", home.y);
      saved.putDouble("homeZ", home.z);
      saveReference(player, saved);
      board(player);
      return ship;
    } catch (RuntimeException | CommandSyntaxException failure) {
      try {
        player.stopRiding();
        if (safeStanding(level, player, home))
          player.teleportTo(level, home.x, home.y, home.z, player.getYRot(), player.getXRot());
        for (UUID id : allocated)
          if (!id.equals(ship.getUniqueId())
              && container.getSubLevel(id) instanceof ServerSubLevel created)
            container.removeSubLevel(created, SubLevelRemovalReason.REMOVED);
        container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
        saveReference(player, new CompoundTag());
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      if (failure instanceof CommandSyntaxException command) throw command;
      LogUtils.getLogger()
          .error("Cannot create native test airship {}", ship.getUniqueId(), failure);
      throw ERROR.create(
          Component.translatable(
              "message.aeroscapes_reimagine.test_ship.failed", ship.getUniqueId().toString()));
    }
  }

  public static void board(ServerPlayer player) throws CommandSyntaxException {
    ServerSubLevel ship = ship(player);
    family(ship);
    ((TestShipContainer) SubLevelContainer.getContainer(player.serverLevel()))
        .aeroscapes$testShips()
        .boarding()
        .request(player, ship);
  }

  static void finishBoard(ServerPlayer player) throws CommandSyntaxException {
    ServerSubLevel ship = ship(player);
    BlockPos anchor = ship.getPlot().getCenterBlock();
    if (ship.getUserDataTag().getCompound(MARKER).getInt("schema") == 2)
      require(
          player.serverLevel().getBlockState(anchor.offset(TestAirshipBlueprint.SEAT)).getBlock()
              instanceof SeatBlock,
          "seat");
    boolean legacy = ship.getUserDataTag().getCompound(MARKER).getInt("schema") == 1;
    Vec3 localPosition =
        legacy
            ? new Vec3(anchor.getX() - 1.5, anchor.getY() + 1.55, anchor.getZ() + 1.5)
            : Vec3.atCenterOf(anchor.offset(TestAirshipBlueprint.SEAT)).add(0, 0.5, 0);
    Vec3 position = ship.logicalPose().transformPosition(localPosition);
    require(safeDestination(player.serverLevel(), player, position), "blocked");
    BlockPos seatPosition = anchor.offset(TestAirshipBlueprint.SEAT);
    var seats = player.serverLevel().getEntitiesOfClass(SeatEntity.class, new AABB(seatPosition));
    if (!legacy) for (SeatEntity seat : seats) require(seat.getPassengers().isEmpty(), "occupied");
    Vec3 previous = player.position();
    float previousYaw = player.getYRot(), previousPitch = player.getXRot();
    try {
      player.stopRiding();
      player.teleportTo(player.serverLevel(), position.x, position.y, position.z, 0, 15);
      if (legacy) return;
      if (!seats.isEmpty()) require(player.startRiding(seats.getFirst(), true), "blocked");
      else {
        SeatBlock.sitDown(player.serverLevel(), seatPosition, player);
        require(player.isPassenger(), "blocked");
      }
    } catch (RuntimeException | CommandSyntaxException failure) {
      player.stopRiding();
      if (safeDestination(player.serverLevel(), player, previous))
        player.teleportTo(
            player.serverLevel(), previous.x, previous.y, previous.z, previousYaw, previousPitch);
      throw failure;
    }
  }

  public static void leave(ServerPlayer player) throws CommandSyntaxException {
    ((TestShipContainer) SubLevelContainer.getContainer(player.serverLevel()))
        .aeroscapes$testShips()
        .boarding()
        .cancel(player);
    require(player.serverLevel().dimension() == Level.OVERWORLD, "overworld");
    CompoundTag saved = reference(player);
    require(
        saved.hasUUID("ship")
            && saved.contains("homeX", CompoundTag.TAG_DOUBLE)
            && saved.contains("homeY", CompoundTag.TAG_DOUBLE)
            && saved.contains("homeZ", CompoundTag.TAG_DOUBLE),
        "missing");
    Vec3 position =
        new Vec3(saved.getDouble("homeX"), saved.getDouble("homeY"), saved.getDouble("homeZ"));
    require(safeStanding(player.serverLevel(), player, position), "home");
    stopControls(player);
    player.stopRiding();
    player.teleportTo(
        player.serverLevel(),
        position.x,
        position.y,
        position.z,
        player.getYRot(),
        player.getXRot());
  }

  private static void stopControls(ServerPlayer player) {
    CompoundTag saved = reference(player);
    if (!saved.hasUUID("ship")) return;
    var candidate =
        SubLevelContainer.getContainer(player.serverLevel()).getSubLevel(saved.getUUID("ship"));
    if (candidate instanceof ServerSubLevel ship && isFixture(ship)) {
      CompoundTag marker = ship.getUserDataTag().getCompound(MARKER);
      if (marker.getInt("schema") == 2 && player.getUUID().equals(marker.getUUID("owner")))
        TestAirshipBlueprint.stop(ship);
    }
  }

  public static boolean isFixture(ServerSubLevel ship) {
    CompoundTag data = ship.getUserDataTag();
    if (data == null) return false;
    CompoundTag marker = data.getCompound(MARKER);
    return marker.getInt("schema") == 2
        && marker.hasUUID("owner")
        && marker.hasUUID("ship")
        && ship.getUniqueId().equals(marker.getUUID("ship"));
  }

  public static void ownChild(ServerSubLevel parent, ServerSubLevel child) {
    CompoundTag relation = parent.getUserDataTag().getCompound(MARKER);
    UUID rootId = relation.hasUUID("root") ? relation.getUUID("root") : parent.getUniqueId();
    var container = SubLevelContainer.getContainer(parent.getLevel());
    if (!(container.getSubLevel(rootId) instanceof ServerSubLevel root) || !isFixture(root))
      throw new IllegalStateException("Owned root is unavailable");
    CompoundTag rootTag = root.getUserDataTag(), rootMarker = rootTag.getCompound(MARKER);
    if (!rootMarker.getUUID("owner").equals(relation.getUUID("owner")))
      throw new IllegalStateException("Native child owner mismatch");
    CompoundTag childTag =
        child.getUserDataTag() == null ? new CompoundTag() : child.getUserDataTag().copy();
    CompoundTag previous = childTag.getCompound(MARKER);
    if (!previous.isEmpty())
      throw new IllegalStateException("Refuse to claim an existing fixture child");
    CompoundTag marker = new CompoundTag();
    marker.putInt("schema", 2);
    marker.putUUID("owner", rootMarker.getUUID("owner"));
    marker.putUUID("ship", child.getUniqueId());
    marker.putUUID("parent", parent.getUniqueId());
    marker.putUUID("root", rootId);
    childTag.put(MARKER, marker);
    child.setUserDataTag(childTag);
    var children = rootMarker.getList("children", net.minecraft.nbt.Tag.TAG_INT_ARRAY);
    children.add(net.minecraft.nbt.NbtUtils.createUUID(child.getUniqueId()));
    rootMarker.put("children", children);
    var bearing =
        root.getLevel()
            .getBlockEntity(root.getPlot().getCenterBlock().offset(TestAirshipBlueprint.BEARING));
    if (bearing
            instanceof
            dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity
                steering
        && child.getUniqueId().equals(steering.getSubLevelID()))
      rootMarker.putUUID("steering", child.getUniqueId());
    root.setUserDataTag(rootTag);
  }

  static List<ServerSubLevel> family(ServerSubLevel root) throws CommandSyntaxException {
    CompoundTag marker = root.getUserDataTag().getCompound(MARKER);
    var members = new java.util.ArrayList<ServerSubLevel>();
    var identifiers = new java.util.HashSet<UUID>();
    for (var value : marker.getList("children", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
      UUID id = net.minecraft.nbt.NbtUtils.loadUUID(value);
      require(identifiers.add(id) && !root.getUniqueId().equals(id), "owner");
      var found = SubLevelContainer.getContainer(root.getLevel()).getSubLevel(id);
      require(found instanceof ServerSubLevel, "unloaded");
      ServerSubLevel child = (ServerSubLevel) found;
      require(isFixture(child), "owner");
      CompoundTag related = child.getUserDataTag().getCompound(MARKER);
      require(
          isFixture(child)
              && related.hasUUID("root")
              && root.getUniqueId().equals(related.getUUID("root"))
              && marker.getUUID("owner").equals(related.getUUID("owner")),
          "owner");
      members.add(child);
    }
    java.util.Collections.reverse(members);
    members.add(root);
    return members;
  }

  public static void remove(ServerPlayer player) throws CommandSyntaxException {
    ((TestShipContainer) SubLevelContainer.getContainer(player.serverLevel()))
        .aeroscapes$testShips()
        .boarding()
        .cancel(player);
    ServerSubLevel ship = ship(player);
    var members = family(ship);
    for (ServerSubLevel member : members)
      for (ServerPlayer other : player.serverLevel().players())
        require(other == player || !near(other, member), "occupied");
    if (members.stream().anyMatch(member -> near(player, member))) leave(player);
    else stopControls(player);
    for (ServerSubLevel member : members)
      SubLevelContainer.getContainer(player.serverLevel())
          .removeSubLevel(member, SubLevelRemovalReason.REMOVED);
    saveReference(player, new CompoundTag());
  }

  public static ServerSubLevel ship(ServerPlayer player) throws CommandSyntaxException {
    require(player.serverLevel().dimension() == Level.OVERWORLD, "overworld");
    clearRemovedReference(player);
    CompoundTag saved = reference(player);
    require(saved.hasUUID("ship"), "missing");
    var found =
        SubLevelContainer.getContainer(player.serverLevel()).getSubLevel(saved.getUUID("ship"));
    require(found instanceof ServerSubLevel, "unloaded");
    ServerSubLevel ship = (ServerSubLevel) found;
    CompoundTag data = ship.getUserDataTag();
    require(data != null && data.contains(MARKER, CompoundTag.TAG_COMPOUND), "owner");
    CompoundTag marker = data.getCompound(MARKER);
    require(
        (marker.getInt("schema") == 1 || marker.getInt("schema") == 2)
            && marker.hasUUID("ship")
            && marker.getUUID("ship").equals(ship.getUniqueId())
            && marker.hasUUID("owner")
            && marker.getUUID("owner").equals(player.getUUID()),
        "owner");
    return ship;
  }

  public static void removed(ServerSubLevel ship) {
    CompoundTag data = ship.getUserDataTag();
    if (data == null) return;
    CompoundTag marker = data.getCompound(MARKER);
    if ((marker.getInt("schema") != 1 && marker.getInt("schema") != 2)
        || !marker.hasUUID("owner")
        || !marker.hasUUID("ship")
        || !ship.getUniqueId().equals(marker.getUUID("ship"))) return;
    if (marker.hasUUID("parent")) {
      UUID rootId = marker.hasUUID("root") ? marker.getUUID("root") : marker.getUUID("parent");
      var found = SubLevelContainer.getContainer(ship.getLevel()).getSubLevel(rootId);
      if (found instanceof ServerSubLevel root && isFixture(root)) {
        CompoundTag rootTag = root.getUserDataTag(), record = rootTag.getCompound(MARKER);
        if (marker.getUUID("owner").equals(record.getUUID("owner"))) {
          if (record.hasUUID("steering") && ship.getUniqueId().equals(record.getUUID("steering")))
            record.remove("steering");
          var children = record.getList("children", net.minecraft.nbt.Tag.TAG_INT_ARRAY);
          children.removeIf(
              value -> net.minecraft.nbt.NbtUtils.loadUUID(value).equals(ship.getUniqueId()));
          record.put("children", children);
          root.setUserDataTag(rootTag);
        }
      }
      return;
    }
    var server = ship.getLevel().getServer();
    ServerPlayer player = server.getPlayerList().getPlayer(marker.getUUID("owner"));
    if (player != null) {
      if (reference(player).hasUUID("ship")
          && reference(player).getUUID("ship").equals(ship.getUniqueId()))
        saveReference(player, new CompoundTag());
    } else RemovedTestShips.get(server).record(marker.getUUID("owner"), ship.getUniqueId());
  }

  private static void clearRemovedReference(ServerPlayer player) {
    CompoundTag saved = reference(player);
    if (saved.hasUUID("ship")
        && RemovedTestShips.get(player.serverLevel().getServer())
            .consume(player.getUUID(), saved.getUUID("ship")))
      saveReference(player, new CompoundTag());
  }

  public static void copyReference(Player original, Player replacement) {
    if (reference(original).isEmpty()) return;
    CompoundTag persisted = replacement.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    persisted.put(MARKER, reference(original).copy());
    replacement.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
  }

  private static void preflight(ServerLevel level, AABB volume) throws CommandSyntaxException {
    require(
        volume.minY >= level.getMinBuildHeight()
            && volume.maxY < level.getMaxBuildHeight()
            && level.getWorldBorder().isWithinBounds(volume),
        "bounds");
    for (int x = ((int) Math.floor(volume.minX)) >> 4;
        x <= ((int) Math.floor(volume.maxX)) >> 4;
        x++)
      for (int z = ((int) Math.floor(volume.minZ)) >> 4;
          z <= ((int) Math.floor(volume.maxZ)) >> 4;
          z++) require(level.getChunkSource().getChunkNow(x, z) != null, "chunks");
    for (BlockPos position :
        BlockPos.betweenClosed(
            BlockPos.containing(volume.minX, volume.minY, volume.minZ),
            BlockPos.containing(volume.maxX, volume.maxY, volume.maxZ))) {
      var state = level.getBlockState(position);
      require(
          (state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR) || state.is(Blocks.VOID_AIR))
              && state.getFluidState().isEmpty()
              && level.getBlockEntity(position) == null,
          "blocked");
    }
    for (ServerSubLevel other : SubLevelContainer.getContainer(level).getAllSubLevels())
      require(!other.boundingBox().intersects(volume), "blocked");
    require(level.getEntities(null, volume).isEmpty(), "occupied");
  }

  private static boolean near(ServerPlayer player, ServerSubLevel ship) {
    var plot = ship.getPlot().getBoundingBox();
    return ship.boundingBox().toMojang().inflate(3).contains(player.position())
        || new AABB(
                plot.minX() - 2,
                plot.minY() - 2,
                plot.minZ() - 2,
                plot.maxX() + 3,
                plot.maxY() + 3,
                plot.maxZ() + 3)
            .contains(player.position());
  }

  private static boolean safeDestination(ServerLevel level, ServerPlayer player, Vec3 position) {
    if (!Double.isFinite(position.x)
        || !Double.isFinite(position.y)
        || !Double.isFinite(position.z)) return false;
    AABB box = player.getBoundingBox().move(position.subtract(player.position()));
    if (box.minY < level.getMinBuildHeight()
        || box.maxY >= level.getMaxBuildHeight()
        || !level.getWorldBorder().isWithinBounds(box)) return false;
    for (int x = ((int) Math.floor(box.minX)) >> 4; x <= ((int) Math.floor(box.maxX)) >> 4; x++)
      for (int z = ((int) Math.floor(box.minZ)) >> 4; z <= ((int) Math.floor(box.maxZ)) >> 4; z++)
        if (level.getChunkSource().getChunkNow(x, z) == null) return false;
    return level.noCollision(player, box)
        && CanFallAtleastHelper.canFallAtleastWithSubLevels(level, box) == null;
  }

  private static boolean safeStanding(ServerLevel level, ServerPlayer player, Vec3 position) {
    if (!safeDestination(level, player, position)) return false;
    BlockPos support = BlockPos.containing(position.add(0, -0.05, 0));
    return level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
        && level.getFluidState(support.above()).isEmpty();
  }

  private static CompoundTag reference(Player player) {
    return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(MARKER);
  }

  private static void saveReference(Player player, CompoundTag value) {
    CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    persisted.put(MARKER, value);
    player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
  }

  private static void require(boolean condition, String key) throws CommandSyntaxException {
    if (!condition)
      throw ERROR.create(Component.translatable("message.aeroscapes_reimagine.test_ship." + key));
  }
}
