package dev.aeroscapes.sky;

import dev.aeroscapes.sky.fauna.FaunaEncounters;
import dev.dacite.skywhales.entity.SkyWhale;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Pod companions return to their leader, and an idle whale may briefly swim beside a moving ship.
 * Both yield at once to native combat and leave ordinary wandering to the native drift goal.
 */
public final class WhaleCompanionGoal extends Goal {
  public static final double FOLLOW_START = 40.0;
  public static final double FOLLOW_STOP = 28.0;
  public static final double LEADER_RANGE = 160.0;
  public static final int ESCORT_CHECK_TICKS = 100;
  public static final float ESCORT_CHANCE = 0.2F;
  public static final double ESCORT_RANGE = 80.0;

  /** Horizontal entry speed and total speed ceiling, in Sable's blocks per second. */
  public static final double ESCORT_MINIMUM_SPEED = 2.0;

  public static final double ESCORT_MAXIMUM_SPEED = 10.0;
  public static final int ESCORT_MINIMUM_TICKS = 600;
  public static final int ESCORT_MAXIMUM_TICKS = 1200;
  public static final int ESCORT_COOLDOWN_TICKS = 3600;
  public static final double CATCH_UP_RESERVE = 3.5;
  private static final double FOLLOW_SPEED_MODIFIER = 1.2;
  private static final int REPATH_TICKS = 10;

  private final SkyWhale whale;
  private Mode mode = Mode.NONE;
  private @Nullable SkyWhale leader;
  private @Nullable ServerPlayer escorted;
  private @Nullable ServerSubLevel escortedShip;
  private @Nullable Vec3 waypoint;
  private @Nullable AABB checkedRoute;
  private int escortUntil;
  private int nextEscortTick;
  private double escortSide;
  private int repath;

  public WhaleCompanionGoal(SkyWhale whale) {
    this.whale = whale;
    setFlags(EnumSet.of(Flag.MOVE));
  }

  @Override
  public boolean canUse() {
    if (whale.getTarget() != null || !(whale.level() instanceof ServerLevel level)) return false;
    leader = WhalePods.leader(level, whale);
    if (leader != null) {
      double distance = whale.distanceToSqr(leader);
      if (distance > FOLLOW_START * FOLLOW_START && distance < LEADER_RANGE * LEADER_RANGE) {
        mode = Mode.FOLLOW;
        return true;
      }
      return false;
    }
    if (whale.tickCount < nextEscortTick
        || (whale.tickCount + whale.getId()) % ESCORT_CHECK_TICKS != 0) return false;
    return startEscort(level);
  }

  private boolean startEscort(ServerLevel level) {
    double nearest = ESCORT_RANGE * ESCORT_RANGE;
    ServerPlayer candidate = null;
    ServerSubLevel vessel = null;
    for (var player : level.players()) {
      if (!player.isAlive()
          || player.isSpectator()
          || !(Sable.HELPER.getTrackingOrVehicleSubLevel(player) instanceof ServerSubLevel ship)
          || !canEscort(ship)) continue;
      double distance =
          Sable.HELPER
              .projectOutOfSubLevel(level, player.position())
              .distanceToSqr(whale.position());
      if (distance < nearest) {
        nearest = distance;
        candidate = player;
        vessel = ship;
      }
    }
    if (vessel == null || whale.getRandom().nextFloat() >= ESCORT_CHANCE) return false;
    escorted = candidate;
    escortedShip = vessel;
    Vec3 heading = velocity(vessel);
    escortUntil =
        whale.tickCount
            + ESCORT_MINIMUM_TICKS
            + whale.getRandom().nextInt(ESCORT_MAXIMUM_TICKS - ESCORT_MINIMUM_TICKS + 1);
    Vec3 offset = whale.position().subtract(vessel.boundingBox().toMojang().getCenter());
    double cross = heading.x * offset.z - heading.z * offset.x;
    escortSide = (cross >= 0.0 ? 1.0 : -1.0) * (32.0 + whale.getRandom().nextDouble() * 8.0);
    double distanceToLane = escortLane(vessel, heading).distanceTo(whale.position());
    int arrivalTicks = Mth.ceil(20 * distanceToLane / CATCH_UP_RESERVE);
    escortUntil =
        Math.max(escortUntil, whale.tickCount + Math.min(ESCORT_MAXIMUM_TICKS, arrivalTicks + 160));
    mode = Mode.ESCORT;
    return true;
  }

