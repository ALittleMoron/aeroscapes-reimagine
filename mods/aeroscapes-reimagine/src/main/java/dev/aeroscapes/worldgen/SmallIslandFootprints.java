package dev.aeroscapes.worldgen;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Finds a climate anchor on the same bounded, connected body of natural terrain. */
final class SmallIslandFootprints {
  private static final int MAXIMUM_SPAN = 64;
  private static final int MAXIMUM_NODES = 2048;
  private static final long WIDE = Long.MIN_VALUE;
  private static final Direction[] NEIGHBORS = Direction.values();

  private record Entry(long position, long anchor) {}

  private final BiomeTerrainMask.TerrainNode occupied;
  private final int width;
  private final int height;
  private final AtomicReferenceArray<Entry> results = new AtomicReferenceArray<>(1 << 17);

  SmallIslandFootprints(BiomeTerrainMask.TerrainNode occupied, int width, int height) {
    this.occupied = occupied;
    this.width = width;
    this.height = height;
  }

  @Nullable
  BlockPos anchor(int x, int y, int z) {
    long start = BlockPos.asLong(x, y, z);
    Entry cached = cached(start);
    long anchor = cached == null ? trace(start) : cached.anchor();
    if (anchor == WIDE) return null;
    return new BlockPos(
        BlockPos.getX(anchor) * width,
        BlockPos.getY(anchor) * height,
        BlockPos.getZ(anchor) * width);
  }

  private long trace(long start) {
    int minX = BlockPos.getX(start);
    int maxX = minX;
    int minZ = BlockPos.getZ(start);
    int maxZ = minZ;
    var component = new LongOpenHashSet();
    var pending = new LongArrayList();
    component.add(start);
    pending.push(start);
    long anchor = WIDE;
    while (!pending.isEmpty()) {
      long position = pending.popLong();
      Entry cached = cached(position);
      if (cached != null) {
        anchor = cached.anchor();
        break;
      }
      int x = BlockPos.getX(position);
      int y = BlockPos.getY(position);
      int z = BlockPos.getZ(position);
      minX = Math.min(minX, x);
      maxX = Math.max(maxX, x);
      minZ = Math.min(minZ, z);
      maxZ = Math.max(maxZ, z);
      if ((Math.max(maxX - minX, maxZ - minZ) + 1) * width > MAXIMUM_SPAN
          || component.size() > MAXIMUM_NODES) break;
      for (Direction direction : NEIGHBORS) {
        int nextX = x + direction.getStepX();
        int nextY = y + direction.getStepY();
        int nextZ = z + direction.getStepZ();
        long next = BlockPos.asLong(nextX, nextY, nextZ);
        if (!component.contains(next) && occupied.occupied(nextX, nextY, nextZ)) {
          component.add(next);
          pending.push(next);
        }
      }
      if (pending.isEmpty()) anchor = center(component, minX + maxX, minZ + maxZ);
    }
    for (var entries = component.iterator(); entries.hasNext(); ) {
      long position = entries.nextLong();
      results.set(index(position), new Entry(position, anchor));
    }
    return anchor;
  }

  private static long center(LongOpenHashSet component, int centerX2, int centerZ2) {
    long best = 0;
    int minimumDistance = Integer.MAX_VALUE;
    int highest = Integer.MIN_VALUE;
    for (var entries = component.iterator(); entries.hasNext(); ) {
      long position = entries.nextLong();
      int x = 2 * BlockPos.getX(position) - centerX2;
      int z = 2 * BlockPos.getZ(position) - centerZ2;
      int distance = x * x + z * z;
      int y = BlockPos.getY(position);
      if (distance < minimumDistance
          || distance == minimumDistance && (y > highest || y == highest && position < best)) {
        best = position;
        minimumDistance = distance;
        highest = y;
      }
    }
    return best;
  }

  @Nullable
  private Entry cached(long position) {
    Entry entry = results.get(index(position));
    return entry != null && entry.position() == position ? entry : null;
  }

  private int index(long position) {
    long mixed = (position ^ position >>> 33) * 0xff51afd7ed558ccdL;
    return (int) (mixed ^ mixed >>> 33) & (results.length() - 1);
  }
}
