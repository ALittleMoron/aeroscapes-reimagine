package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Checks the native pyramid's late minimum-height scan and below-box treasure room. */
public final class IslandPyramidStructure extends DesertPyramidStructure {
  public static final MapCodec<IslandPyramidStructure> CODEC =
      simpleCodec(IslandPyramidStructure::new);
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<IslandPyramidStructure>>
      TYPE = TYPES.register("island_pyramid", () -> () -> CODEC);

  public IslandPyramidStructure(StructureSettings settings) {
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
    var piece = builder.build().pieces().getFirst();
    BoundingBox bounds = piece.getBoundingBox();
    int lowest = context.heightAccessor().getMaxBuildHeight();
    int bottom = context.heightAccessor().getMinBuildHeight();
    int firstHeight =
        context
            .chunkGenerator()
            .getBaseHeight(
                bounds.minX(),
                bounds.minZ(),
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                context.heightAccessor(),
                context.randomState());
    if (firstHeight - 16 < bottom) return Optional.empty();
    if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise))
      throw new IllegalStateException("Island pyramids require the pinned noise generator");
    var volume =
        NativeTerrainVolume.nativeColumns(
            noise, context.randomState(), context.heightAccessor(), bounds);
    for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
      for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
        int height = context.heightAccessor().getMaxBuildHeight();
        while (height > bottom
            && !Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
                .isOpaque()
                .test(volume.block(x, height - 1, z))) height--;
        // Native random burial is 0..2, and its treasure-room floor is local Y=-14.
        if (height - 16 < bottom) return Optional.empty();
        lowest = Math.min(lowest, height);
      }
    }
    // Native HPos values below zero are treated as uninitialized on every callback.
    if (lowest < 0 || context.heightAccessor().isOutsideBuildHeight(lowest + 14))
      return Optional.empty();
    for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
      for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
        // Native foundation starts at local -5. The lowest possible burial
        // gives a necessary natural bed for every one of its three native rolls.
        boolean bed = false;
        for (int y = lowest - 7; y > bottom + 1; y--) {
          if (!piece.isReplaceableByStructures(volume.block(x, y, z))) {
            bed = NativeTerrainVolume.solid(volume.block(x, y, z));
            break;
          }
        }
        if (!bed) return Optional.empty();
      }
    }
    return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }
}
