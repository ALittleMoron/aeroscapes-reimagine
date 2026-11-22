package dev.aeroscapes.regression;

import com.crispytwig.naturalist.world.entity.animal.deer.Deer;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

final class WildlifeReactionsRegression {
  private WildlifeReactionsRegression() {}

  static void verify(ServerLevel level) {
    var center = level.getSharedSpawnPos().south(32).atY(level.getMaxBuildHeight() - 20);
    prepareGround(level, center);
    var entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("naturalist:deer"));
    if (!(entityType.create(level) instanceof Deer deer)) {
      throw new IllegalStateException("Naturalist deer did not load");
    }
    Vec3 home = Vec3.atBottomCenterOf(center);
    Vec3 near = home.add(5, 0, 0);
    Vec3 far = home.add(14, 0, 0);
    deer.moveTo(home.x, home.y, home.z, 0, 0);
    require(level.addFreshEntity(deer), "Could not place the deer");
    deer.setOnGround(true);
    deer.getRandom().setSeed(6940);
    try (var visitor =
            TestShipPlayerSession.create(level, UUID.randomUUID(), "DeerVisitor", near, null);
        var stranger =
            TestShipPlayerSession.create(level, UUID.randomUUID(), "DeerStranger", far, null)) {
      var avoidance =
          deer.goalSelector.getAvailableGoals().stream()
              .map(WrappedGoal::getGoal)
              .filter(AvoidEntityGoal.class::isInstance)
              .findFirst()
              .orElseThrow();
      require(canFlee(avoidance), "Unfamiliar approach did not scare the deer");
      crouch(visitor.player, true);
      for (int tick = 0; tick < 400; tick++) {
        deer.tickCount++;
        deer.customServerAiStep();
      }
      crouch(visitor.player, false);
      require(!canFlee(avoidance), "Familiar slow approach still scared the deer");
      visitor.player.setSprinting(true);
      require(canFlee(avoidance), "Familiar sprint did not scare the deer");
      visitor.player.setSprinting(false);
      visitor.player.setPos(far);
      stranger.player.setPos(near);
      require(canFlee(avoidance), "The deer tolerated a stranger");
      stranger.player.setPos(far);
      visitor.player.setPos(near);

      Vec3 noise = home.add(0, 0, -10);
      level.gameEvent(GameEvent.STEP, noise, GameEvent.Context.of(null, null));
      require(!canFlee(avoidance), "A quiet step startled the deer");
      level.explode(null, noise.x, noise.y, noise.z, 3, Level.ExplosionInteraction.NONE);
      require(canFlee(avoidance), "A nearby blast did not interrupt familiarity");
      crouch(visitor.player, true);
      deer.goalSelector.tick();
      var retreat = deer.getNavigation().getPath();
      require(
          retreat != null && retreat.getTarget().distToCenterSqr(noise) > home.distanceToSqr(noise),
          "The startled deer did not find a route away from the blast: home="
              + home
              + ", source="
              + noise
              + ", target="
              + (retreat == null ? null : retreat.getTarget())
              + ", running goals="
              + deer.goalSelector.getAvailableGoals().stream()
                  .filter(WrappedGoal::isRunning)
                  .map(goal -> goal.getGoal().getClass().getSimpleName())
                  .toList());
      deer.tickCount += 100;
      deer.goalSelector.tick();
      crouch(visitor.player, false);
      require(!canFlee(avoidance), "The deer failed to calm down");
      level.gameEvent(GameEvent.LIGHTNING_STRIKE, noise, GameEvent.Context.of(null, null));
      require(canFlee(avoidance), "A nearby lightning strike did not startle the deer");
    } finally {
      deer.discard();
    }
  }

  private static void prepareGround(ServerLevel level, BlockPos center) {
    for (BlockPos foot :
        BlockPos.betweenClosed(center.offset(-16, 0, -16), center.offset(16, 0, 16))) {
      level.setBlock(foot.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
      level.setBlock(foot, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
      level.setBlock(foot.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
  }

  private static void crouch(ServerPlayer player, boolean crouching) {
    player.setShiftKeyDown(crouching);
    player.setPose(crouching ? Pose.CROUCHING : Pose.STANDING);
  }

  private static boolean canFlee(Goal goal) {
    for (int attempt = 0; attempt < 4; attempt++) {
      if (goal.canUse()) return true;
    }
    return false;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
