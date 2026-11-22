package dev.aeroscapes.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class IslandStopStructure extends Structure {
  private static final MapCodec<IslandStopStructure> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      settingsCodec(instance),
                      StringRepresentable.fromEnum(Kind::values)
                          .fieldOf("kind")
                          .forGetter(structure -> structure.kind))
                  .apply(instance, IslandStopStructure::new));
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredRegister<StructurePieceType> PIECES =
      DeferredRegister.create(Registries.STRUCTURE_PIECE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<IslandStopStructure>> TYPE =
      TYPES.register("island_stop", () -> () -> CODEC);
  private static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE =
      PIECES.register("island_stop", () -> (context, tag) -> new Piece(tag));

  private static final int[][] CENTERS = {
    {7, 7}, {4, 4}, {11, 4}, {4, 11}, {11, 11}, {7, 4}, {11, 7}, {4, 7}, {7, 11}
  };
  private static final int[][] LANDING_CENTERS = {
    {6, 7}, {9, 7}, {7, 6}, {7, 9},
    {6, 4}, {9, 4}, {4, 6}, {4, 9},
    {6, 11}, {9, 11}, {11, 6}, {11, 9}
  };
  private static final ResourceLocation LANDING_ID =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "landing_deck");
  private static final ResourceLocation DEN_ID =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "fox_den");

  enum Kind implements StringRepresentable {
    SURVEY,
    GARDEN,
    DEN,
    LANDING;

    @Override
    public String getSerializedName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  private final Kind kind;

  private IslandStopStructure(StructureSettings settings, Kind kind) {
    super(settings);
    this.kind = kind;
  }

  public static void register(IEventBus bus) {
    TYPES.register(bus);
    PIECES.register(bus);
    NeoForge.EVENT_BUS.addListener(IslandStopStructure::addSurveyNote);
  }

  private static void addSurveyNote(ChunkEvent.Load event) {
    if (!event.isNewChunk() || !(event.getChunk() instanceof LevelChunk chunk)) return;
    for (var start : chunk.getAllStarts().values()) {
      if (start.isValid()
          && start.getPieces().getFirst() instanceof Piece piece
          && piece.kind == Kind.SURVEY) {
        SurveyNote.place(chunk, piece.site);
      }
    }
  }

  public static void prepareLanding(WorldGenRegion level, ChunkAccess chunk) {
    var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LANDING_ID);
    if (structure == null) return;
    var landing = stop(level, chunk, structure, Kind.LANDING);
    // Adjacent decoration can write here as soon as this chunk finishes carving.
    if (landing != null && !landing.hasLanding()) landing.layLanding();
  }

  public static void finishStopPlants(WorldGenRegion level, ChunkAccess center) {
    var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    var landingType = structures.get(LANDING_ID);
    var denType = structures.get(DEN_ID);
    if (landingType == null && denType == null) return;
    ChunkPos.rangeClosed(center.getPos(), 1)
        .forEach(
            position -> {
              var chunk = level.getChunk(position.x, position.z, ChunkStatus.CARVERS, false);
              if (chunk == null || chunk.getPersistedStatus() == ChunkStatus.FULL) return;
              var landing = stop(level, chunk, landingType, Kind.LANDING);
              if (landing != null && landing.hasLanding()) landing.clearLandingApproach();
              var den = stop(level, chunk, denType, Kind.DEN);
              if (den != null && den.hasDen()) den.clearDenOpening();
            });
  }

  private static IslandStop stop(
      WorldGenRegion level, ChunkAccess chunk, Structure structure, Kind kind) {
    if (structure == null) return null;
    var start = chunk.getStartForStructure(structure);
    if (start == null || !start.isValid()) return null;
    return start.getPieces().getFirst() instanceof Piece piece
            && piece.kind == kind
            && chunk.getPos().equals(new ChunkPos(piece.site.anchor()))
        ? new IslandStop(level, piece.site)
        : null;
  }

  @Override
  protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    var terrain = IslandTerrain.from(context.randomState());
    if (terrain == null
        || !(context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator)) {
      return Optional.empty();
    }
    var chunk = context.chunkPos();
    var height = context.heightAccessor();
    int bottom = height.getMinBuildHeight() + IslandStop.FOUNDATION_DEPTH + 1;
    int first =
        context.random().nextInt(kind == Kind.LANDING ? LANDING_CENTERS.length : CENTERS.length);
    for (int[] center : CENTERS) {
      if (generator.getBaseHeight(
              chunk.getBlockX(center[0]),
              chunk.getBlockZ(center[1]),
              Heightmap.Types.OCEAN_FLOOR_WG,
              height,
              context.randomState())
          > bottom) {
        var raw =
            NativeTerrainVolume.nativeColumns(
                generator,
                context.randomState(),
                height,
                new BoundingBox(
                    chunk.getMinBlockX(),
                    bottom,
                    chunk.getMinBlockZ(),
                    chunk.getMaxBlockX(),
                    height.getMaxBuildHeight() - 1,
                    chunk.getMaxBlockZ()));
        return fit(context, terrain, raw, bottom, first);
      }
    }
    return Optional.empty();
  }

  private Optional<GenerationStub> fit(
      GenerationContext context,
      IslandTerrain terrain,
      NativeTerrainVolume raw,
      int bottom,
      int first) {
    var chunk = context.chunkPos();
    var centers = kind == Kind.LANDING ? LANDING_CENTERS : CENTERS;
    for (int attempt = 0; attempt < centers.length; attempt++) {
      var center = centers[(first + attempt) % centers.length];
      int x = chunk.getBlockX(center[0]);
      int z = chunk.getBlockZ(center[1]);
      for (int y = context.heightAccessor().getMaxBuildHeight() - 10; y >= bottom; y--) {
        if (!NativeTerrainVolume.solid(raw.block(x, y, z))
            || !raw.block(x, y + 1, z).isAir()
            || terrain.compute(new SinglePointContext(x, y + 4, z)) > 0
            || !habitat(context, raw, x, y, z)) continue;
        var anchor = new BlockPos(x, y, z);
        var facing = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
        int turns = kind == Kind.SURVEY ? 1 : 4;
        for (int turn = 0; turn < turns; turn++, facing = facing.getClockWise()) {
          var site = new IslandStop.Site(anchor, facing);
          if (site.fits(raw, chunk, kind)) {
            return Optional.of(
                new GenerationStub(anchor, builder -> builder.addPiece(new Piece(kind, site))));
          }
        }
      }
    }
    return Optional.empty();
  }

  private boolean habitat(GenerationContext context, NativeTerrainVolume raw, int x, int y, int z) {
    var biome =
        context
            .biomeSource()
            .getNoiseBiome(
                QuartPos.fromBlock(x),
                QuartPos.fromBlock(y + 1),
                QuartPos.fromBlock(z),
                context.randomState().sampler());
    if (!context.validBiome().test(biome)
        || biome.is(Tags.Biomes.IS_CAVE)
        || biome.is(Tags.Biomes.IS_UNDERGROUND)) return false;
    boolean marineShore = biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_OCEAN);
    return switch (kind) {
      case GARDEN ->
          !marineShore
              && biome.value().getPrecipitationAt(new BlockPos(x, y + 1, z))
                  == Biome.Precipitation.RAIN
              && NativeTerrainVolume.solid(raw.block(x, y - 1, z));
      case DEN ->
          !marineShore
              && (biome.is(BiomeTags.IS_FOREST)
                  || biome.is(BiomeTags.IS_TAIGA)
                  || biome.value().hasPrecipitation()
                      && biome.value().getBaseTemperature() <= 0.9F);
      case LANDING -> !marineShore;
      case SURVEY -> !biome.is(BiomeTags.IS_OCEAN);
    };
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }

  private static final class Piece extends StructurePiece {
    private final Kind kind;
    private final IslandStop.Site site;

    Piece(Kind kind, IslandStop.Site site) {
      super(PIECE.get(), 0, site.bounds(kind));
      this.kind = kind;
      this.site = site;
    }

    Piece(CompoundTag tag) {
      super(PIECE.get(), tag);
      kind = Kind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT));
      site =
          new IslandStop.Site(
              BlockPos.of(tag.getLong("anchor")), Direction.from2DDataValue(tag.getInt("facing")));
    }

    @Override
    protected void addAdditionalSaveData(
        StructurePieceSerializationContext context, CompoundTag tag) {
      tag.putString("kind", kind.getSerializedName());
      tag.putLong("anchor", site.anchor().asLong());
      tag.putInt("facing", site.forward().get2DDataValue());
    }

    @Override
    public void postProcess(
        WorldGenLevel level,
        StructureManager manager,
        ChunkGenerator generator,
        RandomSource random,
        BoundingBox chunkBounds,
        ChunkPos chunk,
        BlockPos pivot) {
      if (chunk.equals(new ChunkPos(site.anchor())))
        new IslandStop(level, site).build(kind, random);
    }
  }
}
