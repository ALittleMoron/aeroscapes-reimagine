package dev.aeroscapes.sky;

import com.mojang.logging.LogUtils;
import dev.dacite.skywhales.corpse.WhaleCorpseSpawner;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Builds native carcasses in reserved physics storage instead of overwriting the death site. */
public final class WhaleCarcasses {
  private static final Logger LOGGER = LogUtils.getLogger();

  private WhaleCarcasses() {}

  public static void spawn(ServerLevel level, Vec3 center, float deathYaw) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null || container.physicsSystem() == null) {
      // A missing physics engine must not crash the dying whale's tick; skip the carcass.
      LOGGER.warn(
          "Skipping a sky whale carcass in {}: no Sable physics container",
          level.dimension().location());
      return;
    }
    var pose = new Pose3d();
    pose.position().set(center.x, center.y, center.z);
    var staging = (ServerSubLevel) container.allocateNewSubLevel(pose);
    try {
      var plot = staging.getPlot();
      var origin = plot.getCenterBlock();
      pose = staging.logicalPose();
      pose.rotationPoint().set(origin.getX(), origin.getY(), origin.getZ());
      // Initialize only reserved chunks touched by the native body and adjacent meat.
      var minimum = new ChunkPos(origin.offset(-7, 0, -15));
      var maximum = new ChunkPos(origin.offset(7, 0, 17));
      for (int chunkX = minimum.x; chunkX <= maximum.x; chunkX++) {
        for (int chunkZ = minimum.z; chunkZ <= maximum.z; chunkZ++) {
          plot.newEmptyChunk(new ChunkPos(chunkX, chunkZ));
        }
      }
      WhaleCorpseSpawner.spawnCorpse(level, Vec3.atLowerCornerOf(origin), deathYaw);
    } finally {
      container.removeSubLevel(staging, SubLevelRemovalReason.REMOVED);
    }
  }
}
