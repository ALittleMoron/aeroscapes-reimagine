package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.OceanRuinStructure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Rejects native ocean-ruin layouts whose late footprint scan descends into the void. */
public final class IslandOceanRuinStructure extends OceanRuinStructure {
  public static final MapCodec<IslandOceanRuinStructure> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      settingsCodec(instance),
                      OceanRuinStructure.Type.CODEC
                          .fieldOf("biome_temp")
                          .forGetter(value -> value.biomeTemp),
                      Codec.floatRange(0.0F, 1.0F)
                          .fieldOf("large_probability")
                          .forGetter(value -> value.largeProbability),
                      Codec.floatRange(0.0F, 1.0F)
                          .fieldOf("cluster_probability")
                          .forGetter(value -> value.clusterProbability))
                  .apply(instance, IslandOceanRuinStructure::new));
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<IslandOceanRuinStructure>>
      TYPE = TYPES.register("island_ocean_ruin", () -> () -> CODEC);

  public IslandOceanRuinStructure(
      StructureSettings settings,
      Type temperature,
      float largeProbability,
      float clusterProbability) {
    super(settings, temperature, largeProbability, clusterProbability);
  }

  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Override
  public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    Optional<GenerationStub> nativeStub = super.findGenerationPoint(context);
    if (nativeStub.isEmpty()) return Optional.empty();
    GenerationStub stub = nativeStub.orElseThrow();
    BlockPos anchor = stub.position();
    // Structure's final biome check otherwise runs only after this method. Keep
    // its exact predicate ahead of the expensive native assembly/terrain scan.
    if (!context
        .validBiome()
        .test(
            context
                .chunkGenerator()
                .getBiomeSource()
                .getNoiseBiome(
                    QuartPos.fromBlock(anchor.getX()),
                    QuartPos.fromBlock(anchor.getY()),
                    QuartPos.fromBlock(anchor.getZ()),
                    context.randomState().sampler()))) return Optional.empty();
    // Native pieces start at placeholder Y=90 and move during postProcess. Test
    // their prospective positions, not those placeholder boxes. Consume RNG once.
    StructurePiecesBuilder builder = stub.getPiecesBuilder();
    Map<Long, NoiseColumn> columns = new HashMap<>();
    for (StructurePiece piece : builder.build().pieces()) {
      if (!(piece instanceof TemplateStructurePiece template)
          || !supported(context, template, columns)) return Optional.empty();
    }
    return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
  }

  private static boolean supported(
      GenerationContext context, TemplateStructurePiece piece, Map<Long, NoiseColumn> columns) {
    BlockPos origin = piece.templatePosition();
    int initialY =
        context
            .chunkGenerator()
            .getBaseHeight(
                origin.getX(),
                origin.getZ(),
                Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(),
                context.randomState());
    int bottom = context.heightAccessor().getMinBuildHeight() + 1;
    if (initialY <= bottom) return false;
    BoundingBox bounds = piece.getBoundingBox();
    int lowest = 512;
    int depressedColumns = 0;
    boolean emptyBottom = false;
    for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
      for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
        final int columnX = x;
        final int columnZ = z;
        NoiseColumn column =
            columns.computeIfAbsent(
                ChunkPos.asLong(x, z),
                ignored ->
                    context
                        .chunkGenerator()
                        .getBaseColumn(
                            columnX, columnZ, context.heightAccessor(), context.randomState()));
        int y = initialY - 1;
        while (passesThrough(column.getBlock(y)) && y > bottom) y--;
        lowest = Math.min(lowest, y);
        emptyBottom |= y == bottom && passesThrough(column.getBlock(y));
        if (y < initialY - 3) depressedColumns++;
        // This is the exact native depressed-column threshold. Once crossed
        // with an empty bottom, later columns cannot restore a valid placement.
        if (emptyBottom && initialY - 1 - lowest > 2 && depressedColumns > bounds.getXSpan() - 3)
          return false;
      }
    }
    int placementY =
        initialY - 1 - lowest > 2 && depressedColumns > bounds.getXSpan() - 3
            ? lowest + 1
            : initialY;
    return !context.heightAccessor().isOutsideBuildHeight(placementY)
        && !context.heightAccessor().isOutsideBuildHeight(placementY + bounds.getYSpan() - 1);
  }

  private static boolean passesThrough(BlockState state) {
    return state.isAir() || state.getFluidState().is(FluidTags.WATER) || state.is(BlockTags.ICE);
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }
}
