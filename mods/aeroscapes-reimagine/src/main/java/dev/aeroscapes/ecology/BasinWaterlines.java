package dev.aeroscapes.ecology;

import dev.aeroscapes.worldgen.IslandLayout;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.ChunkPos;

/** Per-client-level immutable snapshots, safe for concurrent terrain render workers. */
public final class BasinWaterlines {
  private static final int CAPACITY = 256;
  private final AtomicReference<Map<Long, IslandLayout.Basin>> cells =
      new AtomicReference<>(Map.of());

  public int at(int blockX, int blockZ, int fallback) {
    var basin = cells.get().get(ChunkPos.asLong(blockX >> 10, blockZ >> 10));
    return basin != null && basin.water(blockX, basin.waterline(), blockZ)
        ? basin.waterline() + 1
        : fallback;
  }

  public void put(IslandLayout.Basin basin) {
    long key = ChunkPos.asLong(basin.centerX() >> 10, basin.centerZ() >> 10);
    Map<Long, IslandLayout.Basin> previous = cells.get();
    while (true) {
      if (basin.equals(previous.get(key))) return;
      var next = new LinkedHashMap<>(previous);
      next.put(key, basin);
      if (next.size() > CAPACITY) next.remove(next.keySet().iterator().next());
      if (cells.compareAndSet(previous, Collections.unmodifiableMap(next))) return;
      previous = cells.get();
    }
  }
}
