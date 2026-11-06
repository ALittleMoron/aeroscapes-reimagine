package dev.aeroscapes.construction;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/** Reuses native collider entries: Sable's process-wide native registry has no deletion API. */
public final class LittleSableColliders {
  private static final Map<BlockState, ArrayDeque<RapierVoxelColliderData>> AVAILABLE =
      new HashMap<>();
  private final Map<BlockPos, Entry> loaded = new HashMap<>();

  public @Nullable RapierVoxelColliderData get(
      BlockGetter level, BlockPos position, BlockState state) {
    List<AABB> boxes = LittleSableGeometry.collision(level, position, state).toAabbs();
    Entry entry = loaded.get(position);
    if (entry != null && entry.state() != state) {
      release(position);
      entry = null;
    }
    if (boxes.isEmpty()) {
      release(position);
      return null;
    }
    if (entry == null) {
      entry = new Entry(state, acquire(state), List.of());
      loaded.put(position.immutable(), entry);
    }
    if (!entry.boxes().equals(boxes)) {
      entry.data().clearBoxes();
      for (AABB box : boxes)
        entry
            .data()
            .addBox(
                new Vector3d(Math.max(box.minX, 0), Math.max(box.minY, 0), Math.max(box.minZ, 0)),
                new Vector3d(Math.min(box.maxX, 1), Math.min(box.maxY, 1), Math.min(box.maxZ, 1)));
      entry = new Entry(state, entry.data(), List.copyOf(boxes));
      loaded.put(position.immutable(), entry);
    }
    return entry.data();
  }

  public List<AABB> boxes(BlockPos position) {
    Entry entry = loaded.get(position);
    return entry == null ? List.of() : entry.boxes();
  }

  public void release(BlockPos position) {
    Entry removed = loaded.remove(position);
    if (removed != null) recycle(removed);
  }

  public void releaseSection(int sectionX, int sectionY, int sectionZ) {
    var iterator = loaded.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      BlockPos position = entry.getKey();
      if (position.getX() >> 4 == sectionX
          && position.getY() >> 4 == sectionY
          && position.getZ() >> 4 == sectionZ) {
        recycle(entry.getValue());
        iterator.remove();
      }
    }
  }

  public void clear() {
    loaded.values().forEach(LittleSableColliders::recycle);
    loaded.clear();
  }

  private static synchronized RapierVoxelColliderData acquire(BlockState state) {
    var pool = AVAILABLE.get(state);
    if (pool != null && !pool.isEmpty()) return pool.removeFirst();
    var data =
        Rapier3D.createVoxelColliderEntry(
            PhysicsBlockPropertyHelper.getFriction(state),
            PhysicsBlockPropertyHelper.getVolume(state),
            PhysicsBlockPropertyHelper.getRestitution(state),
            false,
            BlockWithSubLevelCollisionCallback.sable$getCallback(state));
    // Native chunk cells reserve sixteen bits for handle + 1; truncation would select another
    // shape.
    if (data.handle() < 0 || data.handle() >= 0xffff)
      throw new IllegalStateException(
          "Sable native collider registry exceeded its 16-bit cell index");
    return data;
  }

  private static synchronized void recycle(Entry entry) {
    entry.data().clearBoxes();
    AVAILABLE.computeIfAbsent(entry.state(), ignored -> new ArrayDeque<>()).addLast(entry.data());
  }

  private record Entry(BlockState state, RapierVoxelColliderData data, List<AABB> boxes) {}
}
