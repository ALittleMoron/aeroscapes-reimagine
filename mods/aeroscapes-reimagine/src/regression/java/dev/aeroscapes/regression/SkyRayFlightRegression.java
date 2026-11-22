package dev.aeroscapes.regression;

import dev.aeroscapes.sky.fauna.AeroFauna;
import dev.aeroscapes.sky.fauna.FaunaEncounters;
import dev.aeroscapes.sky.fauna.SkyRay;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Follows a living ray through a complete patrol cycle in the encounter fixture's loaded air. */
final class SkyRayFlightRegression {
  private SkyRayFlightRegression() {}

  static void verifyFamily(ServerLevel level, ServerPlayer observer, AABB neighborhood) {
    for (int seed = 0; seed < 48; seed++) {
      int count = FaunaEncounters.spawnNear(level, observer, RandomSource.create(seed), false);
      List<SkyRay> rays = level.getEntitiesOfClass(SkyRay.class, neighborhood);
      try {
        require(count == rays.size() && count <= 2, "A ray encounter admits at most one calf");
        if (count != 2) continue;
        var adult = rays.stream().filter(ray -> !ray.isCalf()).findFirst().orElseThrow();
        var calf = rays.stream().filter(SkyRay::isCalf).findFirst().orElseThrow();
        require(calf.getSpawnType() == MobSpawnType.NATURAL, "Calf was naturally spawned");
        require(calf.getBbWidth() < adult.getBbWidth(), "Calf body shrank with its visible form");
        require(
            !adult.clearanceBounds().intersects(calf.clearanceBounds()),
            "Adult and calf spawn with a clear wing span");
        verifySavedFamily(level, adult, calf);
        verifyWingBounds(calf);
        verifyFollow(level, adult, calf);
        return;
      } finally {
        rays.forEach(Entity::discard);
      }
    }
    throw new IllegalStateException("No ray family in the fixed natural encounter sample");
  }

  private static void verifyFollow(ServerLevel level, SkyRay adult, SkyRay calf) {
    var initial = calf.position();
    var farther = calf.position().subtract(adult.position()).normalize().scale(4);
    var offsetBounds = calf.clearanceBounds().move(farther);
    if (FaunaEncounters.clear(level, offsetBounds)) calf.setPos(initial.add(farther));
    double before = calf.distanceTo(adult);
    for (int tick = 0; tick < 60; tick++) {
      level.tickNonPassenger(adult);
      level.tickNonPassenger(calf);
      verifyWingBounds(calf);
      require(
          !adult.clearanceBounds().intersects(calf.clearanceBounds()),
          "Swimming calf crossed the adult's wing envelope");
    }
    require(
        calf.position().distanceTo(initial) > 3 && calf.distanceTo(adult) < before + 6,
        "Calf follows the moving adult");
  }

  private static void verifySavedFamily(ServerLevel level, SkyRay adult, SkyRay calf) {
    var fromSave = newRay(level);
    fromSave.load(calf.saveWithoutId(new CompoundTag()));
    require(
        fromSave.isCalf() && fromSave.clearanceBounds().equals(calf.clearanceBounds()),
        "Saved calf restores size and juvenile form before ticking");
    var lateTracker = newRay(level);
    lateTracker
        .getEntityData()
        .assignValues(Objects.requireNonNull(calf.getEntityData().getNonDefaultValues()));
    require(
        lateTracker.isCalf() && lateTracker.getBbWidth() == calf.getBbWidth(),
        "The juvenile form and physical size reach a late tracker");
    fromSave.load(adult.saveWithoutId(new CompoundTag()));
    require(!fromSave.isCalf(), "An adult without juvenile data stays an adult");
  }

  static void verifyCalfNearHull(ServerLevel level, Vec3 hullCenter) {
    var calf = newRay(level);
    var tag = calf.saveWithoutId(new CompoundTag());
    tag.putBoolean("RayCalf", true);
    calf.load(tag);
    calf.moveTo(hullCenter.x, hullCenter.y, hullCenter.z);
    require(!FaunaEncounters.clear(level, calf.clearanceBounds()), "Hull excludes the calf");
    calf.moveTo(hullCenter.x + 12, hullCenter.y, hullCenter.z);
    require(FaunaEncounters.clear(level, calf.clearanceBounds()), "Open lane admits the calf");
  }

  private static SkyRay newRay(ServerLevel level) {
    return Objects.requireNonNull(AeroFauna.SKY_RAY.get().create(level));
  }

  private static void verifyWingBounds(SkyRay ray) {
    var clearance = ray.clearanceBounds().inflate(1e-6);
    for (var part : ray.getParts()) {
      var box = part.getBoundingBox();
      require(
          clearance.contains(box.minX, box.minY, box.minZ)
              && clearance.contains(box.maxX, box.maxY, box.maxZ),
          "Ray wing moved beyond spawn and culling clearance");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

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
        verifyWingBounds(ray);
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