  @Override
  public boolean canContinueToUse() {
    if (whale.getTarget() != null || !(whale.level() instanceof ServerLevel level)) return false;
    return switch (mode) {
      case FOLLOW -> {
        leader = WhalePods.leader(level, whale);
        if (leader == null) yield false;
        double distance = whale.distanceToSqr(leader);
        yield distance > FOLLOW_STOP * FOLLOW_STOP && distance < LEADER_RANGE * LEADER_RANGE;
      }
      case ESCORT ->
          escorted != null
              && escorted.isAlive()
              && !escorted.isSpectator()
              && whale.tickCount < escortUntil
              && Sable.HELPER
                      .projectOutOfSubLevel(level, escorted.position())
                      .distanceToSqr(whale.position())
                  < (ESCORT_RANGE + 32.0) * (ESCORT_RANGE + 32.0)
              && escortedShip != null
              && canEscort(escortedShip)
              && Sable.HELPER.getTrackingOrVehicleSubLevel(escorted) == escortedShip;
      case NONE -> false;
    };
  }

  @Override
  public boolean requiresUpdateEveryTick() {
    return true;
  }

  @Override
  public void start() {
    repath = 0;
  }

  @Override
  public void stop() {
    if (mode == Mode.ESCORT) nextEscortTick = whale.tickCount + ESCORT_COOLDOWN_TICKS;
    mode = Mode.NONE;
    escortedShip = null;
    waypoint = null;
    checkedRoute = null;
    escorted = null;
    leader = null;
  }

  @Override
  public void tick() {
    if (mode == Mode.ESCORT) {
      escort();
      return;
    }
    if (--repath <= 0) {
      repath = REPATH_TICKS;
      waypoint = followGoal();
      if (waypoint != null && !pathClear(waypoint)) {
        waypoint = waypoint.add(0, 8, 0);
        if (!pathClear(waypoint)) waypoint = null;
      }
    }
    // FlyingMoveControl consumes MOVE_TO after one tick, even between route checks.
    if (waypoint != null)
      whale
          .getMoveControl()
          .setWantedPosition(waypoint.x, waypoint.y, waypoint.z, FOLLOW_SPEED_MODIFIER);
  }

  private void escort() {
    if (escortedShip == null || !canEscort(escortedShip)) return;
    Vec3 shipVelocity = velocity(escortedShip);
    Vec3 lane = escortLane(escortedShip, shipVelocity);
    Vec3 desired = shipVelocity.scale(0.05).add(lane.subtract(whale.position()).scale(0.065));
    double speedLimit = (shipVelocity.length() + CATCH_UP_RESERVE) / 20;
    desired = desired.scale(Math.min(1.0, speedLimit / Math.max(desired.length(), 1.0e-9)));
    // Air travel stores velocity after applying drag; recover the last physical step for steering.
    Vec3 previous = whale.getDeltaMovement().multiply(1 / 0.91, 1 / 0.98, 1 / 0.91);
    Vec3 change = desired.subtract(previous);
    Vec3 next = previous.add(change.scale(Math.min(1, 0.013 / Math.max(change.length(), 1.0e-9))));
    float yaw = (float) (Mth.atan2(next.z, next.x) * Mth.RAD_TO_DEG) - 90;
    yaw = Mth.approachDegrees(whale.getYRot(), yaw, 3.0F);
    if (!stepClear(next, yaw)) {
      whale.setDeltaMovement(Vec3.ZERO);
      return;
    }
    whale.setDeltaMovement(next);
    Vec3 look = whale.position().add(Vec3.directionFromRotation(0, yaw)).add(0, next.y, 0);
    whale.getMoveControl().setWantedPosition(look.x, look.y, look.z, 0);
  }

