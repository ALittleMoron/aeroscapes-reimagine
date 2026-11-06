package dev.aeroscapes.construction;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Geometry actually submitted to the native pipeline, also used by the server regression. */
public interface LittleSableColliderAccess {
  List<AABB> aeroscapes$colliderBoxes(BlockPos position);
}
