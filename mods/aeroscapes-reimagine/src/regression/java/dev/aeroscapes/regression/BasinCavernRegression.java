package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.CityCavern;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

/** Controlled cavern under a real basin, followed by an intentional leak to validate the oracle. */
final class BasinCavernRegression {
  private final ServerLevel level;
  private final List<BlockPos> cavity = new ArrayList<>();
  private final int floor;
  private final int bottom;
  private static final int X = 2600;
  private static final int Z = -4768;

  BasinCavernRegression(ServerLevel level) {
    this.level = level;
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    if (terrain == null) throw new IllegalStateException("Missing basin terrain");
    var basin = terrain.basin(X, Z);
    if (basin == null) throw new IllegalStateException("Missing natural basin control");
    floor = (int) Math.floor(basin.floor(X, Z));
    int lowestFloor = floor;
    for (int x = X - 16; x <= X + 16; x++)
      for (int z = Z - 16; z <= Z + 16; z++)
        lowestFloor = Math.min(lowestFloor, (int) Math.floor(basin.floor(x, z)));
    bottom = lowestFloor - 40;
    BoundingBox part = new BoundingBox(X - 4, bottom, Z - 4, X + 4, bottom + 1, Z + 4);
    CityCavern cavern = new CityCavern(List.of(part));
    int removedStone = 0;
    for (int x = X - 16; x <= X + 16; x++)
      for (int z = Z - 16; z <= Z + 16; z++) {
        require(cavern.separatesBasin(basin, x, z), "Controlled sealed cavern rejected");
        for (int y = bottom; y <= bottom + 14; y++) {
          BlockPos point = new BlockPos(x, y, z);
          if (cavern.contribution(x, y, z) >= 0 && !part.isInside(point)) continue;
          // Clearing every cavity voxel is stronger than subtracting its native density.
          // No protective stone is added; the basin's existing floor must retain the water.
          require(level.getFluidState(point).isEmpty(), "Control cavity already contains fluid");
          var state = level.getBlockState(point);
          if (state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
              || state.is(Blocks.BASALT)
              || state.is(Blocks.SMOOTH_BASALT)) removedStone++;
          cavity.add(point);
        }
      }
    require(removedStone > 100, "Control cavity did not intersect real basin rock");
    for (BlockPos point : cavity) level.setBlock(point, Blocks.AIR.defaultBlockState(), 3);
    BlockPos source = new BlockPos(X, floor + 1, Z);
    require(level.getFluidState(source).is(Fluids.WATER), "No real basin water above control");
    level.scheduleTick(source, Fluids.WATER, 1);
    LogUtils.getLogger()
        .info(
            "Polish controlled basin cavern voxels={} removedStone={}",
            cavity.size(),
            removedStone);
  }

  void verifySealedAndOpenLeak() {
    require(fluidCount() == 0, "Sealed basin cavern filled with water after ticks");
    var terrain = IslandTerrain.from(level.getChunkSource().randomState());
    if (terrain == null) throw new IllegalStateException("Missing basin terrain");
    CityCavern leaking = new CityCavern(List.of(new BoundingBox(X, bottom, Z, X, floor, Z)));
    require(
        !leaking.separatesBasin(terrain.basin(X, Z), X, Z),
        "Basin protection accepted a template opening through the floor");
    for (int y = bottom; y < floor; y++)
      for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
        require(
            level.getFluidState(new BlockPos(X, y, Z).relative(direction)).isEmpty(),
            "Intermediate fluid would invalidate the basin-only leak control");
    for (int y = bottom; y <= floor; y++)
      require(
          level.getFluidState(new BlockPos(X, y, Z)).isEmpty(),
          "Leak shaft already contains fluid");
    for (int y = bottom; y <= floor; y++)
      level.setBlock(new BlockPos(X, y, Z), Blocks.AIR.defaultBlockState(), 3);
    level.scheduleTick(new BlockPos(X, floor + 1, Z), Fluids.WATER, 1);
    LogUtils.getLogger()
        .info(
            "Polish sealed basin cavern dry after {} ticks; opening negative control", leakTicks());
  }

  void verifyLeakDetected() {
    int count = fluidCount();
    require(count > 0, "Water oracle missed the deliberately breached basin");
    int cavityTop =
        cavity.stream()
            .filter(point -> point.getX() == X && point.getZ() == Z)
            .mapToInt(BlockPos::getY)
            .max()
            .orElseThrow();
    for (int y = cavityTop; y <= floor + 1; y++)
      require(
          level.getFluidState(new BlockPos(X, y, Z)).is(net.minecraft.tags.FluidTags.WATER),
          "Leak control has no continuous water path from basin to cavity at Y" + y);
    LogUtils.getLogger().info("Polish basin leak negative control detected {} fluid voxels", count);
  }

  int leakTicks() {
    return (floor - bottom + 1) * Fluids.WATER.getTickDelay(level) + 100;
  }

  private int fluidCount() {
    int count = 0;
    for (BlockPos point : cavity) if (!level.getFluidState(point).isEmpty()) count++;
    return count;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
