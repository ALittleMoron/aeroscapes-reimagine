package dev.aeroscapes.regression;

import dev.aeroscapes.worldgen.CarvingSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;

/** Checks actual block support and snapshot lifetime without generating another terrain volume. */
final class CarvingSupportRegression {
  private CarvingSupportRegression() {}

  static void verify(ServerLevel level) {
    var chunk =
        new ProtoChunk(
            new ChunkPos(-3, 5),
            UpgradeData.EMPTY,
            LevelHeightAccessor.create(-64, 64),
            level.registryAccess().registryOrThrow(Registries.BIOME),
            null);
    var rock = new BlockPos(-33, -64, 80);
    var water = rock.offset(-1, 1, 1);
    var upper = new BlockPos(-48, -1, 95);
    chunk.setBlockState(rock, Blocks.STONE.defaultBlockState(), false);
    chunk.setBlockState(water, Blocks.WATER.defaultBlockState(), false);
    chunk.setBlockState(upper, Blocks.DIRT.defaultBlockState(), false);
    for (int y = -64; y <= -33; y++)
      chunk.setBlockState(new BlockPos(-47, y, 80), Blocks.STONE.defaultBlockState(), false);
    for (int y = -64; y <= -34; y++)
      chunk.setBlockState(new BlockPos(-46, y, 80), Blocks.STONE.defaultBlockState(), false);
    for (int y = -32; y <= -17; y++)
      chunk.setBlockState(new BlockPos(-46, y, 80), Blocks.STONE.defaultBlockState(), false);
    var support = new CarvingSupport(chunk);
    require(
        support.solid(rock.getX(), rock.getY(), rock.getZ()), "Native rock supports a cave floor");
    require(
        support.solid(upper.getX(), upper.getY(), upper.getZ()),
        "Surface material is retained at the top and chunk corner");
    require(!support.solid(water.getX(), water.getY(), water.getZ()), "Fluid is not a solid floor");
    require(!support.solid(-33, -63, 80), "Air is not a solid floor");
    require(
        !support.solid(-33, -65, 80) && !support.solid(-33, 0, 80),
        "Height boundaries reject support");
    require(
        support.protectsRoof(-47, -33, 80)
            && support.protectsRoof(-47, -40, 80)
            && !support.protectsRoof(-47, -41, 80),
        "A continuous thick island keeps a roof and leaves rock below it available for carving");
    require(
        !support.protectsRoof(-46, -34, 80) && !support.protectsRoof(-46, -17, 80),
        "The gap between stacked thin islands does not count toward roof thickness");
    chunk.setBlockState(rock, Blocks.AIR.defaultBlockState(), false);
    chunk.setBlockState(water, Blocks.STONE.defaultBlockState(), false);
    chunk.setBlockState(new BlockPos(-47, -33, 80), Blocks.AIR.defaultBlockState(), false);
    require(support.protectsRoof(-47, -33, 80), "Roof decisions use the pre-carving terrain");
    require(
        support.solid(rock.getX(), rock.getY(), rock.getZ()),
        "Earlier carve writes cannot alter another cave's support decision");
    require(
        !support.solid(water.getX(), water.getY(), water.getZ()),
        "Later writes cannot invent original support");
    try {
      support.solid(-32, -64, 80);
      throw new IllegalStateException("Support accepted a different chunk");
    } catch (IllegalArgumentException expected) {
      // Chunk-local storage must never wrap a neighbouring coordinate into its mask.
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
