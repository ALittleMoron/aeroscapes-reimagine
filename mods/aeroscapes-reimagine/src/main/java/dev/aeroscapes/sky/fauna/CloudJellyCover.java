package dev.aeroscapes.sky.fauna;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

final class CloudJellyCover {
  private static final Direction[] DIRECTIONS = {
    Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN
  };
  private static final int CABIN_SEARCH_BUDGET = 512;
  private static final WeakHashMap<LivingEntity, CabinSample> CABINS = new WeakHashMap<>();

  private CloudJellyCover() {}

  static AABB worldBounds(LivingEntity victim) {
    var plot = Sable.HELPER.getContaining(victim);
    return plot == null
        ? victim.getBoundingBox()
        : new BoundingBox3d(victim.getBoundingBox()).transform(plot.logicalPose()).toMojang();
  }

  static boolean exposed(
      LivingEntity victim, double time, Vec3 core, Vec3 root, Vec3 skin, Vec3 center) {
    Level level = victim.level();
    SubLevel ship = null;
    if (time < 1) {
      ship = Sable.HELPER.getTrackingOrVehicleSubLevel(victim);
      if (ship == null) ship = Sable.HELPER.getContaining(victim);
    }
    // The rider's hull must appear once, at the pose it had at contact. Other hulls and terrain
    // remain in Sable's normal world clip.
    if (blocked(level, victim, core, root, ship)
        || blocked(level, victim, root, skin, ship)
        || blocked(level, victim, skin, center, ship)
        || inCabin(victim, center, ship, time == 1)) return false;
    if (ship == null) return true;
    var hitPose = ship.lastPose().lerp(ship.logicalPose(), time, new Pose3d());
    var plot = ship.getPlot();
    CollisionContext context = CollisionContext.of(victim);
    Vec3 localCore = hitPose.transformPositionInverse(core);
    Vec3 localRoot = hitPose.transformPositionInverse(root);
    Vec3 localSkin = hitPose.transformPositionInverse(skin);
    Vec3 localCenter = hitPose.transformPositionInverse(center);
    return !enclosed(plot, localCenter, context)
        && !plotBlocked(plot, context, localCore, localRoot)
        && !plotBlocked(plot, context, localRoot, localSkin)
        && !plotBlocked(plot, context, localSkin, localCenter);
  }

  private static boolean blocked(
      Level level, LivingEntity victim, Vec3 from, Vec3 to, @Nullable SubLevel ignored) {
    if (from.distanceToSqr(to) < 1.0e-10) return false;
    AABB path = new AABB(from, to);
    for (int x = SectionPos.blockToSectionCoord(Mth.floor(path.minX));
        x <= SectionPos.blockToSectionCoord(Mth.floor(path.maxX));
        x++) {
      for (int z = SectionPos.blockToSectionCoord(Mth.floor(path.minZ));
          z <= SectionPos.blockToSectionCoord(Mth.floor(path.maxZ));
          z++) {
        if (level.getChunkSource().getChunkNow(x, z) == null) return true;
      }
    }
    var clip =
        new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim);
    if (ignored != null) ((ClipContextExtension) clip).sable$setIgnoredSubLevel(ignored);
    return level.clip(clip).getType() != HitResult.Type.MISS;
  }

  private static boolean inCabin(
      LivingEntity victim, Vec3 center, @Nullable SubLevel ignored, boolean current) {
    long now = victim.level().getGameTime();
    var cached = CABINS.get(victim);
    if (current && cached != null && cached.tick == now && cached.center.equals(center))
      return cached.enclosed;
    boolean enclosed = false;
    var point = new BoundingBox3d(new AABB(center, center).inflate(0.001));
    for (var ship : Sable.HELPER.getAllIntersecting(victim.level(), point)) {
      if (ship != ignored
          && enclosed(
              ship.getPlot(),
              ship.logicalPose().transformPositionInverse(center),
              CollisionContext.of(victim))) {
        enclosed = true;
        break;
      }
    }
    if (current) CABINS.put(victim, new CabinSample(now, center, enclosed));
    return enclosed;
  }

  private static boolean enclosed(LevelPlot plot, Vec3 start, CollisionContext context) {
    var bounds = plot.getBoundingBox();
    BlockPos cell = BlockPos.containing(start);
    if (bounds == null || !bounds.contains(cell.getX(), cell.getY(), cell.getZ())) return false;
    for (var direction : DIRECTIONS) {
      double edge =
          switch (direction) {
            case UP -> bounds.maxY() + 1.5 - start.y;
            case DOWN -> start.y - bounds.minY() + 0.5;
            case EAST -> bounds.maxX() + 1.5 - start.x;
            case WEST -> start.x - bounds.minX() + 0.5;
            case SOUTH -> bounds.maxZ() + 1.5 - start.z;
            case NORTH -> start.z - bounds.minZ() + 0.5;
          };
      if (!plotBlocked(plot, context, start, start.relative(direction, edge))) return false;
    }

    // A doorway can be off axis. Probe connected air inside the actual plot, retaining shelter
    // in a very large compartment when all six straight routes are blocked and the budget is spent.
    var open = new ArrayDeque<BlockPos>();
    var visited = new HashSet<BlockPos>();
    open.add(cell);
    visited.add(cell);
    Vec3 fraction = start.subtract(Vec3.atLowerCornerOf(cell));
    while (!open.isEmpty() && visited.size() <= CABIN_SEARCH_BUDGET) {
      BlockPos here = open.removeFirst();
      Vec3 from = Vec3.atLowerCornerOf(here).add(fraction);
      for (var direction : DIRECTIONS) {
        BlockPos next = here.relative(direction);
        if (visited.contains(next) || plotBlocked(plot, context, from, from.relative(direction, 1)))
          continue;
        if (!bounds.contains(next.getX(), next.getY(), next.getZ())) return false;
        visited.add(next);
        open.addLast(next);
      }
    }
    return true;
  }

  private static boolean plotBlocked(LevelPlot plot, CollisionContext context, Vec3 from, Vec3 to) {
    return BlockGetter.traverseBlocks(
        from,
        to,
        context,
        (collision, pos) -> {
          var chunk = plot.getChunk(plot.toLocal(new ChunkPos(pos)));
          if (chunk == null) return null;
          var shape =
              chunk
                  .getBlockState(pos)
                  .getCollisionShape(plot.getSubLevel().getLevel(), pos, collision);
          return !shape.isEmpty() && shape.clip(from, to, pos) != null ? Boolean.TRUE : null;
        },
        ignored -> false);
  }

  private record CabinSample(long tick, Vec3 center, boolean enclosed) {}
}
