package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class IslandDungeonFitRegression {
  private IslandDungeonFitRegression() {}

  static void verify(ServerLevel level) {
    if (level.getSeed() != 1)
      throw new IllegalArgumentException("Island dungeon fixture needs seed 1");
    var structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .getHolder(ResourceLocation.parse("terralith:underground/frosted_dungeon"))
            .orElseThrow()
            .value();
    var chunk = level.getChunk(38, 63, ChunkStatus.STRUCTURE_STARTS);
    var start = chunk.getStartForStructure(structure);
    if (start == null || !start.isValid()) {
      throw new IllegalStateException("Frosted dungeon has no natural start at chunk 38, 63");
    }

    var bounds = start.getPieces().getFirst().getBoundingBox();
    var source = level.getChunkSource();
    int samples = 0;
    int perimeter = 0;
    var floor = new BlockPos.MutableBlockPos();
    for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
      for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
        floor.set(x, bounds.minY() - 1, z);
        var column = source.getGenerator().getBaseColumn(x, z, level, source.randomState());
        if (!solid(column.getBlock(floor.getY()))) {
          throw new IllegalStateException("Frosted dungeon lacks raw support at " + floor);
        }
        samples++;
        if (x == bounds.minX() || x == bounds.maxX() || z == bounds.minZ() || z == bounds.maxZ()) {
          // Later caves can cut a pocket inside the perimeter without exposing the outer base.
          if (!solid(level.getBlockState(floor))) {
            throw new IllegalStateException("Frosted dungeon perimeter is exposed at " + floor);
          }
          perimeter++;
        }
      }
    }
    LogUtils.getLogger()
        .info(
            "Island dungeon fit regression passed: bounds={}, raw={}, full perimeter={}",
            bounds,
            samples,
            perimeter);
  }

  private static boolean solid(BlockState state) {
    return state.getFluidState().isEmpty()
        && state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP);
  }
}
