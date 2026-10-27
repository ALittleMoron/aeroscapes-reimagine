package dev.aeroscapes.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Natural support for the pinned projected Nova roots, retaining native lazy assembly. */
public final class RootSupportedStructure extends Structure {
  public static final MapCodec<RootSupportedStructure> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      Structure.DIRECT_CODEC.fieldOf("delegate").forGetter(value -> value.delegate),
                      Codec.intRange(1, 384)
                          .fieldOf("root_height")
                          .forGetter(value -> value.rootHeight))
                  .apply(instance, RootSupportedStructure::new));
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<RootSupportedStructure>>
      TYPE = TYPES.register("root_supported", () -> () -> CODEC);

  private final Structure delegate;
  private final int rootHeight;

  public RootSupportedStructure(Structure delegate, int rootHeight) {
    super(
        new StructureSettings(
            delegate.biomes(),
            delegate.spawnOverrides(),
            delegate.step(),
            delegate.terrainAdaptation()));
    this.delegate = delegate;
    this.rootHeight = rootHeight;
  }

  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Override
  protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    Optional<GenerationStub> result = delegate.findValidGenerationPoint(context);
    if (result.isEmpty()) return result;
    BlockPos anchor = result.orElseThrow().position();
    // The pinned rigid roots have odd X/Z spans, groundLevelDelta=1 and
    // named-anchor Y=0 (or no named anchor). Native stub is their ground center.
    // Native JigsawPlacement retains its own world-height clamp for every child.
    if (context.heightAccessor().isOutsideBuildHeight(anchor.getY() - 1)
        || context.heightAccessor().isOutsideBuildHeight(anchor.getY() + rootHeight - 2)) {
      return Optional.empty();
    }
    int supportY =
        Math.max(
            anchor.getY() - 1,
            Math.min(
                anchor.getY(),
                context
                    .chunkGenerator()
                    .getBaseHeight(
                        anchor.getX(),
                        anchor.getZ(),
                        Heightmap.Types.OCEAN_FLOOR_WG,
                        context.heightAccessor(),
                        context.randomState())));
    if (context.heightAccessor().isOutsideBuildHeight(supportY - 4)) return Optional.empty();
    NoiseColumn column =
        context
            .chunkGenerator()
            .getBaseColumn(
                anchor.getX(), anchor.getZ(), context.heightAccessor(), context.randomState());
    return solidFloor(column.getBlock(supportY - 1)) && solidFloor(column.getBlock(supportY - 4))
        ? result
        : Optional.empty();
  }

  private static boolean solidFloor(BlockState state) {
    return state.getFluidState().isEmpty()
        && Block.isFaceFull(
            state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP);
  }

  @Override
  public void afterPlace(
      WorldGenLevel level,
      StructureManager structureManager,
      ChunkGenerator generator,
      RandomSource random,
      BoundingBox bounds,
      ChunkPos chunk,
      PiecesContainer pieces) {
    delegate.afterPlace(level, structureManager, generator, random, bounds, chunk, pieces);
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }
}
