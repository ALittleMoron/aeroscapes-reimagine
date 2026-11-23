package dev.aeroscapes.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AirborneExpeditionStructure extends Structure {
  private static final MapCodec<AirborneExpeditionStructure> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      settingsCodec(instance),
                      StringRepresentable.fromEnum(Kind::values)
                          .fieldOf("kind")
                          .forGetter(structure -> structure.kind))
                  .apply(instance, AirborneExpeditionStructure::new));
  private static final String MOD_ID = "aeroscapes_reimagine";
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, MOD_ID);
  private static final DeferredRegister<StructurePieceType> PIECES =
      DeferredRegister.create(Registries.STRUCTURE_PIECE, MOD_ID);
  private static final DeferredHolder<StructureType<?>, StructureType<AirborneExpeditionStructure>>
      TYPE = TYPES.register("airborne_expedition", () -> () -> CODEC);
  private static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE =
      PIECES.register("airborne_expedition", () -> (context, tag) -> new Piece(tag));

  enum Kind implements StringRepresentable {
    DERELICT,
    TETHERED,
    OBSERVATION,
    CARGO_BARGE,
    CLIFF_SKIFF,
    SALVAGE_TUG,
    SURVEY_CATAMARAN,
    MAIL_CUTTER,
    PASSENGER_FERRY,
    WEATHER_SHIP,
    GLASSHOUSE_CARRIER;

    boolean isShipVariant() {
      return switch (this) {
        case DERELICT, TETHERED, OBSERVATION -> false;
        default -> true;
      };
    }

    @Override
    public String getSerializedName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  private final Kind kind;

  private AirborneExpeditionStructure(StructureSettings settings, Kind kind) {
    super(settings);
    this.kind = kind;
  }

  static void register(IEventBus bus) {
    TYPES.register(bus);
    PIECES.register(bus);
  }

  public static List<BoundingBox> airframes(StructureManager structures, ChunkPos column) {
    return structures
        .startsForStructure(column, structure -> structure instanceof AirborneExpeditionStructure)
        .stream()
        .map(start -> (Piece) start.getPieces().getFirst())
        .map(piece -> piece.site.airframeBounds())
        .toList();
  }

  @Override
  protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator)
        || IslandTerrain.from(context.randomState()) == null) return Optional.empty();
    var horizontal = context.chunkPos().getMiddleBlockPosition(0);
    var facing = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
    if (kind == Kind.TETHERED) return ledge(context, generator, horizontal, facing);
    int deck = 155 + context.random().nextInt(100);
    for (int height : new int[] {deck, 252, 282}) {
      var site = new AirborneExpedition.Site(horizontal.atY(height), facing, kind);
      if (AerialStructure.clear(context, site.flightSpace())) return start(site);
    }
    return Optional.empty();
  }

  private Optional<GenerationStub> ledge(
      GenerationContext context,
      NoiseBasedChunkGenerator generator,
      BlockPos center,
      Direction facing) {
    for (int xOffset : new int[] {-4, 0, 4}) {
      for (int zOffset : new int[] {-4, 0, 4}) {
        var probe = center.offset(xOffset, 0, zOffset);
        int y = ground(context, generator, probe);
        if (y < 85 || y > 265) continue;
        var anchor = probe.atY(y);
        for (int turn = 0; turn < 4; turn++) {
          var site = new AirborneExpedition.Site(anchor, facing, kind);
          facing = facing.getClockWise();
          if (!eligibleLedge(context, generator, site)) continue;
          if (AerialStructure.clear(context, site.flightSpace())) return start(site);
        }
      }
    }
    return Optional.empty();
  }

  private static boolean eligibleLedge(
      GenerationContext context, NoiseBasedChunkGenerator generator, AirborneExpedition.Site site) {
    var anchor = site.origin();
    var biome =
        context
            .biomeSource()
            .getNoiseBiome(
                QuartPos.fromBlock(anchor.getX()),
                QuartPos.fromBlock(anchor.getY()),
                QuartPos.fromBlock(anchor.getZ()),
                context.randomState().sampler());
    if (!context.validBiome().test(biome)) return false;
    for (int x : new int[] {-1, 1}) {
      for (int z : new int[] {-2, 1}) {
        int floor = ground(context, generator, site.ledge(x, 0, z));
        if (floor < anchor.getY() - 1 || floor > anchor.getY()) return false;
      }
    }
    for (int x : new int[] {-2, 0, 2}) {
      if (ground(context, generator, site.ledge(x, 0, 5)) > anchor.getY() - 6) return false;
    }
    return AerialStructure.clear(context, site.ledgeApproach());
  }

  private static int ground(
      GenerationContext context, NoiseBasedChunkGenerator generator, BlockPos pos) {
    return generator.getBaseHeight(
            pos.getX(),
            pos.getZ(),
            Heightmap.Types.OCEAN_FLOOR_WG,
            context.heightAccessor(),
            context.randomState())
        - 1;
  }

  private Optional<GenerationStub> start(AirborneExpedition.Site site) {
    return Optional.of(
        new GenerationStub(site.origin(), builder -> builder.addPiece(new Piece(site))));
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }

  private static final class Piece extends StructurePiece {
    private final AirborneExpedition.Site site;

    Piece(AirborneExpedition.Site site) {
      super(PIECE.get(), 0, site.pieceBounds());
      this.site = site;
    }

    Piece(CompoundTag tag) {
      super(PIECE.get(), tag);
      site =
          new AirborneExpedition.Site(
              BlockPos.of(tag.getLong("origin")),
              Direction.from2DDataValue(tag.getInt("facing")),
              Kind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT)));
    }

    @Override
    protected void addAdditionalSaveData(
        StructurePieceSerializationContext context, CompoundTag tag) {
      tag.putLong("origin", site.origin().asLong());
      tag.putInt("facing", site.forward().get2DDataValue());
      tag.putString("kind", site.kind().getSerializedName());
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
      new AirborneExpedition(level, chunkBounds, site).build(random);
    }
  }
}
