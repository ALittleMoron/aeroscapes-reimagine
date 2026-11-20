package dev.aeroscapes.sky.fauna;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A bounded neighborhood of terrain for the crown and outstretched, turning strands. */
final class CloudJellyNavigation {
  private AABB surveyed;
  private long refresh;
  private final List<AABB> solids = new ArrayList<>();

  Vec3 avoid(CloudJelly jelly, Vec3 velocity) {
    long now = jelly.level().getGameTime();
    AABB body = jelly.clearanceBounds();
    AABB look = body.expandTowards(velocity.scale(16)).inflate(1.0);
    if (surveyed == null || now >= refresh || !contains(surveyed, look)) {
      surveyed = look.inflate(2);
      refresh = now + 12;
      solids.clear();
      // Loaded terrain only. Coalesce excess cells in severely obstructed or command-spawned sites.
      AABB excess = null;
      for (var shape : jelly.level().getBlockCollisions(jelly, surveyed)) {
        var block = shape.bounds();
        if (solids.size() < 128) solids.add(block);
        else excess = excess == null ? block : excess.minmax(block);
      }
      if (excess != null) solids.add(excess);
    }
    Vec3 normal = Vec3.ZERO;
    double nearest = Double.POSITIVE_INFINITY;
    for (AABB block : solids) {
      if (!look.intersects(block)) continue;
      var face = nearestFace(body, block);
      if (face.gap < nearest) {
        normal = face.out;
        nearest = face.gap;
      }
    }
    if (normal == Vec3.ZERO && jelly.horizontalCollision) {
      Vec3 direction = jelly.swimFrame(now).current;
      normal = new Vec3(-direction.x, 0, -direction.z).normalize();
    }
    if (normal != Vec3.ZERO) {
      jelly.turnAway(normal, now);
      velocity = velocity.add(normal.scale(.032));
    }
    // Rotation alone can swing a giant's tip into a wall beyond the native upright movement box.
    // Resolve that change at the same tick as the visible pose.
    for (AABB block : solids) {
      AABB moved = body.move(velocity);
      if (!moved.intersects(block)) continue;
      var face = nearestFace(moved, block);
      velocity = velocity.add(face.out.scale(Math.min(.65, .002 - face.gap)));
    }
    return velocity;
  }

  private static boolean contains(AABB outer, AABB inner) {
    return outer.minX <= inner.minX
        && outer.minY <= inner.minY
        && outer.minZ <= inner.minZ
        && outer.maxX >= inner.maxX
        && outer.maxY >= inner.maxY
        && outer.maxZ >= inner.maxZ;
  }

  private record Face(double gap, Vec3 out) {}

  private static Face nearestFace(AABB body, AABB obstacle) {
    double best = obstacle.minX - body.maxX;
    Vec3 out = new Vec3(-1, 0, 0);
    double gap = body.minX - obstacle.maxX;
    if (gap > best) {
      best = gap;
      out = new Vec3(1, 0, 0);
    }
    gap = obstacle.minZ - body.maxZ;
    if (gap > best) {
      best = gap;
      out = new Vec3(0, 0, -1);
    }
    gap = body.minZ - obstacle.maxZ;
    if (gap > best) {
      best = gap;
      out = new Vec3(0, 0, 1);
    }
    gap = obstacle.minY - body.maxY;
    if (gap > best) {
      best = gap;
      out = new Vec3(0, -1, 0);
    }
    gap = body.minY - obstacle.maxY;
    if (gap > best) {
      best = gap;
      out = new Vec3(0, 1, 0);
    }
    return new Face(best, out);
  }
}
