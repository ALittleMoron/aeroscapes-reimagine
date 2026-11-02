package dev.aeroscapes.pickup;

import dev.aeroscapes.mixin.pickup.ItemPickupDelayAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import team.creative.itemphysic.ItemPhysic;
import team.creative.itemphysic.common.CommonPhysic;

/** Server validation for the exact native pickup and charged-toss packets. */
public final class PickupRequests {
  private PickupRequests() {}

  public static boolean canPickup(ServerPlayer player, ItemEntity item) {
    if (!player.isAlive()
        || !item.isAlive()
        || item.level() != player.level()
        || ((ItemPickupDelayAccess) item).aeroscapes$getPickupDelay() == 32767
        || (item.getTarget() != null && !item.getTarget().equals(player.getUUID()))
        || !ItemPhysic.CONFIG.pickup.canPickup(player)) return false;
    double range = CommonPhysic.getReachDistance(player);
    double tolerance = ItemPhysic.CONFIG.pickup.hitboxIncrease;
    if (!Double.isFinite(range)
        || range <= 0
        || !Double.isFinite(tolerance)
        || tolerance < 0
        || tolerance > 1) return false;
    Level level = player.level();
    Vec3 localEye = player.getEyePosition();
    Vec3 eye = worldPosition(level, localEye);
    Vec3 view =
        worldPosition(level, localEye.add(player.getViewVector(1))).subtract(eye).normalize();
    if (!finite(eye) || !finite(view) || view.lengthSqr() == 0) return false;
    AABB box = worldBounds(level, item.getBoundingBox().inflate(tolerance));
    var entry = box.contains(eye) ? Optional.of(eye) : box.clip(eye, eye.add(view.scale(range)));
    if (entry.isEmpty() || !finite(entry.get())) return false;
    Vec3 target = entry.get();
    HitResult hit =
        level.clip(
            new ClipContext(
                eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    return hit.getType() == HitResult.Type.MISS
        || worldPosition(level, hit.getLocation()).distanceToSqr(eye)
            >= eye.distanceToSqr(target) - 0.000001;
  }

  public static boolean canThrow(ServerPlayer player, int power) {
    var settings = ItemPhysic.CONFIG.throwConfig;
    return player.isAlive()
        && !player.isSpectator()
        && settings.enabled
        && power >= 1
        && power <= settings.maxStages
        && Double.isFinite(settings.multiplierPerStage)
        && settings.multiplierPerStage >= 0;
  }

  public static Vec3 worldPosition(Level level, Vec3 position) {
    Vector3d transformed =
        SableCompanion.INSTANCE.projectOutOfSubLevel(
            level, new Vector3d(position.x, position.y, position.z), new Vector3d());
    return new Vec3(transformed.x, transformed.y, transformed.z);
  }

  private static AABB worldBounds(Level level, AABB box) {
    AABB result = null;
    for (int corner = 0; corner < 8; corner++) {
      Vec3 transformed =
          worldPosition(
              level,
              new Vec3(
                  (corner & 1) == 0 ? box.minX : box.maxX,
                  (corner & 2) == 0 ? box.minY : box.maxY,
                  (corner & 4) == 0 ? box.minZ : box.maxZ));
      AABB point = new AABB(transformed, transformed);
      result = result == null ? point : result.minmax(point);
    }
    return result;
  }

  private static boolean finite(Vec3 position) {
    return Double.isFinite(position.x)
        && Double.isFinite(position.y)
        && Double.isFinite(position.z);
  }
}
