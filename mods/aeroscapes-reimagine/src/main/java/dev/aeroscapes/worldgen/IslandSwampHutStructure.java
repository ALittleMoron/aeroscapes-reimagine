package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutStructure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Retains native first-chunk height averaging only where all four stilts meet natural ground. */
public final class IslandSwampHutStructure extends SwampHutStructure {
  public static final MapCodec<IslandSwampHutStructure> CODEC =
      simpleCodec(IslandSwampHutStructure::new);
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<IslandSwampHutStructure>>
      TYPE = TYPES.register("island_swamp_hut", () -> () -> CODEC);

  public IslandSwampHutStructure(StructureSettings settings) {
    super(settings);
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
    if (!context
        .validBiome()
        .test(
            context
                .biomeSource()
                .getNoiseBiome(
                    QuartPos.fromBlock(anchor.getX()), QuartPos.fromBlock(anchor.getY()),
                    QuartPos.fromBlock(anchor.getZ()), context.randomState().sampler())))
      return Optional.empty();
    var builder = stub.getPiecesBuilder();
    StructurePiece piece = builder.build().pieces().getFirst();
    var bounds = piece.getBoundingBox();
    Map<Long, NoiseColumn> columns = new HashMap<>();
    // The first postProcess callback averages only its intersection with the
    // footprint. Validate each possible first chunk, preserving that native rule.
    for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++) {
      for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++) {
        int sum = 0;
        int count = 0;
        for (int x = Math.max(bounds.minX(), chunkX * 16);
            x <= Math.min(bounds.maxX(), chunkX * 16 + 15);
            x++) {
          for (int z = Math.max(bounds.minZ(), chunkZ * 16);
              z <= Math.min(bounds.maxZ(), chunkZ * 16 + 15);
              z++) {
            NoiseColumn column = column(context, columns, x, z);
            int height = context.heightAccessor().getMaxBuildHeight();
            while (height > context.heightAccessor().getMinBuildHeight()
                && !Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
                    .isOpaque()
                    .test(column.getBlock(height - 1))) height--;
            if (height <= context.heightAccessor().getMinBuildHeight()) return Optional.empty();
            sum += height;
            count++;
          }
        }
        int average = sum / count;
        if (average < 0
            || context.heightAccessor().isOutsideBuildHeight(average)
            || context.heightAccessor().isOutsideBuildHeight(average + bounds.getYSpan() - 1)) {
          return Optional.empty();
        }
        for (int localX : new int[] {1, 5}) {
          for (int localZ : new int[] {2, 7}) {
            BlockPos leg = piece.getWorldPos(localX, -1, localZ);
            NoiseColumn column = column(context, columns, leg.getX(), leg.getZ());
            boolean bed = false;
            for (int y = average - 1; y > context.heightAccessor().getMinBuildHeight() + 1; y--) {
              if (!piece.isReplaceableByStructures(column.getBlock(y))) {
                bed = NativeTerrainVolume.solid(column.getBlock(y));
                break;
              }
            }
            if (!bed) return Optional.empty();
          }
        }
      }
    }
    return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
  }

  private static NoiseColumn column(
      GenerationContext context, Map<Long, NoiseColumn> columns, int x, int z) {
    return columns.computeIfAbsent(
        ChunkPos.asLong(x, z),
        ignored ->
            context
                .chunkGenerator()
                .getBaseColumn(x, z, context.heightAccessor(), context.randomState()));
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }
}
