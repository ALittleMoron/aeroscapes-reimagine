package dev.aeroscapes.worldgen;

import java.util.ArrayDeque;
import java.util.function.IntBinaryOperator;

/** A sampled, center-connected natural land disk; this does not generate Minecraft chunks. */
final class IslandFootprint {
  static final int RADIUS = 32;
  static final int STEP = 4;
  static final int NO_LAND = Integer.MIN_VALUE;
  private static final int MAXIMUM_NEIGHBOR_HEIGHT_DIFFERENCE = 8;
  private static final int GRID_RADIUS = RADIUS / STEP;
  private static final int WIDTH = GRID_RADIUS * 2 + 1;

  private IslandFootprint() {}

  static boolean accepts(IntBinaryOperator surfaceHeightAtOffset) {
    int[][] heights = new int[WIDTH][WIDTH];
    boolean[][] visited = new boolean[WIDTH][WIDTH];
    int diskSamples = 0;
    for (int x = -GRID_RADIUS; x <= GRID_RADIUS; x++) {
      for (int z = -GRID_RADIUS; z <= GRID_RADIUS; z++) {
        heights[x + GRID_RADIUS][z + GRID_RADIUS] =
            surfaceHeightAtOffset.applyAsInt(x * STEP, z * STEP);
        if (x * x + z * z <= GRID_RADIUS * GRID_RADIUS) {
          diskSamples++;
        }
      }
    }
    if (heights[GRID_RADIUS][GRID_RADIUS] == NO_LAND) {
      return false;
    }
    ArrayDeque<Integer> frontier = new ArrayDeque<>();
    frontier.add(GRID_RADIUS * WIDTH + GRID_RADIUS);
    visited[GRID_RADIUS][GRID_RADIUS] = true;
    int connectedSamples = 0;
    int minimumX = GRID_RADIUS;
    int maximumX = GRID_RADIUS;
    int minimumZ = GRID_RADIUS;
    int maximumZ = GRID_RADIUS;
    while (!frontier.isEmpty()) {
      int sample = frontier.remove();
      int x = sample / WIDTH;
      int z = sample % WIDTH;
      int offsetX = x - GRID_RADIUS;
      int offsetZ = z - GRID_RADIUS;
      if (offsetX * offsetX + offsetZ * offsetZ <= GRID_RADIUS * GRID_RADIUS) {
        connectedSamples++;
      }
      minimumX = Math.min(minimumX, x);
      maximumX = Math.max(maximumX, x);
      minimumZ = Math.min(minimumZ, z);
      maximumZ = Math.max(maximumZ, z);
      for (int neighbor : new int[] {sample - WIDTH, sample + WIDTH, sample - 1, sample + 1}) {
        int nextX = neighbor / WIDTH;
        int nextZ = neighbor % WIDTH;
        if (nextX >= 0
            && nextX < WIDTH
            && nextZ >= 0
            && nextZ < WIDTH
            && Math.abs(nextX - x) + Math.abs(nextZ - z) == 1
            && !visited[nextX][nextZ]
            && heights[nextX][nextZ] != NO_LAND
            && Math.abs(heights[nextX][nextZ] - heights[x][z])
                <= MAXIMUM_NEIGHBOR_HEIGHT_DIFFERENCE) {
          visited[nextX][nextZ] = true;
          frontier.add(neighbor);
        }
      }
    }
    return connectedSamples * 10 >= diskSamples * 7
        && Math.max(maximumX - minimumX, maximumZ - minimumZ) * STEP >= RADIUS * 2;
  }
}