  private Vec3 escortLane(ServerSubLevel ship, Vec3 shipVelocity) {
    Vec3 forward = horizontal(shipVelocity).normalize();
    Vec3 side = new Vec3(-forward.z, 0, forward.x);
    var hull = ship.boundingBox().toMojang();
    double halfBeam = (Math.abs(side.x) * hull.getXsize() + Math.abs(side.z) * hull.getZsize()) / 2;
    double offset = Math.copySign(Math.max(Math.abs(escortSide), halfBeam + 23), escortSide);
    return hull.getCenter().add(side.scale(offset)).add(forward.scale(8)).add(0, 2, 0);
  }

  private boolean stepClear(Vec3 next, float yaw) {
    if (!(whale.level() instanceof ServerLevel level)) return false;
    AABB sweep = bodyFacing(yaw).expandTowards(next).inflate(.25);
    var ships = SubLevelContainer.getContainer(level);
    if (ships != null && ships.queryIntersecting(new BoundingBox3d(sweep)).iterator().hasNext())
      return false;
    // A change of course can leave previously checked terrain before the next routine repath.
    if (--repath > 0
        && checkedRoute != null
        && checkedRoute.contains(sweep.minX, sweep.minY, sweep.minZ)
        && checkedRoute.contains(sweep.maxX, sweep.maxY, sweep.maxZ)) return true;
    repath = REPATH_TICKS;
    AABB route = sweep.expandTowards(next.scale(REPATH_TICKS)).inflate(1);
    checkedRoute = FaunaEncounters.clear(level, route) ? route : null;
    return checkedRoute != null;
  }

  private static boolean canEscort(ServerSubLevel ship) {
    Vec3 motion = velocity(ship);
    return !ship.isRemoved()
        && motion.horizontalDistance() >= ESCORT_MINIMUM_SPEED - 0.001
        && motion.length() <= ESCORT_MAXIMUM_SPEED + 0.001;
  }

  private static Vec3 velocity(ServerSubLevel ship) {
    var motion = ship.latestLinearVelocity;
    return new Vec3(motion.x, motion.y, motion.z);
  }

  private @Nullable Vec3 followGoal() {
    if (leader == null) return null;
    Vec3 toLeader = horizontal(whale.position().subtract(leader.position()));
    if (toLeader.lengthSqr() < 1.0E-6) return null;
    return leader.position().add(toLeader.normalize().scale(FOLLOW_STOP));
  }

  private boolean pathClear(Vec3 goal) {
    if (!(whale.level() instanceof ServerLevel level)) return false;
    Vec3 delta = goal.subtract(whale.position());
    float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90;
    AABB body = bodyFacing(yaw);
    int steps = Math.max(1, Mth.ceil(delta.length() / 4));
    for (int i = 1; i <= steps; i++)
      if (!FaunaEncounters.clear(level, body.move(delta.scale((double) i / steps)))) return false;
    return true;
  }

  private AABB bodyFacing(float yaw) {
    var variety = ((WhaleVariantAccess) whale).aeroscapes$variety();
    return whale
        .getBoundingBox()
        .minmax(WhaleDecorationClearance.enclosing(variety, whale.position(), yaw))
        .minmax(WhaleDecorationClearance.enclosing(variety, whale.position(), whale.yBodyRot));
  }

  private static Vec3 horizontal(Vec3 vector) {
    return new Vec3(vector.x, 0.0, vector.z);
  }

  private enum Mode {
    NONE,
    FOLLOW,
    ESCORT
  }
}
