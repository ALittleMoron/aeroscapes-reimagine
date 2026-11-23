package dev.aeroscapes.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.featuresize.FeatureSize;
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
                          .forGetter(structure -> structure.kind),
                      StringRepresentable.fromEnum(LandingScene::values)
                          .optionalFieldOf("scene", LandingScene.PLAIN)
                          .forGetter(structure -> structure.scene))
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
  private static final ThreadLocal<LandingSearch> LANDING_SEARCH = new ThreadLocal<>();
  private static final ThreadLocal<TreeLandingChunk> TREE_LANDING = new ThreadLocal<>();

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
  private final LandingScene scene;

  private IslandStopStructure(StructureSettings settings, Kind kind, LandingScene scene) {
    super(settings);
    this.kind = kind;
    this.scene = scene;
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
    for (var start : chunk.getAllStarts().values()) {
      if (start.isValid()
          && start.getPieces().getFirst() instanceof Piece piece
          && piece.kind == Kind.LANDING
          && chunk.getPos().equals(new ChunkPos(piece.site.anchor()))) {
        // Adjacent decoration can write here as soon as this chunk finishes carving.
        var landing = new IslandStop(level, piece.site);
        if (!landing.hasLanding()) landing.layLanding();
        if (piece.terrace != null) piece.terrace.prepare(level, piece.scene);
      }
    }
  }

  public static boolean treeBlocksLanding(
      WorldGenRegion level, BlockPos origin, int height, FeatureSize size) {
    int radius = 0;
    for (int layer = 0; layer <= height + 1; layer++) {
      radius = Math.max(radius, IslandStop.treeRadius(size, height, layer));
    }
    for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) {
      for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
        var site = preparedLanding(level, x, z);
        if (site != null && site.treeIntersectsLanding(origin, height, size)) return true;
      }
    }
    return false;
  }

  public static boolean reservedLandingTreeCell(WorldGenRegion level, BlockPos point) {
    var site = preparedLanding(level, point.getX() >> 4, point.getZ() >> 4);
    return site != null && site.containsLandingAir(point);
  }

  private static IslandStop.Site preparedLanding(WorldGenRegion level, int x, int z) {
    long position = ChunkPos.asLong(x, z);
    var cached = TREE_LANDING.get();
    if (cached != null && cached.region().get() == level && cached.position() == position)
      return cached.site();
    var chunk = level.getChunk(x, z, ChunkStatus.CARVERS, false);
    if (chunk == null) return null;
    IslandStop.Site site = null;
    for (var start : chunk.getAllStarts().values()) {
      if (start.isValid()
          && start.getPieces().getFirst() instanceof Piece piece
          && piece.kind == Kind.LANDING
          && new IslandStop(level, piece.site).hasLanding()) {
        site = piece.site;
        break;
      }
    }
    TREE_LANDING.set(new TreeLandingChunk(new WeakReference<>(level), position, site));
    return site;
  }

  private record TreeLandingChunk(
      WeakReference<WorldGenRegion> region, long position, IslandStop.Site site) {}

  public static void finishStopPlants(WorldGenRegion level, ChunkAccess center) {
    ChunkPos.rangeClosed(center.getPos(), 1)
        .forEach(
            position -> {
              var chunk = level.getChunk(position.x, position.z, ChunkStatus.CARVERS, false);
              if (chunk == null || chunk.getPersistedStatus() == ChunkStatus.FULL) return;
              for (var start : chunk.getAllStarts().values()) {
                if (start.isValid()
                    && start.getPieces().getFirst() instanceof Piece piece
                    && position.equals(new ChunkPos(piece.site.anchor()))) {
                  var stop = new IslandStop(level, piece.site);
                  if (piece.kind == Kind.LANDING && stop.hasLanding()) {
                    stop.clearLandingApproach();
                    if (piece.terrace != null) {
                      piece.terrace.restoreFloor(level, piece.scene);
                      piece.terrace.clearRoute(stop);
                    }
                  }
                  if (piece.kind == Kind.DEN && stop.hasDen()) stop.clearDenOpening();
                }
              }
            });
  }

  @Override
  protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    var terrain = IslandTerrain.from(context.randomState());
    if (terrain == null
        || !(context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator)) {
      return Optional.empty();
    }
    var height = context.heightAccessor();
    int bottom = height.getMinBuildHeight() + IslandStop.FOUNDATION_DEPTH + 1;
    if (kind == Kind.LANDING) return findLanding(context, terrain, generator, bottom);
    var raw = sample(context, generator, bottom);
    if (raw == null) return Optional.empty();
    int first = context.random().nextInt(CENTERS.length);
    for (int attempt = 0; attempt < CENTERS.length; attempt++) {
      var center = CENTERS[(first + attempt) % CENTERS.length];
      int x = context.chunkPos().getBlockX(center[0]);
      int z = context.chunkPos().getBlockZ(center[1]);
      for (int y = height.getMaxBuildHeight() - 10; y >= bottom; y--) {
        if (!surface(terrain, raw, x, y, z) || !habitat(context, raw, x, y, z)) continue;
        var anchor = new BlockPos(x, y, z);
        var facing = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
        int turns = kind == Kind.SURVEY ? 1 : 4;
        for (int turn = 0; turn < turns; turn++, facing = facing.getClockWise()) {
          var site = new IslandStop.Site(anchor, facing);
          if (site.fits(raw, context.chunkPos(), kind)) return stub(site, null);
        }
      }
    }
    return Optional.empty();
  }

  private Optional<GenerationStub> findLanding(
      GenerationContext context,
      IslandTerrain terrain,
      NoiseBasedChunkGenerator generator,
      int bottom) {
    // Random spread retries the set's alternatives in the same chunk. Share the
    // fit result, including failed attempts, without retaining the sampled volume.
    var search = LANDING_SEARCH.get();
    if (search == null || !search.matches(context, biomes())) {
      var raw = sample(context, generator, bottom);
      var fit = raw == null ? LandingFit.EMPTY : fitLanding(context, terrain, raw, bottom);
      search = new LandingSearch(context, biomes(), fit);
      LANDING_SEARCH.set(search);
    }
    var fit = search.fit();
    if (scene == LandingScene.PLAIN)
      return fit.deck() == null ? Optional.empty() : stub(fit.deck(), null);
    var terrace = scene == LandingScene.WATER_STOP ? fit.rainy() : fit.terrace();
    return terrace == null ? Optional.empty() : stub(terrace.site(), terrace);
  }

  private Optional<GenerationStub> stub(IslandStop.Site site, LandingScene.Terrace terrace) {
    return Optional.of(
        new GenerationStub(
            site.anchor(), builder -> builder.addPiece(new Piece(kind, site, scene, terrace))));
  }

  private static NativeTerrainVolume sample(
      GenerationContext context, NoiseBasedChunkGenerator generator, int bottom) {
    var height = context.heightAccessor();
    var chunk = context.chunkPos();
    for (int[] center : CENTERS) {
      if (generator.getBaseHeight(
              chunk.getBlockX(center[0]),
              chunk.getBlockZ(center[1]),
              Heightmap.Types.OCEAN_FLOOR_WG,
              height,
              context.randomState())
          > bottom) {
        return NativeTerrainVolume.nativeColumns(
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
      }
    }
    return null;
  }

  private LandingFit fitLanding(
      GenerationContext context, IslandTerrain terrain, NativeTerrainVolume raw, int bottom) {
    var chunk = context.chunkPos();
    int first = context.random().nextInt(LANDING_CENTERS.length);
    var biomes =
        new BiomeManager(
            (x, y, z) ->
                context.biomeSource().getNoiseBiome(x, y, z, context.randomState().sampler()),
            BiomeManager.obfuscateSeed(context.seed()));
    IslandStop.Site deck = null;
    LandingScene.Terrace terrace = null;
    for (int attempt = 0; attempt < LANDING_CENTERS.length; attempt++) {
      var center = LANDING_CENTERS[(first + attempt) % LANDING_CENTERS.length];
      int x = chunk.getBlockX(center[0]);
      int z = chunk.getBlockZ(center[1]);
      for (int y = context.heightAccessor().getMaxBuildHeight() - 10; y >= bottom; y--) {
        if (!surface(terrain, raw, x, y, z) || !habitat(context, raw, x, y, z)) continue;
        var anchor = new BlockPos(x, y, z);
        var facing = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
        for (int turn = 0; turn < 4; turn++, facing = facing.getClockWise()) {
          var site = new IslandStop.Site(anchor, facing);
          if (!site.fits(raw, chunk, kind)) continue;
          if (deck == null) deck = site;
          var candidate = LandingScene.Terrace.find(site, chunk, raw);
          if (candidate == null) continue;
          if (terrace == null) terrace = candidate;
          var gutter = candidate.pos(2, 1, 2);
          if (biomes.getBiome(gutter).value().getPrecipitationAt(gutter)
              == Biome.Precipitation.RAIN) {
            return new LandingFit(deck, terrace, candidate);
          }
        }
      }
    }
    return new LandingFit(deck, terrace, null);
  }

  private static boolean surface(
      IslandTerrain terrain, NativeTerrainVolume raw, int x, int y, int z) {
    return raw.solid(x, y, z)
        && raw.block(x, y + 1, z).isAir()
        && terrain.compute(new SinglePointContext(x, y + 4, z)) <= 0;
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

  private record LandingFit(
      IslandStop.Site deck, LandingScene.Terrace terrace, LandingScene.Terrace rainy) {
    private static final LandingFit EMPTY = new LandingFit(null, null, null);
  }

  private record LandingSearch(
      WeakReference<RandomState> random,
      WeakReference<ChunkGenerator> generator,
      WeakReference<HolderSet<Biome>> biomes,
      ChunkPos chunk,
      long seed,
      int min,
      int max,
      LandingFit fit) {
    LandingSearch(GenerationContext context, HolderSet<Biome> biomes, LandingFit fit) {
      this(
          new WeakReference<>(context.randomState()),
          new WeakReference<>(context.chunkGenerator()),
          new WeakReference<>(biomes),
          context.chunkPos(),
          context.seed(),
          context.heightAccessor().getMinBuildHeight(),
          context.heightAccessor().getMaxBuildHeight(),
          fit);
    }

    boolean matches(GenerationContext context, HolderSet<Biome> validBiomes) {
      return random.get() == context.randomState()
          && generator.get() == context.chunkGenerator()
          && biomes.get() == validBiomes
          && chunk.equals(context.chunkPos())
          && seed == context.seed()
          && min == context.heightAccessor().getMinBuildHeight()
          && max == context.heightAccessor().getMaxBuildHeight();
    }
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }

  private static final class Piece extends StructurePiece {
    private final Kind kind;
    private final IslandStop.Site site;
    private final LandingScene scene;
    private final LandingScene.Terrace terrace;

    Piece(Kind kind, IslandStop.Site site, LandingScene scene, LandingScene.Terrace terrace) {
      super(
          PIECE.get(),
          0,
          terrace == null
              ? site.bounds(kind)
              : BoundingBox.encapsulatingBoxes(List.of(site.bounds(kind), terrace.bounds()))
                  .orElseThrow());
      this.kind = kind;
      this.site = site;
      this.scene = scene;
      this.terrace = terrace;
    }

    Piece(CompoundTag tag) {
      super(PIECE.get(), tag);
      kind = Kind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT));
      site =
          new IslandStop.Site(
              BlockPos.of(tag.getLong("anchor")), Direction.from2DDataValue(tag.getInt("facing")));
      if (tag.contains("scene")) {
        scene = LandingScene.valueOf(tag.getString("scene").toUpperCase(Locale.ROOT));
        terrace =
            new LandingScene.Terrace(site, tag.getInt("scene_side"), tag.getInt("scene_rise"));
      } else {
        scene = LandingScene.PLAIN;
        terrace = null;
      }
    }

    @Override
    protected void addAdditionalSaveData(
        StructurePieceSerializationContext context, CompoundTag tag) {
      tag.putString("kind", kind.getSerializedName());
      tag.putLong("anchor", site.anchor().asLong());
      tag.putInt("facing", site.forward().get2DDataValue());
      if (terrace != null) {
        tag.putString("scene", scene.getSerializedName());
        tag.putInt("scene_side", terrace.side());
        tag.putInt("scene_rise", terrace.rise());
      }
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
      if (!chunk.equals(new ChunkPos(site.anchor()))) return;
      new IslandStop(level, site).build(kind, random);
      if (terrace != null) scene.build(level, terrace, random);
    }
  }
}
