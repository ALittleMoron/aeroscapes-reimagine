package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.ToIntBiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckPieces;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckStructure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Checks the native late mean-height placement rather than the ship's placeholder Y90. */
public final class IslandShipwreckStructure extends ShipwreckStructure {
  public static final MapCodec<IslandShipwreckStructure> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      settingsCodec(instance),
                      Codec.BOOL.fieldOf("is_beached").forGetter(value -> value.isBeached))
                  .apply(instance, IslandShipwreckStructure::new));
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<IslandShipwreckStructure>>
      TYPE = TYPES.register("island_shipwreck", () -> () -> CODEC);
  private static final int SAMPLE_STEP = 4;

  public IslandShipwreckStructure(StructureSettings settings, boolean beached) {
    super(settings, beached);
  }

  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Override
  public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    Optional<GenerationStub> nativeStub = super.findGenerationPoint(context);
    if (nativeStub.isEmpty() || isBeached) return nativeStub;
    GenerationStub stub = nativeStub.orElseThrow();
    BlockPos anchor = stub.position();
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
    var builder = stub.getPiecesBuilder();
    Map<Long, NoiseColumn> columns = new HashMap<>();
    BiFunction<Integer, Integer, NoiseColumn> naturalColumns =
        (x, z) ->
            columns.computeIfAbsent(
                ChunkPos.asLong(x, z),
                ignored ->
                    context
                        .chunkGenerator()
                        .getBaseColumn(x, z, context.heightAccessor(), context.randomState()));
    for (StructurePiece piece : builder.build().pieces()) {
      if (!(piece instanceof ShipwreckPieces.ShipwreckPiece shipwreck)) return Optional.empty();
      boolean large = shipwreck.isTooBigToFitInWorldGenRegion();
      ToIntBiFunction<Integer, Integer> heights =
          (x, z) ->
              large
                  ? context
                      .chunkGenerator()
                      .getFirstOccupiedHeight(
                          x,
                          z,
                          Heightmap.Types.WORLD_SURFACE_WG,
                          context.heightAccessor(),
                          context.randomState())
                  : context
                      .chunkGenerator()
                      .getBaseHeight(
                          x,
                          z,
                          Heightmap.Types.OCEAN_FLOOR_WG,
                          context.heightAccessor(),
                          context.randomState());
      int placement = nativePlacementY(shipwreck, heights);
      if (!supported(shipwreck, placement, context.heightAccessor(), naturalColumns))
        return Optional.empty();
    }
    return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
  }

  private static int nativePlacementY(
      ShipwreckPieces.ShipwreckPiece piece, ToIntBiFunction<Integer, Integer> heights) {
    if (piece.isTooBigToFitInWorldGenRegion()) {
      BoundingBox bounds = piece.getBoundingBox();
      // The pinned large branch uses four occupied WORLD_SURFACE corners, one
      // beyond each maximum edge; the small late branch instead averages first-free floor heights.
      int x = bounds.minX();
      int z = bounds.minZ();
      int width = bounds.getXSpan();
      int depth = bounds.getZSpan();
      return (heights.applyAsInt(x, z)
              + heights.applyAsInt(x, z + depth)
              + heights.applyAsInt(x + width, z)
              + heights.applyAsInt(x + width, z + depth))
          / 4;
    }
    BlockPos origin = piece.templatePosition();
    Vec3i size = piece.template().getSize();
    int samples = size.getX() * size.getZ();
    if (samples == 0) return heights.applyAsInt(origin.getX(), origin.getZ());
    int total = 0;
    // Native postProcess samples this unrotated rectangle even for a rotated hull.
    for (int x = origin.getX(); x < origin.getX() + size.getX(); x++) {
      for (int z = origin.getZ(); z < origin.getZ() + size.getZ(); z++)
        total += heights.applyAsInt(x, z);
    }
    return total / samples;
  }

  private static boolean supported(
      TemplateStructurePiece piece,
      int placement,
      LevelHeightAccessor height,
      BiFunction<Integer, Integer, NoiseColumn> columns) {
    BlockPos origin = piece.templatePosition();
    BoundingBox bounds =
        piece
            .template()
            .getBoundingBox(
                piece.placeSettings(), new BlockPos(origin.getX(), placement, origin.getZ()));
    int bedY = bounds.minY() - 4;
    if (height.isOutsideBuildHeight(bedY) || height.isOutsideBuildHeight(bounds.maxY()))
      return false;
    int tested = 0;
    int supported = 0;
    for (int x = bounds.minX(); ; x = Math.min(x + SAMPLE_STEP, bounds.maxX())) {
      for (int z = bounds.minZ(); ; z = Math.min(z + SAMPLE_STEP, bounds.maxZ())) {
        tested++;
        if (NativeTerrainVolume.solid(columns.apply(x, z).getBlock(bedY))) supported++;
        if (z == bounds.maxZ()) break;
      }
      if (x == bounds.maxX()) break;
    }
    // Preserve modest native seabed unevenness without accepting a mean hanging
    // between separate island layers or far above its basin floor.
    return supported * 5 >= tested * 3;
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }
}
