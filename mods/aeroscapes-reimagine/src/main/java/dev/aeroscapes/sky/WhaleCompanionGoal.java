package dev.aeroscapes.sky;

import dev.dacite.skywhales.entity.SkyWhale;
import dev.ryanhcode.sable.Sable;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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

  /** Blocks per second, measured from the ship's own movement between two checks. */
  public static final double ESCORT_MINIMUM_SPEED = 2.0;

  public static final double ESCORT_MAXIMUM_SPEED = 10.0;
  public static final int ESCORT_MINIMUM_TICKS = 600;
  public static final int ESCORT_MAXIMUM_TICKS = 1200;
  public static final int ESCORT_COOLDOWN_TICKS = 3600;
  public static final double ESCORT_SPEED_MODIFIER = 1.6;
  private static final double FOLLOW_SPEED_MODIFIER = 1.2;
  private static final int REPATH_TICKS = 10;
  private static final int HEADING_SAMPLE_TICKS = 20;

  private final SkyWhale whale;
  private Mode mode = Mode.NONE;
  private @Nullable SkyWhale leader;
  private @Nullable ServerPlayer escorted;
  private @Nullable UUID sampledShip;
  private @Nullable Vec3 sampledShipPosition;
  private int sampledTick;
  private int escortUntil;
  private int nextEscortTick;
  private double escortSide;
  private Vec3 heading = Vec3.ZERO;
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
    ServerPlayer candidate = null;
    double nearest = ESCORT_RANGE * ESCORT_RANGE;
    for (var player : level.players()) {
      if (player.isSpectator()) continue;
      double distance = player.distanceToSqr(whale);
      if (distance < nearest && Sable.HELPER.getTrackingOrVehicleSubLevel(player) != null) {
        nearest = distance;
        candidate = player;
      }
    }
    var ship = candidate == null ? null : Sable.HELPER.getTrackingOrVehicleSubLevel(candidate);
    if (ship == null) {
      sampledShip = null;
      return false;
    }
    var position = shipPosition(ship);
    var previous = sampledShipPosition;
    boolean sameShip =
        ship.getUniqueId().equals(sampledShip)
            && previous != null
            && whale.tickCount - sampledTick <= 2 * ESCORT_CHECK_TICKS;
    Vec3 heading = sameShip ? horizontal(position.subtract(previous)) : Vec3.ZERO;
    double speed = heading.length() * 20.0 / Math.max(1, whale.tickCount - sampledTick);
    sampledShip = ship.getUniqueId();
    sampledShipPosition = position;
    sampledTick = whale.tickCount;
    if (!sameShip
        || speed < ESCORT_MINIMUM_SPEED
        || speed > ESCORT_MAXIMUM_SPEED
        || whale.getRandom().nextFloat() >= ESCORT_CHANCE) return false;
    escorted = candidate;
    this.heading = heading.normalize();
    escortUntil =
        whale.tickCount
            + ESCORT_MINIMUM_TICKS
            + whale.getRandom().nextInt(ESCORT_MAXIMUM_TICKS - ESCORT_MINIMUM_TICKS + 1);
    Vec3 offset = whale.position().subtract(candidate.position());
    double cross = heading.x * offset.z - heading.z * offset.x;
    escortSide = (cross >= 0.0 ? 1.0 : -1.0) * (32.0 + whale.getRandom().nextDouble() * 8.0);
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
              && escorted.distanceToSqr(whale) < (ESCORT_RANGE + 32.0) * (ESCORT_RANGE + 32.0)
              && Sable.HELPER.getTrackingOrVehicleSubLevel(escorted) != null;
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
    sampledShip = null;
    sampledShipPosition = null;
    escorted = null;
    leader = null;
  }

  @Override
  public void tick() {
    if (--repath > 0) return;
    repath = REPATH_TICKS;
    Vec3 goal = mode == Mode.FOLLOW ? followGoal() : escortGoal();
    if (goal == null) return;
    if (!pathClear(goal)) {
      goal = goal.add(0.0, 8.0, 0.0);
      if (!pathClear(goal)) return;
    }
    whale
        .getMoveControl()
        .setWantedPosition(
            goal.x,
            goal.y,
            goal.z,
            mode == Mode.FOLLOW ? FOLLOW_SPEED_MODIFIER : ESCORT_SPEED_MODIFIER);
  }

  private @Nullable Vec3 followGoal() {
    if (leader == null) return null;
    Vec3 toLeader = horizontal(whale.position().subtract(leader.position()));
    if (toLeader.lengthSqr() < 1.0E-6) return null;
    return leader.position().add(toLeader.normalize().scale(FOLLOW_STOP));
  }

  private @Nullable Vec3 escortGoal() {
    if (escorted == null) return null;
    var ship = Sable.HELPER.getTrackingOrVehicleSubLevel(escorted);
    if (ship == null) return null;
    Vec3 position = shipPosition(ship);
    if (sampledShipPosition != null) {
      Vec3 moved = horizontal(position.subtract(sampledShipPosition));
      if (moved.lengthSqr() > 0.25) heading = moved.normalize();
    }
    if (sampledShipPosition == null || whale.tickCount - sampledTick >= HEADING_SAMPLE_TICKS) {
      sampledShipPosition = position;
      sampledTick = whale.tickCount;
    }
    if (heading.lengthSqr() < 1.0E-6) return null;
    Vec3 side = new Vec3(-heading.z, 0.0, heading.x).scale(escortSide);
    return escorted.position().add(side).add(heading.scale(8.0)).add(0.0, 2.0, 0.0);
  }

  private boolean pathClear(Vec3 goal) {
    var level = whale.level();
    Vec3 delta = goal.subtract(whale.position());
    int steps = Math.max(1, Mth.ceil(delta.length() / 4.0));
    double halfWidth = whale.getBbWidth() / 2.0;
    for (int step = 1; step <= steps; step++) {
      Vec3 point = whale.position().add(delta.scale((double) step / steps));
      var box =
          new AABB(
                  point.x - halfWidth,
                  point.y,
                  point.z - halfWidth,
                  point.x + halfWidth,
                  point.y + whale.getBbHeight(),
                  point.z + halfWidth)
              .inflate(2.0);
      if (!loaded(level, box) || !level.noCollision(box) || level.containsAnyLiquid(box))
        return false;
    }
    return true;
  }

  /** Every chunk the collision and fluid probes can touch must already be loaded. */
  private static boolean loaded(net.minecraft.world.level.Level level, AABB box) {
    int y = Mth.floor(box.minY);
    for (int chunkX = (Mth.floor(box.minX) - 1) >> 4;
        chunkX <= (Mth.ceil(box.maxX) + 1) >> 4;
        chunkX++) {
      for (int chunkZ = (Mth.floor(box.minZ) - 1) >> 4;
          chunkZ <= (Mth.ceil(box.maxZ) + 1) >> 4;
          chunkZ++) {
        if (!level.isLoaded(new BlockPos(chunkX << 4, y, chunkZ << 4))) return false;
      }
    }
    return true;
  }

  private static Vec3 shipPosition(dev.ryanhcode.sable.sublevel.SubLevel ship) {
    var position = ship.logicalPose().position();
    return new Vec3(position.x(), position.y(), position.z());
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
