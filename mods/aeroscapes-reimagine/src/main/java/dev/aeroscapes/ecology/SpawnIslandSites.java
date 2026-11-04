package dev.aeroscapes.ecology;

import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.function.Supplier;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Native small islands need a complete physical ocean bed and room for their native decoration. */
public final class SpawnIslandSites {
  private static final ThreadLocal<SpawnIslandColumns> SEARCH = new ThreadLocal<>();

  private SpawnIslandSites() {}

  public static <T> T search(
      ChunkGenerator generator,
      RandomState random,
      LevelHeightAccessor height,
      Supplier<T> operation) {
    if (!(generator instanceof NoiseBasedChunkGenerator noise)) return operation.get();
    SpawnIslandColumns previous = SEARCH.get();
    SEARCH.set(new SpawnIslandColumns(noise, random, height));
    try {
      return operation.get();
    } finally {
      if (previous == null) SEARCH.remove();
      else SEARCH.set(previous);
    }
  }

  /** Native sea is exclusive; an owned basin includes the block at its named waterline. */
  public static int seaLevel(RandomState random, int x, int z, int fallback) {
    IslandTerrain terrain = IslandTerrain.from(random);
    if (terrain == null) return fallback;
    var basin = terrain.basin(x, z);
    return basin != null && basin.water(x, basin.waterline(), z) ? basin.waterline() + 1 : fallback;
  }

  public static boolean accepts(
      ChunkGenerator generator,
      RandomState random,
      LevelHeightAccessor height,
      int centerX,
      int centerZ,
      int radius) {
    IslandTerrain terrain = IslandTerrain.from(random);
    if (terrain == null || !(generator instanceof NoiseBasedChunkGenerator noise)) return false;
    var basin = terrain.basin(centerX, centerZ);
    if (basin == null) return false;
    int sea = basin.waterline();
    int physicalRadius = radius + 17;
    // Biome painting, tree fronds, mob groups and the 20-block lichen search spill beyond the core.
    int clearanceRadius = Math.max(physicalRadius, radius * 3 / 2) + 32;
    // Reject the same dense water footprint before asking for any native noise columns.
    for (int x = centerX - physicalRadius; x <= centerX + physicalRadius; x++)
      for (int z = centerZ - physicalRadius; z <= centerZ + physicalRadius; z++)
        if (distanceSquared(x, z, centerX, centerZ) <= physicalRadius * physicalRadius
            && !basin.water(x, sea, z)) return false;
    SpawnIslandColumns columns = SEARCH.get();
    if (columns == null || !columns.matches(noise, random, height))
      columns = new SpawnIslandColumns(noise, random, height);
    for (int x = centerX - clearanceRadius; x <= centerX + clearanceRadius; x += 8)
      for (int z = centerZ - clearanceRadius; z <= centerZ + clearanceRadius; z += 8) {
        if (distanceSquared(x, z, centerX, centerZ) > physicalRadius * physicalRadius) continue;
        if (columns.height(x, z) != sea + 1) return false;
      }
    for (int left = Math.floorDiv(centerX - clearanceRadius, 16) * 16;
        left <= centerX + clearanceRadius;
        left += 16)
      for (int front = Math.floorDiv(centerZ - clearanceRadius, 16) * 16;
          front <= centerZ + clearanceRadius;
          front += 16) {
        int right = Math.min(left + 15, centerX + clearanceRadius);
        int back = Math.min(front + 15, centerZ + clearanceRadius);
        int closestX = Math.clamp(centerX, left, right);
        int closestZ = Math.clamp(centerZ, front, back);
        if (distanceSquared(closestX, closestZ, centerX, centerZ)
            > clearanceRadius * clearanceRadius) continue;
        var tile = columns.tile(left, front);
        for (int x = left; x <= right; x++)
          for (int z = front; z <= back; z++) {
            int distance = distanceSquared(x, z, centerX, centerZ);
            if (distance > clearanceRadius * clearanceRadius) continue;
            if (tile.top(x, z) > sea
                || distance <= physicalRadius * physicalRadius
                    && (tile.top(x, z) != sea || !tile.waterBed(x, z))) return false;
          }
      }
    return true;
  }

  private static int distanceSquared(int x, int z, int centerX, int centerZ) {
    int dx = x - centerX;
    int dz = z - centerZ;
    return dx * dx + dz * dz;
  }
}
