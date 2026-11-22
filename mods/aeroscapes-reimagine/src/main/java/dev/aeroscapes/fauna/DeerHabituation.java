package dev.aeroscapes.fauna;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

public final class DeerHabituation {
  private static final int SAMPLE_INTERVAL = 20;
  private static final int QUIET_SAMPLES = 18;
  private static final int FAMILIARITY_TICKS = 2400;
  private static final int STARTLE_TICKS = 45;
  private static final int STARTLE_COOLDOWN_TICKS = 80;
  private static final int RETREAT_RANGE = 9;
  private static final int OBSERVATION_RANGE = 9;
  private static final double PERSONAL_SPACE_SQUARED = 3 * 3;

  private final PathfinderMob deer;
  private @Nullable UUID observer;
  private int quietSamples;
  private int lastObserved;
  private @Nullable Vec3 noise;
  private int startledUntil;
  private int nextStartle;

  public DeerHabituation(PathfinderMob deer) {
    this.deer = deer;
  }

  public void observe(ServerLevel level) {
    int now = deer.tickCount;
    if ((now + deer.getId()) % SAMPLE_INTERVAL != 0) return;
    int timeout = quietSamples == QUIET_SAMPLES ? FAMILIARITY_TICKS : SAMPLE_INTERVAL;
    if (now - lastObserved > timeout) {
      observer = null;
      quietSamples = 0;
    }
    if (now < startledUntil || deer.hurtTime > 0) return;
    var known = observer == null ? null : level.getPlayerByUUID(observer);
    if (known != null && quiet(known)) {
      quietSamples = Math.min(QUIET_SAMPLES, quietSamples + 1);
      lastObserved = now;
    } else if (observer == null) {
      var nearest =
          level.getNearestPlayer(
              deer.getX(),
              deer.getY(),
              deer.getZ(),
              OBSERVATION_RANGE,
              entity -> entity instanceof Player player && quiet(player));
      if (nearest != null) {
        observer = nearest.getUUID();
        quietSamples = 1;
        lastObserved = now;
      }
    }
  }

  private boolean quiet(Player player) {
    double distance = deer.distanceToSqr(player);
    return distance >= PERSONAL_SPACE_SQUARED
        && distance < OBSERVATION_RANGE * OBSERVATION_RANGE
        && player.isAlive()
        && player.isCrouching()
        && !player.isSprinting()
        && !player.isInvisible()
        && !player.isCreative()
        && !player.isSpectator()
        && !(player instanceof FakePlayer)
        && deer.hasLineOfSight(player);
  }

  public boolean avoidNormally(LivingEntity candidate) {
    int now = deer.tickCount;
    return quietSamples < QUIET_SAMPLES
        || now - lastObserved > FAMILIARITY_TICKS
        || !candidate.getUUID().equals(observer)
        || candidate.isSprinting()
        || deer.hurtTime > 0
        || now < startledUntil
        || deer.distanceToSqr(candidate) < PERSONAL_SPACE_SQUARED;
  }

  public boolean startle(Vec3 source) {
    int now = deer.tickCount;
    if (now < nextStartle) return false;
    noise = source;
    startledUntil = now + STARTLE_TICKS;
    nextStartle = now + STARTLE_COOLDOWN_TICKS;
    return true;
  }

  public Goal startleGoal() {
    return new Retreat();
  }

  private boolean hasRetreatTerrain(ServerLevel level) {
    var center = deer.blockPosition();
    var chunks = level.getChunkSource();
    int minX = SectionPos.blockToSectionCoord(center.getX() - RETREAT_RANGE);
    int maxX = SectionPos.blockToSectionCoord(center.getX() + RETREAT_RANGE);
    int minZ = SectionPos.blockToSectionCoord(center.getZ() - RETREAT_RANGE);
    int maxZ = SectionPos.blockToSectionCoord(center.getZ() + RETREAT_RANGE);
    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        if (chunks.getChunkNow(x, z) == null) return false;
      }
    }
    return true;
  }

  private final class Retreat extends Goal {
    private final PathNavigation navigation = deer.getNavigation();
    private @Nullable Path path;

    private Retreat() {
      setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
      Vec3 source = noise;
      if (source == null
          || deer.tickCount >= startledUntil
          || !(deer.level() instanceof ServerLevel level)) return false;
      noise = null;
      if (!hasRetreatTerrain(level)) return false;
      path = pathAway(source, 3);
      // Vertical samples can all miss a thin, level ledge.
      if (path == null) path = pathAway(source, 0);
      return path != null;
    }

    private @Nullable Path pathAway(Vec3 source, int verticalRange) {
      var target = LandRandomPos.getPosAway(deer, RETREAT_RANGE, verticalRange, source);
      if (target == null || target.distanceToSqr(source) <= deer.distanceToSqr(source)) {
        return null;
      }
      var candidate = navigation.createPath(target.x, target.y, target.z, 0);
      return candidate != null && candidate.canReach() ? candidate : null;
    }

    @Override
    public boolean canContinueToUse() {
      return deer.tickCount < startledUntil && !navigation.isDone();
    }

    @Override
    public void start() {
      navigation.moveTo(path, 1.7);
    }

    @Override
    public void stop() {
      navigation.stop();
      path = null;
    }
  }
}
