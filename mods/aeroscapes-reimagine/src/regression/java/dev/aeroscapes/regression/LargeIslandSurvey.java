package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandLayout;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Captures decorated terrain for a coarse top and side view of a naturally generated island. */
final class LargeIslandSurvey {
  private static final int CELL = 1536;
  private static final int STEP = 96;
  private static final int REACH = 576;

  private LargeIslandSurvey() {}

  static void write(ServerLevel level, Path output) {
    var terrain = IslandTerrain.from(level.getChunkSource().randomState());
    if (terrain == null) throw new IllegalStateException("Missing owned island layout");
    var form =
        IslandLayout.LargeForm.valueOf(System.getProperty("aeroscapes.regression.islandForm"));
    var island = nearest(terrain.layout(), form);
    int centerX = (int) Math.round(island.centerX());
    int centerZ = (int) Math.round(island.centerZ());
    int low = Math.max(level.getMinBuildHeight(), (int) (island.anchor() - island.depth() - 28));
    int high =
        Math.min(level.getMaxBuildHeight() - 1, (int) (island.anchor() + island.peakHeight() + 28));
    var logger = LogUtils.getLogger();
    logger.info("Survey {} on seed {} at {}, {}", form, level.getSeed(), centerX, centerZ);
    int sampledLand = 0;
    try (var writer = Files.newBufferedWriter(output)) {
      writer.write("x,z,top,bottom,block,biome\n");
      for (int dz = -REACH; dz <= REACH; dz += STEP) {
        for (int dx = -REACH; dx <= REACH; dx += STEP) {
          int x = centerX + dx;
          int z = centerZ + dz;
          var chunk = level.getChunk(x >> 4, z >> 4);
          var position = new BlockPos.MutableBlockPos();
          int top = low - 1;
          int bottom = high + 1;
          String block = "";
          String biome = "";
          for (int y = high; y >= low; y--) {
            position.set(x, y, z);
            var state = chunk.getBlockState(position);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (top < low) {
              top = y;
              block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
              biome = level.getBiome(position).unwrapKey().orElseThrow().location().toString();
            }
            bottom = y;
          }
          if (top >= low) sampledLand++;
          writer.write(x + "," + z + "," + top + "," + bottom + "," + block + "," + biome + "\n");
        }
      }
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot write island survey: " + output, error);
    }
    if (sampledLand < 8)
      throw new IllegalStateException("Too little generated terrain for island form " + form);
    locateVillage(level);
    logger.info("Large island survey completed: {} sampled solid columns", sampledLand);
  }

  private static void locateVillage(ServerLevel level) {
    var id = ResourceLocation.parse("skyvillages:skyvillage");
    var structure =
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(id).orElseThrow();
    var origin = level.getSharedSpawnPos();
    var found =
        level
            .getChunkSource()
            .getGenerator()
            .findNearestMapStructure(level, HolderSet.direct(structure), origin, 100, false);
    if (found == null)
      throw new IllegalStateException("Cannot locate a Sky Village from spawn " + origin);
    var position = found.getFirst();
    var start = level.getChunk(position).getStartForStructure(structure.value());
    if (start == null || !start.isValid() || start.getPieces().size() < 2)
      throw new IllegalStateException("Sky Village has no assembled natural start at " + position);
    LogUtils.getLogger()
        .info(
            "Sky Village witness seed={} spawn={} found={} pieces={}",
            level.getSeed(),
            origin,
            position,
            start.getPieces().size());
  }

  private static IslandLayout.LargeIsland nearest(
      IslandLayout layout, IslandLayout.LargeForm form) {
    IslandLayout.LargeIsland nearest = null;
    double distance = Double.POSITIVE_INFINITY;
    for (int x = -6 * CELL; x <= 6 * CELL; x += CELL) {
      for (int z = -6 * CELL; z <= 6 * CELL; z += CELL) {
        var candidate = layout.largeIsland(x, z);
        if (candidate == null
            || candidate.form() != form
            || Math.max(candidate.radiusX(), candidate.radiusZ()) < 400) continue;
        double radius = Math.hypot(candidate.centerX(), candidate.centerZ());
        if (radius < distance) {
          nearest = candidate;
          distance = radius;
        }
      }
    }
    if (nearest == null) throw new IllegalStateException("No sample found for island form " + form);
    return nearest;
  }
}
