package dev.aeroscapes.regression;

import dev.aeroscapes.sky.fauna.AeroFauna;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class FaunaHistoricalCoverRegression {
  private FaunaHistoricalCoverRegression() {}

  static void verify(ServerLevel level, ServerSubLevel ship) {
    var bounds = Objects.requireNonNull(ship.getPlot().getBoundingBox());
    Vec3 floor = new Vec3(bounds.minX() + 2.5, bounds.minY() + 0.5, bounds.minZ() + 2.5);
    Vec3 cabin = floor.add(0, 2, 0);
    Vec3 deck = floor.add(0, 5.5, 0);
    var pose = ship.logicalPose();
    Vec3 shift = pose.transformPosition(deck).subtract(pose.transformPosition(floor));
    ship.updateLastPose();
    pose.position().add(shift.x, shift.y, shift.z);
    SubLevelContainer.getContainer(level)
        .physicsSystem()
        .getPipeline()
        .teleport(ship, pose.position(), pose.orientation());
    ship.updateBoundingBox();
    ship.forceUpdateGlobalBounds();
    check(level, ship, deck, true);
    check(level, ship, cabin, false);
  }

  private static void check(
      ServerLevel level, ServerSubLevel ship, Vec3 localCenter, boolean deck) {
    var rider = Objects.requireNonNull(EntityType.COW.create(level));
    var jelly = Objects.requireNonNull(AeroFauna.CLOUD_JELLY.get().create(level));
    Vec3 start = ship.lastPose().transformPosition(localCenter);
    Vec3 end = ship.logicalPose().transformPosition(localCenter);
    if (start.distanceTo(end) < 5)
      throw new IllegalStateException("Missing ship sweep in cabin test");
    rider.moveTo(end.x, end.y - rider.getBbHeight() / 2, end.z);
    ((EntityMovementExtension) rider).sable$setTrackingSubLevel(ship);
    FaunaRegression.centerBell(jelly, start);
    // The current floor occupies the old deck's jelly core; the old cabin now hangs in open air.
    var ray =
        new ClipContext(
            start,
            start.add(0, 0.125, 0),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            rider);
    if ((level.clip(ray).getType() == HitResult.Type.BLOCK) != deck)
      throw new IllegalStateException("Final hull did not cross the expected old contact path");
    float health = rider.getHealth();
    if (jelly.stingAlong(rider, start, jelly.position()) != deck
        || (rider.getHealth() < health) != deck)
      throw new IllegalStateException(
          deck
              ? "Later hull position shielded earlier deck contact"
              : "A cabin that moved away lost shelter at contact time");
  }
}
