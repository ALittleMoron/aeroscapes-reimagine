package dev.aeroscapes.sky;

import dev.dacite.skywhales.entity.SkyWhale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Checks the pinned whale's actual multipart body at the selected spawn yaw. */
public final class WhaleSpawnClearance {
  private static final PartOffset[] OFFSETS = {
    new PartOffset(12.0, 0.0, -3.6),
    new PartOffset(6.0, 0.0, -3.6),
    new PartOffset(1.5, 0.0, 0.8),
    new PartOffset(-3.0, 0.0, 1.2),
    new PartOffset(-7.0, 0.0, 1.7),
    new PartOffset(2.5, 4.0, 2.1),
    new PartOffset(2.5, -4.0, 2.1),
    new PartOffset(-9.6, 1.75, 2.5),
    new PartOffset(-9.6, -1.75, 2.5)
  };

  private WhaleSpawnClearance() {}

  public static boolean clear(ServerLevel level, SkyWhale whale) {
    return clear(level, whale, ((WhaleVariantAccess) whale).aeroscapes$variety());
  }

  public static boolean clear(ServerLevel level, SkyWhale whale, WhaleVariety variety) {
    if (!clear(level, whale.getBoundingBox())) return false;
    var parts = whale.getParts();
    if (parts.length != OFFSETS.length) {
      throw new IllegalStateException("Review pinned Sky Whales multipart spawn geometry");
    }
    Vec3 forward = Vec3.directionFromRotation(0.0F, whale.yBodyRot);
    for (int index = 0; index < parts.length; index++) {
      var offset = OFFSETS[index];
      double partX = whale.getX() + forward.x * offset.forward() - forward.z * offset.side();
      double partY = whale.getY() + offset.bottom();
      double partZ = whale.getZ() + forward.z * offset.forward() + forward.x * offset.side();
      double halfWidth = parts[index].getBbWidth() * 0.5;
      var bounds =
          new AABB(
              partX - halfWidth,
              partY,
              partZ - halfWidth,
              partX + halfWidth,
              partY + parts[index].getBbHeight(),
              partZ + halfWidth);
      if (!clear(level, bounds)) return false;
    }
    for (AABB bounds : WhaleDecorationClearance.at(variety, whale.position(), whale.yBodyRot)) {
      if (!clear(level, bounds)) return false;
    }
    return true;
  }

  private static boolean clear(ServerLevel level, AABB bounds) {
    if (bounds.minY < level.getMinBuildHeight() || bounds.maxY > level.getMaxBuildHeight()) {
      return false;
    }
    int minimumChunkX = Mth.floor(bounds.minX) >> 4;
    int maximumChunkX = Mth.floor(Math.nextDown(bounds.maxX)) >> 4;
    int minimumChunkZ = Mth.floor(bounds.minZ) >> 4;
    int maximumChunkZ = Mth.floor(Math.nextDown(bounds.maxZ)) >> 4;
    for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
      for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
        if (!level.isLoaded(new BlockPos(chunkX << 4, Mth.floor(bounds.minY), chunkZ << 4))) {
          return false;
        }
      }
    }
    return level.noCollision(bounds);
  }

  private record PartOffset(double forward, double side, double bottom) {}
}
