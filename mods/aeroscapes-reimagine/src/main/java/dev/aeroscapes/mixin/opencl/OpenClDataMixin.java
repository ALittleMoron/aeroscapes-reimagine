package dev.aeroscapes.mixin.opencl;

import com.ishland.c2me.opts.accel.opencl.common.compiler.GeneratedCLSource;
import com.ishland.c2me.opts.accel.opencl.common.compiler.emitters.misc.CLBlockStateMappings;
import com.ishland.c2me.opts.accel.opencl.common.gen.cache.Stage1Cache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.opencl.IslandOpenCl;
import java.nio.ByteBuffer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "com.ishland.c2me.opts.accel.opencl.common.gen.CLDataUtil", remap = false)
public abstract class OpenClDataMixin {
  @WrapMethod(method = "worldgen_data_root$createForFlatCacheOnly")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras wraps the native buffer factory.
  private static ByteBuffer aeroscapesReimagine$flatColumns(
      ChunkPos start,
      int size,
      GeneratedCLSource source,
      double[] surfaceHeights,
      boolean extraColumn,
      Operation<ByteBuffer> original) {
    return original.call(
        start, size, IslandOpenCl.withFlatCacheMetadata(source), surfaceHeights, extraColumn);
  }

  @WrapMethod(method = "worldgen_data_root$createForArea")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras wraps the native batch buffer factory.
  private static ByteBuffer aeroscapesReimagine$areaColumns(
      ChunkPos start,
      int size,
      StaticCache2D<ProtoChunk> chunks,
      NoiseBasedChunkGenerator generator,
      RandomState state,
      StaticCache2D<StructureManager> structures,
      CLBlockStateMappings mappings,
      GeneratedCLSource source,
      Stage1Cache.AreaCacheEntry surfaceHeights,
      Operation<ByteBuffer> original) {
    if (IslandTerrain.from(state) != null
        && !generator.generatorSettings().value().defaultBlock().is(Blocks.STONE))
      throw new IllegalStateException(
          "Island OpenCL basin liner requires the configured stone default");
    return original.call(
        start,
        size,
        chunks,
        generator,
        state,
        structures,
        mappings,
        IslandOpenCl.withColumns(source, start, size, structures),
        surfaceHeights);
  }
}
