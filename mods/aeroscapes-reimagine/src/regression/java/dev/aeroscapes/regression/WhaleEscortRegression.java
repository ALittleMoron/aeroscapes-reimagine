package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.sky.WhaleCompanionGoal;
import dev.dacite.skywhales.entity.SkyWhale;
import dev.dacite.skywhales.registry.SWEntities;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Moves the assembled Sable cabin through a fixed course and exercises the native flight step. */
final class WhaleEscortRegression {
  private WhaleEscortRegression() {}

  static void verify(ServerLevel level, ServerSubLevel ship) {
    Vec3 center = ship.boundingBox().toMojang().getCenter();
    Vec3 start = new Vec3(center.x, level.getMaxBuildHeight() - 44, center.z);
    for (int x = -1; x <= 5; x++)
      for (int z = -3; z <= 17; z++)
        level.getChunk(((int) start.x >> 4) + x, ((int) start.z >> 4) + z);
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("367daf22-38aa-4eb6-b71b-1aac5ea6e610"),
            "WhaleEscort",
            start,
            null)) {
      double cruise = cruise(level, start.add(36, 2, 0));
      ((EntityMovementExtension) session.player).sable$setTrackingSubLevel(ship);
      for (double speed : new double[] {2, 10}) {
        Vec3 position = ship.boundingBox().toMojang().getCenter();
        moveShip(level, ship, start.subtract(position));
        moveShip(level, ship, new Vec3(0, 0, speed / 20));
        Vec3 rider = ship.boundingBox().toMojang().getCenter().add(0, 3, 0);
        session.player.setPos(rider);
        SkyWhale whale = Objects.requireNonNull(SWEntities.SKY_WHALE.get().create(level));
        whale.moveTo(rider.x + 36, rider.y - 1, rider.z - 24);
        var goal = new WhaleCompanionGoal(whale);
        whale.tickCount =
            Math.floorMod(-whale.getId(), WhaleCompanionGoal.ESCORT_CHECK_TICKS) + 100;
        whale.getRandom().setSeed(4096);
        require(
            goal.canUse(),
            "Whale did not join a moving Sable ship at "
                + speed
                + " blocks/s, sensed "
                + ship.latestLinearVelocity);
        goal.start();
        double peak = 0;
        double finalSpeed = 0;
        int arrival = -1;
        double maxSettledError = 0;
        for (int i = 0; i < 400; i++) {
          moveShip(level, ship, new Vec3(0, 0, speed / 20));
          session.player.setPos(ship.boundingBox().toMojang().getCenter().add(0, 3, 0));
          require(goal.canContinueToUse(), "Escort quit an eligible moving ship at " + speed);
          Vec3 before = whale.position();
          goal.tick();
          whale.getMoveControl().tick();
          whale.travel(Vec3.ZERO);
          whale.tickCount++;
          Vec3 measured = whale.position().subtract(before).scale(20);
          finalSpeed = measured.length();
          peak = Math.max(peak, finalSpeed);
          Vec3 offset = whale.position().subtract(ship.boundingBox().toMojang().getCenter());
          boolean abreast =
              offset.x >= 31
                  && offset.x <= 41
                  && Math.abs(offset.z - 8) < 1
                  && Math.abs(offset.y - 2) < 1;
          double error = measured.distanceTo(new Vec3(0, 0, speed));
          if (arrival < 0 && abreast && error <= .25) arrival = i;
          if (i >= 360) {
            require(abreast, "Whale drifted out of the escort lane: " + offset);
            maxSettledError = Math.max(maxSettledError, error);
          }
        }
        require(
            arrival >= 0 && arrival <= 340,
            "Whale failed to acquire the moving lane in 17 seconds: " + arrival);
        require(
            maxSettledError < .05,
            "Whale's sustained 3D velocity error at " + speed + " blocks/s: " + maxSettledError);
        require(
            peak > speed + .5 && peak <= speed + WhaleCompanionGoal.CATCH_UP_RESERVE + .01,
            "Whale catch-up speed out of bounds: " + peak);
        // An already-approved empty route becomes occupied before the next ten-tick terrain check.
        moveShip(level, ship, whale.position().subtract(ship.boundingBox().toMojang().getCenter()));
        moveShip(level, ship, new Vec3(0, 0, speed / 20));
        goal.tick();
        require(
            whale.getDeltaMovement().equals(Vec3.ZERO),
            "Whale advanced into a hull on a cached route");
        moveShip(level, ship, new Vec3(0, 0, (speed == 2 ? 0 : 11) / 20.0));
        require(!goal.canContinueToUse(), "An ineligible vessel kept its escort");
        moveShip(level, ship, new Vec3(0, 0, 0.25));
        require(goal.canContinueToUse(), "The rider still occupies the eligible ship");
        whale.tickCount += WhaleCompanionGoal.ESCORT_MAXIMUM_TICKS;
        require(!goal.canContinueToUse(), "An eligible ship kept an escort past its deadline");
        goal.stop();
        whale.tickCount +=
            Math.floorMod(-whale.tickCount - whale.getId(), WhaleCompanionGoal.ESCORT_CHECK_TICKS);
        whale.getRandom().setSeed(4096);
        require(!goal.canUse(), "The same whale immediately restarted an escort");
        LogUtils.getLogger()
            .info(
                "Whale motion verified: cruise={}, vessel={}, peak={}, matched={}, arrivalTicks={},"
                    + " maxSettledVelocityError={}",
                cruise,
                speed,
                peak,
                finalSpeed,
                arrival,
                maxSettledError);
      }
    }
  }

  private static double cruise(ServerLevel level, Vec3 start) {
    SkyWhale whale = Objects.requireNonNull(SWEntities.SKY_WHALE.get().create(level));
    whale.moveTo(start.x, start.y, start.z);
    whale.setPersistenceRequired();
    whale.getRandom().setSeed(19);
    require(level.addFreshEntity(whale), "Could not place native cruising whale");
    double[] speeds = new double[420];
    try {
      for (int i = 0; i < speeds.length; i++) {
        Vec3 before = whale.position();
        level.tickNonPassenger(whale);
        speeds[i] = whale.position().subtract(before).horizontalDistance() * 20;
      }
    } finally {
      whale.discard();
    }
    Arrays.sort(speeds);
    double cruise = speeds[speeds.length * 3 / 4];
    require(cruise >= 1.6 && cruise <= 2.6, "Ordinary native drift cruise speed " + cruise);
    LogUtils.getLogger()
        .info(
            "Whale native drift: median={}, p75={}, peak={} blocks/s",
            speeds[speeds.length / 2],
            cruise,
            speeds[speeds.length - 1]);
    return cruise;
  }

  private static void moveShip(ServerLevel level, ServerSubLevel ship, Vec3 step) {
    var physics = SubLevelContainer.getContainer(level).physicsSystem();
    ship.updateLastPose();
    var pose = ship.logicalPose();
    var destination = new Vector3d(pose.position()).add(step.x, step.y, step.z);
    physics.getPipeline().teleport(ship, destination, pose.orientation());
    physics.updatePose(ship);
    ship.updateBoundingBox();
    ship.forceUpdateGlobalBounds();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
