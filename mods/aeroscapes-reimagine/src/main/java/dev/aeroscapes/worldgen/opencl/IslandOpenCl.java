package dev.aeroscapes.worldgen.opencl;

import com.ishland.c2me.opts.accel.opencl.common.compiler.GeneratedCLSource;
import com.ishland.c2me.opts.accel.opencl.common.gen.CLDataUtil;
import com.ishland.c2me.opts.dfc.common.gen.opencl.OpenCLCEmitter;
import com.ishland.c2me.opts.dfc.common.gen.opencl.OpenCLCGenData;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.c2me.IslandTerrainNode;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import java.util.function.Function;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;

/** Optional integration with the exact pinned OpenCL module, without modifying its JAR. */
public final class IslandOpenCl {
  private IslandOpenCl() {}

  public static void register() {
    OpenCLCGenData.REGISTRY.registerExactMatch(
        IslandTerrainNode.class, (OpenCLCEmitter<IslandTerrainNode>) IslandOpenClEmitter::emit);
  }

  public static GeneratedCLSource withColumns(
      GeneratedCLSource source,
      ChunkPos start,
      int size,
      StaticCache2D<StructureManager> structures) {
    return withData(source, terrain -> IslandColumnData.create(terrain, start, size, structures));
  }

  public static GeneratedCLSource withFlatCacheMetadata(GeneratedCLSource source) {
    // Graph gates prove that this phase cannot sample an island column. Retain
    // its table slot without computing a whole cache tile's unused geometry.
    return withData(source, terrain -> new byte[IslandColumnData.HEADER_BYTES]);
  }

  private static GeneratedCLSource withData(
      GeneratedCLSource source, Function<IslandTerrain, byte[]> data) {
    Reference2IntLinkedOpenHashMap<Object> offsets = new Reference2IntLinkedOpenHashMap<>();
    boolean changed = false;
    for (var entry : source.getGlobalDynamicDataOffsets().reference2IntEntrySet()) {
      Object key = entry.getKey();
      if (key instanceof IslandTerrain terrain) {
        key = new CLDataUtil.ConstantBlob(data.apply(terrain), 8);
        changed = true;
      }
      offsets.put(key, entry.getIntValue());
    }
    return changed ? copy(source, source.getGeneratedSource(), offsets) : source;
  }

  public static GeneratedCLSource withKernelHooks(GeneratedCLSource source) {
    int terrainOffset = -1;
    for (var entry : source.getGlobalDynamicDataOffsets().reference2IntEntrySet()) {
      if (entry.getKey() instanceof IslandTerrain) {
        if (terrainOffset >= 0) throw new IllegalStateException("Multiple island terrain roots");
        terrainOffset = entry.getIntValue();
      }
    }
    return terrainOffset < 0
        ? copy(
            source,
            "#define AEROSCAPES_CITY_POST(value, ctx)\n" + source.getGeneratedSource(),
            source.getGlobalDynamicDataOffsets())
        : copy(
            source,
            IslandOpenClKernel.adapt(source.getGeneratedSource(), terrainOffset),
            source.getGlobalDynamicDataOffsets());
  }

  private static GeneratedCLSource copy(
      GeneratedCLSource source, String kernel, Reference2IntLinkedOpenHashMap<Object> offsets) {
    return new GeneratedCLSource(
        source.getOrdinal(),
        kernel,
        source.getConstData(),
        offsets,
        source.getFlatCachePrefills(),
        source.getCache2dPrefills(),
        source.getInterpolatorPrefills(),
        source.getDefines(),
        source.getBiomeMappings(),
        source.getDumpedPath());
  }
}
