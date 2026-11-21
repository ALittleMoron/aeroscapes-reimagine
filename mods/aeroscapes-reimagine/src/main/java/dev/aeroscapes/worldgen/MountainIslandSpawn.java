package dev.aeroscapes.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.neoforge.common.Tags;

/** Searches the seeded large-island family without changing terrain or the world seed. */
final class MountainIslandSpawn {
  static final int SEARCH_RADIUS = 12288;
  private static final int CANDIDATE_STEP = 64;
  private static final int HABITAT_RADIUS = 256;
  private static final int HABITAT_STEP = 32;
  private static final int BIOME_COUNT = 3;
  private static final int WOODLAND_RADIUS = 160;
  private static final int MAX_HEIGHT_DEVIATION = 32;
  private static final int PEAK_RELIEF = 48;
  private static final int MINIMUM_SURFACE_Y = 50;

  private MountainIslandSpawn() {}

  record Candidate(int x, int z, IslandLayout.LargeIsland island) {
    boolean accepts(IntBinaryOperator naturalHeight, BiomeSource biomes, Climate.Sampler sampler) {
      int spawnHeight = islandHeight(naturalHeight, island, x, z);
      if (spawnHeight == IslandFootprint.NO_LAND) return false;
      var frontier = new ArrayDeque<BlockPos>();
      Set<Long> sampled = new HashSet<>();
      Set<Holder<Biome>> habitats = new HashSet<>();
      add(frontier, sampled, x, spawnHeight, z);
      boolean mountain = false;
      boolean woodland = false;
      while (!frontier.isEmpty()) {
        BlockPos point = frontier.removeFirst();
        int sampleX = point.getX();
        int sampleZ = point.getZ();
        int sampleY = point.getY();
        var biome =
            biomes.getNoiseBiome(
                QuartPos.fromBlock(sampleX),
                QuartPos.fromBlock(sampleY - 1),
                QuartPos.fromBlock(sampleZ),
                sampler);
        if (biome.is(BiomeTags.IS_OVERWORLD) && !biome.is(BiomeTags.IS_OCEAN)) {
          habitats.add(biome);
          if (distanceSquared(x, z, sampleX, sampleZ) <= WOODLAND_RADIUS * WOODLAND_RADIUS
              && (biome.is(Tags.Biomes.IS_FOREST)
                  || biome.is(BiomeTags.IS_FOREST)
                  || biome.is(BiomeTags.IS_TAIGA)
                  || biome.is(BiomeTags.IS_JUNGLE))) {
            woodland = true;
          }
        }
        mountain |=
            sampleY >= spawnHeight + PEAK_RELIEF && island.peak(sampleX, sampleZ) >= PEAK_RELIEF;
        if (mountain && woodland && habitats.size() >= BIOME_COUNT) return true;
        visit(frontier, sampled, naturalHeight, sampleX + HABITAT_STEP, sampleY, sampleZ);
        visit(frontier, sampled, naturalHeight, sampleX - HABITAT_STEP, sampleY, sampleZ);
        visit(frontier, sampled, naturalHeight, sampleX, sampleY, sampleZ + HABITAT_STEP);
        visit(frontier, sampled, naturalHeight, sampleX, sampleY, sampleZ - HABITAT_STEP);
      }
      return false;
    }

    private void visit(
        ArrayDeque<BlockPos> frontier,
        Set<Long> sampled,
        IntBinaryOperator naturalHeight,
        int sampleX,
        int neighborY,
        int sampleZ) {
      if (distanceSquared(x, z, sampleX, sampleZ) > HABITAT_RADIUS * HABITAT_RADIUS
          || sampled.contains(ChunkPos.asLong(sampleX, sampleZ))) return;
      int height = islandHeight(naturalHeight, island, sampleX, sampleZ);
      if (height != IslandFootprint.NO_LAND && Math.abs(height - neighborY) <= HABITAT_STEP) {
        add(frontier, sampled, sampleX, height, sampleZ);
      }
    }
  }

  static List<Candidate> candidates(IslandLayout layout, BlockPos origin) {
    int originX = origin.getX();
    int originZ = origin.getZ();
    int minimumX = Math.floorDiv(originX - SEARCH_RADIUS, IslandLayout.LARGE_CELL_SIZE);
    int maximumX = Math.floorDiv(originX + SEARCH_RADIUS, IslandLayout.LARGE_CELL_SIZE);
    int minimumZ = Math.floorDiv(originZ - SEARCH_RADIUS, IslandLayout.LARGE_CELL_SIZE);
    int maximumZ = Math.floorDiv(originZ + SEARCH_RADIUS, IslandLayout.LARGE_CELL_SIZE);
    var candidates = new ArrayList<Candidate>();
    for (int cellX = minimumX; cellX <= maximumX; cellX++) {
      for (int cellZ = minimumZ; cellZ <= maximumZ; cellZ++) {
        var island =
            layout.largeIsland(
                cellX * IslandLayout.LARGE_CELL_SIZE, cellZ * IslandLayout.LARGE_CELL_SIZE);
        if (island == null || island.peakHeight() < PEAK_RELIEF) continue;
        int centerX = (int) island.centerX();
        int centerZ = (int) island.centerZ();
        int radiusX = (int) island.radiusX();
        int radiusZ = (int) island.radiusZ();
        for (int x = centerX - radiusX; x <= centerX + radiusX; x += CANDIDATE_STEP) {
          for (int z = centerZ - radiusZ; z <= centerZ + radiusZ; z += CANDIDATE_STEP) {
            if (distanceSquared(originX, originZ, x, z) <= SEARCH_RADIUS * SEARCH_RADIUS
                && island.radius(x, z) <= 0.65
                && island.peak(x, z) <= PEAK_RELIEF
                && island.surface(x, z) >= MINIMUM_SURFACE_Y) {
              candidates.add(new Candidate(x, z, island));
            }
          }
        }
      }
    }
    candidates.sort(
        Comparator.comparingLong(
            candidate -> distanceSquared(originX, originZ, candidate.x(), candidate.z())));
    return candidates;
  }

  private static int islandHeight(
      IntBinaryOperator naturalHeight, IslandLayout.LargeIsland island, int x, int z) {
    if (island.radius(x, z) >= 1) return IslandFootprint.NO_LAND;
    int height = naturalHeight.applyAsInt(x, z);
    return height != IslandFootprint.NO_LAND
            && Math.abs(height - island.surface(x, z)) <= MAX_HEIGHT_DEVIATION
        ? height
        : IslandFootprint.NO_LAND;
  }

  private static void add(ArrayDeque<BlockPos> frontier, Set<Long> sampled, int x, int y, int z) {
    sampled.add(ChunkPos.asLong(x, z));
    frontier.add(new BlockPos(x, y, z));
  }

  private static long distanceSquared(int firstX, int firstZ, int secondX, int secondZ) {
    long dx = (long) firstX - secondX;
    long dz = (long) firstZ - secondZ;
    return dx * dx + dz * dz;
  }
}
