package dev.aeroscapes.expedition;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import org.jetbrains.annotations.Nullable;

final class DistractionGoal extends Goal {
  private static final String MARK = "aeroscapes_reimagine:distraction_throw";
  private static final double HEARING_RANGE = 8;
  private static final int MAX_CANDIDATES = 4;
  private static final int DURATION_TICKS = 60;
  private final Zombie zombie;
  private @Nullable Path path;
  private long deadline;

  private DistractionGoal(Zombie zombie) {
    this.zombie = zombie;
    setFlags(EnumSet.of(Flag.MOVE));
  }

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DistractionGoal::impact);
  }

  static void mark(Snowball snowball) {
    snowball.getPersistentData().putBoolean(MARK, true);
  }

  private static void impact(ProjectileImpactEvent event) {
    if (!(event.getProjectile() instanceof Snowball snowball)
        || !(snowball.level() instanceof ServerLevel level)
        || !(event.getRayTraceResult() instanceof BlockHitResult hit)
        || !snowball.getPersistentData().getBoolean(MARK)) return;

    var point = hit.getLocation();
    var destination = hit.getBlockPos().relative(hit.getDirection());
    var nearby =
        level.getEntitiesOfClass(
            Zombie.class,
            new AABB(point, point).inflate(HEARING_RANGE),
            zombie ->
                zombie.getType() == EntityType.ZOMBIE
                    && idle(zombie)
                    && zombie.distanceToSqr(point) <= HEARING_RANGE * HEARING_RANGE);
    nearby.sort(Comparator.comparingDouble(zombie -> zombie.distanceToSqr(point)));
    for (Zombie zombie : nearby.stream().limit(MAX_CANDIDATES).toList()) {
      Path candidate = zombie.getNavigation().createPath(destination, 0);
      if (candidate == null
          || !candidate.canReach()
          || candidate.getTarget().distManhattan(destination) > 1) continue;
      var goal =
          zombie.goalSelector.getAvailableGoals().stream()
              .map(wrapped -> wrapped.getGoal())
              .filter(DistractionGoal.class::isInstance)
              .map(DistractionGoal.class::cast)
              .findFirst()
              .orElse(null);
      if (goal == null) {
        goal = new DistractionGoal(zombie);
        zombie.goalSelector.addGoal(3, goal);
      }
      if (goal.path != null) continue;
      goal.path = candidate;
      goal.deadline = level.getGameTime() + DURATION_TICKS;
      return;
    }
  }

  private static boolean idle(Zombie zombie) {
    return zombie.isAlive() && !zombie.isNoAi() && zombie.getTarget() == null;
  }

  @Override
  public boolean canUse() {
    if (path == null) return false;
    if (idle(zombie) && zombie.level().getGameTime() < deadline) return true;
    path = null;
    return false;
  }

  @Override
  public void start() {
    zombie.getNavigation().moveTo(path, 1.0);
  }

  @Override
  public boolean canContinueToUse() {
    return idle(zombie)
        && zombie.level().getGameTime() < deadline
        && !zombie.getNavigation().isDone();
  }

  @Override
  public void stop() {
    path = null;
    zombie.getNavigation().stop();
  }
}
