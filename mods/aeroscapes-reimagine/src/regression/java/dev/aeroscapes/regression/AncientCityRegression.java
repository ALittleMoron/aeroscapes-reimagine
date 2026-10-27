package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** A naturally selected city must provide connected rooms, ancient loot, and Warden growth. */
final class AncientCityRegression {
  private AncientCityRegression() {}

  static void verify(ServerLevel level) {
    var structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.withDefaultNamespace("ancient_city"));
    StructureStart start = level.getChunk(-175, -55).getStartForStructure(structure);
    require(start != null && start.isValid(), "Missing natural ancient city");
    int chunks = 0;
    Set<Long> loaded = new HashSet<>();
    boolean sensor = false;
    boolean shrieker = false;
    for (StructurePiece piece : start.getPieces()) {
      BoundingBox box = piece.getBoundingBox();
      require(
          box.minY() >= level.getMinBuildHeight() + 4 && box.maxY() < level.getMaxBuildHeight(),
          "Clipped ancient-city room");
      for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
        for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
          if (loaded.add(net.minecraft.world.level.ChunkPos.asLong(x, z))) {
            level.getChunk(x, z);
            chunks++;
          }
        }
      }
      // Native sculk-patch rare growths remain within two blocks of the feature origin.
      BoundingBox scan = box.inflatedBy(2);
      for (BlockPos position :
          BlockPos.betweenClosed(
              scan.minX(), scan.minY(), scan.minZ(), scan.maxX(), scan.maxY(), scan.maxZ())) {
        if (!level.hasChunk(position.getX() >> 4, position.getZ() >> 4)) continue;
        var state = level.getBlockState(position);
        sensor |= state.is(Blocks.SCULK_SENSOR);
        shrieker |=
            state.is(Blocks.SCULK_SHRIEKER) && state.getValue(SculkShriekerBlock.CAN_SUMMON);
      }
    }
    require(sensor, "Ancient-city core lacks an actual sculk sensor");
    require(shrieker, "Ancient city has no naturally generated CAN_SUMMON shrieker");
    require(connectedRooms(level, start), "Ancient-city rooms or intended loot are inaccessible");
    LogUtils.getLogger()
        .info(
            "Vanilla ancient-city rooms, loot and CAN_SUMMON growth passed across {} chunks",
            chunks);
  }

  private record Footing(int x, double y, int z) {
    BlockPos position() {
      return BlockPos.containing(x + 0.5, y, z + 0.5);
    }

    AABB playerBox() {
      return EntityType.PLAYER.getDimensions().makeBoundingBox(x + 0.5, y, z + 0.5);
    }
  }

  private static List<Footing> footings(ServerLevel level, StructureStart start, int x, int z) {
    List<Footing> result = new ArrayList<>();
    if (!level.hasChunk(x >> 4, z >> 4)) return result;
    BoundingBox city = start.getBoundingBox();
    Set<Double> heights = new HashSet<>();
    for (int y = city.minY() - 2; y <= city.maxY(); y++) {
      BlockPos support = new BlockPos(x, y, z);
      var shape = level.getBlockState(support).getCollisionShape(level, support);
      for (AABB surface : shape.toAabbs()) {
        double feetY = y + surface.maxY;
        if (!heights.add(feetY)) continue;
        Footing footing = new Footing(x, feetY, z);
        if (start.getPieces().stream()
            .noneMatch(piece -> piece.getBoundingBox().isInside(footing.position()))) continue;
        AABB player = footing.playerBox();
        // Use real fractional carpet/slab/stair heights and the native player box.
        if (level.noCollision(player)
            && !level.containsAnyLiquid(player)
            && !level.noCollision(player.move(0, -0.0001, 0))) result.add(footing);
      }
    }
    return result;
  }

  private static boolean traversable(ServerLevel level, Footing current, Footing next) {
    double rise = next.y() - current.y();
    if (rise > 1.0 || rise < -3.0) return false;
    double travelY = Math.max(current.y(), next.y());
    AABB lifted = current.playerBox().move(0, travelY - current.y(), 0);
    return level.noCollision(current.playerBox().expandTowards(0, travelY - current.y(), 0))
        && level.noCollision(
            lifted.expandTowards(next.x() - current.x(), 0, next.z() - current.z()))
        && level.noCollision(next.playerBox().expandTowards(0, travelY - next.y(), 0));
  }

  private static boolean ancientLoot(ServerLevel level, Footing player, BlockPos position) {
    if (!level.hasChunk(position.getX() >> 4, position.getZ() >> 4)) return false;
    if (!(level.getBlockEntity(position) instanceof RandomizableContainerBlockEntity container))
      return false;
    var table = container.getLootTable();
    return table != null
        && table.location().equals(ResourceLocation.withDefaultNamespace("chests/ancient_city"))
        && !level.getBlockState(position.above()).isRedstoneConductor(level, position.above())
        && level
            .clip(
                new ClipContext(
                    new Vec3(
                        player.x() + 0.5,
                        player.y() + EntityType.PLAYER.getDimensions().eyeHeight(),
                        player.z() + 0.5),
                    Vec3.atCenterOf(position),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()))
            .getBlockPos()
            .equals(position);
  }

  private static boolean connectedRooms(ServerLevel level, StructureStart start) {
    BoundingBox root = start.getPieces().getFirst().getBoundingBox();
    Set<Footing> explored = new HashSet<>();
    Map<Long, List<Footing>> columns = new HashMap<>();
    for (int x = root.minX(); x <= root.maxX(); x++) {
      for (int z = root.minZ(); z <= root.maxZ(); z++) {
        int columnX = x;
        int columnZ = z;
        var candidates =
            columns.computeIfAbsent(
                ChunkPos.asLong(x, z), ignored -> footings(level, start, columnX, columnZ));
        for (Footing candidate : candidates) {
          if (!explored.contains(candidate)
              && root.isInside(candidate.position())
              && route(level, start, candidate, explored, columns)) return true;
        }
      }
    }
    return false;
  }

  private static boolean route(
      ServerLevel level,
      StructureStart start,
      Footing origin,
      Set<Footing> explored,
      Map<Long, List<Footing>> columns) {
    ArrayDeque<Footing> queue = new ArrayDeque<>();
    queue.add(origin);
    explored.add(origin);
    boolean entrance = false;
    boolean chamber = false;
    boolean loot = false;
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    var entrances =
        start.getPieces().stream()
            .filter(
                piece ->
                    piece
                        .createTag(serialization)
                        .getCompound("pool_element")
                        .getString("location")
                        .contains("ancient_city/city/entrance/"))
            .map(StructurePiece::getBoundingBox)
            .toList();
    var chambers =
        start.getPieces().stream()
            .filter(
                piece ->
                    piece
                        .createTag(serialization)
                        .getCompound("pool_element")
                        .getString("location")
                        .contains("ancient_city/structures/chamber_"))
            .map(StructurePiece::getBoundingBox)
            .toList();
    while (!queue.isEmpty() && explored.size() < 100_000) {
      Footing current = queue.removeFirst();
      BlockPos position = current.position();
      entrance |= entrances.stream().anyMatch(box -> box.isInside(position));
      chamber |= chambers.stream().anyMatch(box -> box.isInside(position));
      for (Direction direction : Direction.Plane.HORIZONTAL) {
        BlockPos adjacent = position.relative(direction);
        loot |=
            ancientLoot(level, current, adjacent) || ancientLoot(level, current, adjacent.above());
        for (Footing next :
            columns.computeIfAbsent(
                ChunkPos.asLong(adjacent.getX(), adjacent.getZ()),
                ignored -> footings(level, start, adjacent.getX(), adjacent.getZ()))) {
          if (!explored.contains(next) && traversable(level, current, next)) {
            explored.add(next);
            queue.addLast(next);
          }
        }
      }
      if (entrance && chamber && loot) {
        LogUtils.getLogger()
            .info("Ancient-city native-player walkspace passed: {} positions", explored.size());
        return true;
      }
    }
    LogUtils.getLogger()
        .info(
            "Ancient-city component: root={}, entrance={}, chamber={}, loot={}, explored={}",
            origin,
            entrance,
            chamber,
            loot,
            explored.size());
    return false;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
