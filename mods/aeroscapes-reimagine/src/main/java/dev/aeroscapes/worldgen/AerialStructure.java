package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Fits one native assembly into open sky, retaining its pieces and saved template identity. */
public final class AerialStructure extends Structure {
  public static final MapCodec<AerialStructure> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      Structure.DIRECT_CODEC.fieldOf("delegate").forGetter(value -> value.delegate),
                      com.mojang.serialization.Codec.intRange(128, 383)
                          .fieldOf("min_y")
                          .forGetter(value -> value.minimumY),
                      com.mojang.serialization.Codec.intRange(128, 383)
                          .fieldOf("max_y")
                          .forGetter(value -> value.maximumY),
                      com.mojang.serialization.Codec.intRange(1, 8)
                          .optionalFieldOf("clearance", 3)
                          .forGetter(value -> value.clearance))
                  .apply(instance, AerialStructure::new));
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<AerialStructure>> TYPE =
      TYPES.register("aerial", () -> () -> CODEC);
  private final Structure delegate;
  private final int minimumY;
  private final int maximumY;
  private final int clearance;

  public AerialStructure(Structure delegate, int minimumY, int maximumY, int clearance) {
    super(
        new StructureSettings(
            delegate.biomes(),
            delegate.spawnOverrides(),
            delegate.step(),
            delegate.terrainAdaptation()));
    if (minimumY > maximumY) throw new IllegalArgumentException("Empty aerial height band");
    this.delegate = delegate;
    this.minimumY = minimumY;
    this.maximumY = maximumY;
    this.clearance = clearance;
  }

  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Override
  protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    Optional<GenerationStub> nativeStub =
        delegate.findValidGenerationPoint(
            new GenerationContext(
                context.registryAccess(),
                context.chunkGenerator(),
                context.biomeSource(),
                context.randomState(),
                context.structureTemplateManager(),
                context.random(),
                context.seed(),
                context.chunkPos(),
                context.heightAccessor(),
                biome -> true));
    if (nativeStub.isEmpty()) return Optional.empty();
    GenerationStub stub = nativeStub.orElseThrow();
    var builder = stub.getPiecesBuilder();
    if (builder.build().isEmpty()) return Optional.empty();
    BoundingBox bounds = builder.getBoundingBox();
    int highestFloor =
        Math.min(
            maximumY,
            Math.min(383, context.heightAccessor().getMaxBuildHeight() - 1)
                - clearance
                - bounds.getYSpan()
                + 1);
    if (highestFloor < minimumY) return Optional.empty();
    int first = minimumY + context.random().nextInt(highestFloor - minimumY + 1);
    int[] floors = {first, highestFloor, minimumY, (minimumY + highestFloor) / 2};
    for (int floor : floors) {
      int offset = floor - bounds.minY();
      BoundingBox moved = bounds.moved(0, offset, 0);
      if (!clear(context, moved.inflatedBy(clearance))) continue;
      builder.build().pieces().forEach(piece -> SupportedStructure.movePiece(piece, 0, offset, 0));
      return Optional.of(
          new GenerationStub(stub.position().offset(0, offset, 0), Either.right(builder)));
    }
    return Optional.empty();
  }

  private static boolean clear(GenerationContext context, BoundingBox bounds) {
    if (context.heightAccessor().isOutsideBuildHeight(bounds.minY())
        || context.heightAccessor().isOutsideBuildHeight(bounds.maxY())) return false;
    if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator)) return false;
    IslandTerrain terrain = IslandTerrain.from(context.randomState());
    for (int x = bounds.minX(); x <= bounds.maxX(); x += 32)
      for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 32)
        for (int y = bounds.minY(); y <= bounds.maxY(); y += 24) {
          BoundingBox tile =
              new BoundingBox(
                  x,
                  y,
                  z,
                  Math.min(x + 31, bounds.maxX()),
                  Math.min(y + 23, bounds.maxY()),
                  Math.min(z + 31, bounds.maxZ()));
          NativeTerrainVolume volume =
              new NativeTerrainVolume(generator, context.randomState(), tile);
          for (int sampleX = tile.minX(); sampleX <= tile.maxX(); sampleX++)
            for (int sampleZ = tile.minZ(); sampleZ <= tile.maxZ(); sampleZ++) {
              IslandTerrain.Column column =
                  terrain == null ? null : terrain.column(sampleX, sampleZ);
              for (int sampleY = tile.minY(); sampleY <= tile.maxY(); sampleY++)
                if (!volume.block(sampleX, sampleY, sampleZ).isAir()
                    || column != null && (column.water(sampleY) || column.lava(sampleY)))
                  return false;
            }
        }
    return true;
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
