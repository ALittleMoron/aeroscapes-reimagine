package dev.aeroscapes.equipment.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Limits ground-level footwork to a continuous supported path. */
public final class GroundFootwork {
  private static final double PROBE_SPACING = 0.25;
  private static final int[] SPEED = {2, 3, 4, 4, 3, 2, 1};
  public static final int DURATION = SPEED.length;

  private GroundFootwork() {}

  public static Vec3 motion(Vec3 travel, int tick) {
    return travel.scale(SPEED[tick] / 19.0);
  }

  public static boolean available(Player player) {
    return player.isAlive()
        && !player.isSpectator()
        && !player.isSleeping()
        && player.onGround()
        && !player.isPassenger()
        && !player.isUsingItem()
        && !player.getAbilities().flying
        && !player.isFallFlying()
        && !player.onClimbable()
        && !player.isInWaterOrBubble()
        && !player.isInLava();
  }

  public static Vec3 supportedTravel(Player player, Vec3 travel) {
    if (travel.lengthSqr() < 1.0E-10) return Vec3.ZERO;
    int probes = (int) Math.ceil(travel.length() / PROBE_SPACING);
    Vec3 stride = travel.scale(1.0 / probes);
    var level = player.level();
    AABB start = player.getBoundingBox();
    var entities = level.getEntityCollisions(player, start.expandTowards(travel));
    int supported = 0;
    while (supported < probes) {
      AABB current = start.move(stride.scale(supported));
      AABB next = current.move(stride);
      AABB ground =
          new AABB(next.minX, next.minY - 0.4, next.minZ, next.maxX, next.minY, next.maxZ);
      if (Entity.collideBoundingBox(player, stride, current, level, entities).distanceToSqr(stride)
              > 1.0E-10
          || !level.getBlockCollisions(player, ground).iterator().hasNext()) break;
      supported++;
    }
    return stride.scale(supported);
  }
}
