package dev.aeroscapes.regression;

import dev.aeroscapes.sky.fauna.AeroFauna;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Follows a living ray through a complete patrol cycle in the encounter fixture's loaded air. */
final class SkyRayFlightRegression {
  private SkyRayFlightRegression() {}

  static void verify(ServerLevel level, Vec3 observer) {
    var ray = Objects.requireNonNull(AeroFauna.SKY_RAY.get().create(level));
    ray.moveTo(observer.x, observer.y + 14, observer.z, 0, 0);
    ray.getRandom().setSeed(4269);
    ray.setPersistenceRequired();
    if (!level.addFreshEntity(ray)) throw new IllegalStateException("Cannot add flight test ray");
    int poweredTicks = 0;
    int glideTicks = 0;
    double poweredTravel = 0;
    double glidingTravel = 0;
    boolean banked = false;
    boolean flexed = false;
    try {
      for (int tick = 0; tick < 320; tick++) {
        var before = ray.position();
        var previous = ray.flightPose(1);
        level.tickNonPassenger(ray);
        var pose = ray.flightPose(1);
        double distance = ray.position().distanceTo(before);
        if (pose.power() > 0.99F && previous.power() > 0.99F) {
          poweredTicks++;
          poweredTravel += distance;
          if (pose.shoulder() * pose.tip() < -0.015F) flexed = true;
        }
        if (pose.power() == 0 && previous.power() == 0) {
          glideTicks++;
          glidingTravel += distance;
          if (Math.abs(pose.tip() - previous.tip()) > 1e-5)
            throw new IllegalStateException("The gliding wing kept beating");
        }
        if (!banked && Math.abs(pose.bank()) > 0.06F) {
          var tracker = Objects.requireNonNull(AeroFauna.SKY_RAY.get().create(level));
          tracker.setXRot(ray.getXRot());
          tracker
              .getEntityData()
              .assignValues(Objects.requireNonNull(ray.getEntityData().getNonDefaultValues()));
          var tracked = tracker.flightPose(1);
          if (Math.abs(tracked.shoulder() - pose.shoulder()) > 1e-5
              || Math.abs(tracked.tip() - pose.tip()) > 1e-5
              || Math.abs(tracked.bank() - pose.bank()) > 1e-5)
            throw new IllegalStateException(
                "Late-tracking ray did not receive the current wing pose");
          banked = true;
        }
        var clearance = ray.clearanceBounds().inflate(1e-6);
        for (var part : ray.getParts()) {
          var box = part.getBoundingBox();
          if (!clearance.contains(box.minX, box.minY, box.minZ)
              || !clearance.contains(box.maxX, box.maxY, box.maxZ))
            throw new IllegalStateException("Ray wing moved beyond spawn and culling clearance");
        }
      }
    } finally {
      ray.discard();
    }
    if (poweredTicks < 60
        || glideTicks < 30
        || glidingTravel < 1
        || !banked
        || !flexed
        || poweredTravel / poweredTicks <= 1.10 * glidingTravel / glideTicks)
      throw new IllegalStateException(
          "Incomplete ray flight cycle: powered="
              + poweredTicks
              + "/"
              + poweredTravel
              + ", glided="
              + glideTicks
              + "/"
              + glidingTravel
              + ", banked="
              + banked
              + ", flexed="
              + flexed);
  }
}
