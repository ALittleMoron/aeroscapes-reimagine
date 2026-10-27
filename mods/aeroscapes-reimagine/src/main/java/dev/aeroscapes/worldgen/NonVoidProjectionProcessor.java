package dev.aeroscapes.worldgen;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Prevents selected terrain-matching ruins from projecting onto an empty world's lower bound. */
public final class NonVoidProjectionProcessor extends StructureProcessor {
  public static final NonVoidProjectionProcessor INSTANCE = new NonVoidProjectionProcessor();
  public static final MapCodec<NonVoidProjectionProcessor> CODEC = MapCodec.unit(INSTANCE);

  private static final DeferredRegister<StructureProcessorType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, "aeroscapes_reimagine");
  private static final DeferredHolder<
          StructureProcessorType<?>, StructureProcessorType<NonVoidProjectionProcessor>>
      TYPE = TYPES.register("non_void_projection", () -> () -> CODEC);

  private NonVoidProjectionProcessor() {}

  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Nullable
  @Override
  public StructureTemplate.StructureBlockInfo process(
      LevelReader level,
      BlockPos origin,
      BlockPos pivot,
      StructureTemplate.StructureBlockInfo original,
      StructureTemplate.StructureBlockInfo transformed,
      StructurePlaceSettings settings,
      @Nullable StructureTemplate template) {
    // Match the immediately following native TERRAIN_MATCHING GravityProcessor,
    // including its ServerLevel fallback, offset -1 and untransformed local Y.
    Heightmap.Types heightmap =
        level instanceof ServerLevel
            ? Heightmap.Types.WORLD_SURFACE
            : Heightmap.Types.WORLD_SURFACE_WG;
    int height = level.getHeight(heightmap, transformed.pos().getX(), transformed.pos().getZ());
    int projectedY = height - 1 + original.pos().getY();
    return height <= level.getMinBuildHeight() || level.isOutsideBuildHeight(projectedY)
        ? null
        : transformed;
  }

  @Override
  protected StructureProcessorType<?> getType() {
    return TYPE.get();
  }
}
