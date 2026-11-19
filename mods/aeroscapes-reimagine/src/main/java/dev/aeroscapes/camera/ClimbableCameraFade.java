package dev.aeroscapes.camera;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class ClimbableCameraFade {
  private ClimbableCameraFade() {}

  public static boolean shouldFade(boolean obstructed, Entity cameraEntity) {
    if (!obstructed || !(cameraEntity instanceof Player player)) return obstructed;
    if (player.onClimbable()) return false;
    // Include the approach and dismount, when the body is just outside the climbable block.
    var level = player.level();
    return BlockPos.betweenClosedStream(player.getBoundingBox().inflate(0.5))
        .noneMatch(
            pos -> {
              var state = level.getBlockState(pos);
              return state.is(BlockTags.CLIMBABLE) || state.isLadder(level, pos, player);
            });
  }
}
