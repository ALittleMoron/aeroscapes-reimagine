package dev.aeroscapes.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GroundedRibbitPaths extends StructureProcessor {
  private static final GroundedRibbitPaths INSTANCE = new GroundedRibbitPaths();
  private static final MapCodec<GroundedRibbitPaths> CODEC = MapCodec.unit(INSTANCE);
  private static final DeferredRegister<StructureProcessorType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, "aeroscapes_reimagine");
  private static final DeferredHolder<
          StructureProcessorType<?>, StructureProcessorType<GroundedRibbitPaths>>
      TYPE = TYPES.register("grounded_ribbit_paths", () -> () -> CODEC);

  public static void register(IEventBus bus) {
    TYPES.register(bus);
  }

  private GroundedRibbitPaths() {}

  @Override
  protected StructureProcessorType<?> getType() {
    return TYPE.get();
  }

  @Override
  public List<StructureBlockInfo> finalizeProcessing(
      ServerLevelAccessor level,
      BlockPos origin,
      BlockPos center,
      List<StructureBlockInfo> original,
      List<StructureBlockInfo> processed,
      StructurePlaceSettings settings) {
    if (!(level instanceof WorldGenRegion region)) return processed;
    WorldGenLevel generation = region;
    if (IslandTerrain.from(generation.getLevel().getChunkSource().randomState()) == null)
      return processed;
    var planned = new HashMap<BlockPos, BlockState>();
    for (var block : processed) planned.put(block.pos(), block.state());
    return processed.stream()
        .filter(
            block -> {
              var state = block.state();
              if ((!state.is(Blocks.PODZOL) && !state.is(Blocks.COARSE_DIRT))
                  || !region.getCenter().equals(new ChunkPos(block.pos()))) return true;
              var below = block.pos().below();
              var footing = planned.get(below);
              if (footing == null) footing = level.getBlockState(below);
              return footing.isFaceSturdy(level, below, Direction.UP);
            })
        .toList();
  }
}
