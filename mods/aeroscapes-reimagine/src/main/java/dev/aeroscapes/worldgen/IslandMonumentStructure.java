package dev.aeroscapes.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Native wet monument on a real basin floor; saved older island monuments retain their layout. */
public final class IslandMonumentStructure extends OceanMonumentStructure {
  public static final MapCodec<IslandMonumentStructure> CODEC =
      simpleCodec(IslandMonumentStructure::new);
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(
          net.minecraft.core.registries.Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<IslandMonumentStructure>>
      TYPE = TYPES.register("island_monument", () -> () -> CODEC);
  private static final ThreadLocal<Integer> LOCAL_SEA_LEVEL = new ThreadLocal<>();

  public IslandMonumentStructure(Structure.StructureSettings settings) {
    super(settings);
  }

  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  /** Used only at the two pinned native monument water calls. */
  public static int seaLevel(int nativeSeaLevel) {
    Integer local = LOCAL_SEA_LEVEL.get();
    return local == null ? nativeSeaLevel : local;
  }

  @Override
  public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator))
      return Optional.empty();
    IslandTerrain terrain = IslandTerrain.from(context.randomState());
    if (terrain == null) return Optional.empty();
    int anchorX = context.chunkPos().getMinBlockX();
    int anchorZ = context.chunkPos().getMinBlockZ();
    if (terrain.basin(anchorX, anchorZ) == null) return Optional.empty();
    int[][] offsets = {
      {0, 0},
      {32, 0},
      {0, 32},
      {-32, 0},
      {0, -32},
      {32, 32},
      {-32, 32},
      {-32, -32},
      {32, -32},
      {64, 0},
      {0, 64},
      {-64, 0},
      {0, -64},
      {64, 64},
      {-64, 64},
      {-64, -64},
      {64, -64}
    };
    Map<BlockPos, Integer> floors = new HashMap<>();
    for (int[] offset : offsets) {
      int x = anchorX + offset[0] - 29;
      int z = anchorZ + offset[1] - 29;
      if (!keepsSpacing(context, x, z)) continue;
      OptionalInt floor = basinFloor(context, generator, terrain, x, z, floors);
      if (floor.isEmpty()) continue;
      BlockPos center =
          new BoundingBox(x - 6, floor.getAsInt() - 3, z - 6, x + 63, floor.getAsInt() + 26, z + 63)
              .getCenter();
      if (!context
          .validBiome()
          .test(
              context
                  .biomeSource()
                  .getNoiseBiome(
                      center.getX() >> 2,
                      center.getY() >> 2,
                      center.getZ() >> 2,
                      context.randomState().sampler()))) continue;
      // Select terrain before drawing native orientation/room RNG. The whole shifted
      // clearance stays within 100 blocks of its start, inside native references.
      Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
      Building building =
          new Building(context.random(), x, z, direction, floor.getAsInt() + 1 - 39);
      return Optional.of(
          new GenerationStub(
              building.getBoundingBox().getCenter(), builder -> builder.addPiece(building)));
    }
    return Optional.empty();
  }

  private static boolean keepsSpacing(GenerationContext context, int x, int z) {
    int originalX = context.chunkPos().getMinBlockX();
    int originalZ = context.chunkPos().getMinBlockZ();
    if (x == originalX - 29 && z == originalZ - 29) return true;
    var set =
        context
            .registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET)
            .get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("ocean_monuments"));
    if (set == null
        || !(set.placement() instanceof RandomSpreadStructurePlacement spread)
        || spread.spacing() != 32
        || spread.separation() != 5) return false;
    int regionX = Math.floorDiv(context.chunkPos().x, spread.spacing());
    int regionZ = Math.floorDiv(context.chunkPos().z, spread.spacing());
    if (!spread
        .getPotentialStructureChunk(context.seed(), context.chunkPos().x, context.chunkPos().z)
        .equals(context.chunkPos())) return false;
    // Keep every clearance corner strictly in the owning anchor's Voronoi region.
    // Neighbouring starts may shift too; disjoint regions prevent their overlap.
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        if (dx == 0 && dz == 0) continue;
        ChunkPos neighbor =
            spread.getPotentialStructureChunk(
                context.seed(),
                (regionX + dx) * spread.spacing(),
                (regionZ + dz) * spread.spacing());
        for (int cornerX : new int[] {x - 6, x + 63})
          for (int cornerZ : new int[] {z - 6, z + 63}) {
            long ownX = (long) cornerX - originalX;
            long ownZ = (long) cornerZ - originalZ;
            long otherX = (long) cornerX - neighbor.getMinBlockX();
            long otherZ = (long) cornerZ - neighbor.getMinBlockZ();
            if (ownX * ownX + ownZ * ownZ >= otherX * otherX + otherZ * otherZ) return false;
          }
      }
    return true;
  }

  private static OptionalInt basinFloor(
      GenerationContext context,
      NoiseBasedChunkGenerator generator,
      IslandTerrain terrain,
      int x,
      int z,
      Map<BlockPos, Integer> floors) {
    IslandLayout.Basin basin = terrain.basin(x + 29, z + 29);
    if (basin == null) return OptionalInt.empty();
    int minimumFloor = Integer.MAX_VALUE;
    int maximumFloor = Integer.MIN_VALUE;
    for (int sampleX = x - 6; ; sampleX = Math.min(sampleX + 8, x + 63)) {
      for (int sampleZ = z - 6; ; sampleZ = Math.min(sampleZ + 8, z + 63)) {
        if (basin.radius(sampleX, sampleZ) >= 1
            || basin.waterline() - Math.floor(basin.floor(sampleX, sampleZ)) < 30)
          return OptionalInt.empty();
        BlockPos key = new BlockPos(sampleX, basin.waterline(), sampleZ);
        Integer cached = floors.get(key);
        int floor;
        if (cached != null) floor = cached;
        else {
          NoiseColumn column =
              generator.getBaseColumn(
                  sampleX, sampleZ, context.heightAccessor(), context.randomState());
          floor = basin.waterline();
          while (floor > context.heightAccessor().getMinBuildHeight()
              && column.getBlock(floor).is(Blocks.WATER)) floor--;
          if (!NativeTerrainVolume.solid(column.getBlock(floor))) floor = Integer.MIN_VALUE;
          floors.put(key, floor);
        }
        if (floor == Integer.MIN_VALUE || basin.waterline() - floor < 30)
          return OptionalInt.empty();
        minimumFloor = Math.min(minimumFloor, floor);
        maximumFloor = Math.max(maximumFloor, floor);
        if (maximumFloor - minimumFloor > 18) return OptionalInt.empty();
        if (sampleZ == z + 63) break;
      }
      if (sampleX == x + 63) break;
    }
    return maximumFloor >= 39
            && maximumFloor + 26 < basin.waterline()
            && maximumFloor + 26 < context.heightAccessor().getMaxBuildHeight()
        ? OptionalInt.of(maximumFloor)
        : OptionalInt.empty();
  }

  /** Native loading regenerates room graphs; retain both the saved elevation and owned behavior. */
  public static PiecesContainer regenerate(ChunkPos chunk, long worldSeed, PiecesContainer saved) {
    if (saved.isEmpty()) return saved;
    // Every pre-adaptation native monument is fixed at Y39. New supported starts
    // require Y>=40, so old partial saves retain their original native water behavior.
    if (nativeBounds(saved.pieces().getFirst()).minY() <= 39) {
      return OceanMonumentStructure.regeneratePiecesAfterLoad(chunk, worldSeed, saved);
    }
    WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0));
    random.setLargeFeatureSeed(worldSeed, chunk.x, chunk.z);
    StructurePiece piece = saved.pieces().getFirst();
    BoundingBox bounds = nativeBounds(piece);
    Direction rolled = Direction.Plane.HORIZONTAL.getRandomDirection(random);
    Direction direction = Objects.requireNonNullElse(piece.getOrientation(), rolled);
    StructurePiecesBuilder builder = new StructurePiecesBuilder();
    builder.addPiece(
        new Building(random, bounds.minX(), bounds.minZ(), direction, bounds.minY() - 39));
    return builder.build();
  }

  private static BoundingBox nativeBounds(StructurePiece piece) {
    return piece instanceof Building building ? building.nativeBounds() : piece.getBoundingBox();
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }

  private static final class Building extends OceanMonumentPieces.MonumentBuilding {
    private Building(RandomSource random, int x, int z, Direction direction, int offset) {
      super(random, x, z, direction);
      move(0, offset, 0);
      for (StructurePiece child : childPieces) child.move(0, offset, 0);
    }

    private BoundingBox nativeBounds() {
      return boundingBox;
    }

    @Override
    public BoundingBox getBoundingBox() {
      return new BoundingBox(
          boundingBox.minX() - 6,
          boundingBox.minY() - 4,
          boundingBox.minZ() - 6,
          boundingBox.maxX() + 6,
          boundingBox.maxY() + 3,
          boundingBox.maxZ() + 6);
    }

    @Override
    public void postProcess(
        WorldGenLevel level,
        StructureManager structures,
        ChunkGenerator generator,
        RandomSource random,
        BoundingBox bounds,
        ChunkPos chunk,
        BlockPos origin) {
      Integer previous = LOCAL_SEA_LEVEL.get();
      LOCAL_SEA_LEVEL.set(boundingBox.minY() + 24);
      try {
        super.postProcess(level, structures, generator, random, bounds, chunk, origin);
        sealCarverHoles(level, generator, bounds);
      } finally {
        if (previous == null) LOCAL_SEA_LEVEL.remove();
        else LOCAL_SEA_LEVEL.set(previous);
      }
    }

    private void sealCarverHoles(
        WorldGenLevel level, ChunkGenerator generator, BoundingBox chunkBounds) {
      if (!(generator instanceof NoiseBasedChunkGenerator noise)) {
        throw new IllegalStateException("Island monuments require the pinned noise generator");
      }
      BoundingBox shell = getBoundingBox();
      int left = Math.max(shell.minX(), chunkBounds.minX());
      int right = Math.min(shell.maxX(), chunkBounds.maxX());
      int front = Math.max(shell.minZ(), chunkBounds.minZ());
      int back = Math.min(shell.maxZ(), chunkBounds.maxZ());
      if (left > right || front > back) return;
      NativeTerrainVolume columns =
          new NativeTerrainVolume(
              noise,
              level.getLevel().getChunkSource().randomState(),
              new BoundingBox(left, shell.minY(), front, right, shell.maxY(), back));
      BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
      for (int x = left; x <= right; x++) {
        for (int z = front; z <= back; z++) {
          for (int y = shell.minY(); y <= shell.maxY(); y++) {
            if (x != shell.minX()
                && x != shell.maxX()
                && z != shell.minZ()
                && z != shell.maxZ()
                && y != shell.minY()
                && y != shell.maxY()) continue;
            position.set(x, y, z);
            BlockState existing = level.getBlockState(position);
            if (chunkBounds.isInside(position)
                && (isReplaceableByStructures(existing)
                    || existing.is(BlockTags.REPLACEABLE)
                    || existing.is(BlockTags.CAVE_VINES)
                    || existing.getBlock() instanceof BushBlock
                    || existing.is(Blocks.MOSS_CARPET))
                && !NativeTerrainVolume.solid(existing)
                && level.getBlockEntity(position) == null
                && columns.solid(x, y, z)) {
              // Restore holes only in original positive-density island material.
              // Preserve existing solids and never fill original natural air or fluid.
              level.setBlock(position, columns.block(x, y, z), 2);
            }
          }
        }
      }
    }

    private boolean foundationReplaceable(BlockState state, boolean wet) {
      return isReplaceableByStructures(state)
          || wet && (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT));
    }

    @Override
    protected void fillColumnDown(
        WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox bounds) {
      BlockPos.MutableBlockPos position = getWorldPos(x, y, z);
      if (!bounds.isInside(position)) return;
      boolean wet =
          level.getFluidState(position).is(FluidTags.WATER)
              && BasinWaterline.at(level, position, Integer.MIN_VALUE) > position.getY();
      int limit = wet ? 20 : 4;
      // Preflight every actual native footing, including dry gaps missed by the
      // coarse placement screen. Columns reach a real bed or are omitted.
      BlockPos.MutableBlockPos bed = position.mutable();
      int inspected = 0;
      while (inspected < limit
          && bed.getY() > level.getMinBuildHeight() + 1
          && foundationReplaceable(level.getBlockState(bed), wet)) {
        bed.move(Direction.DOWN);
        inspected++;
      }
      if (!NativeTerrainVolume.solid(level.getBlockState(bed))) return;
      for (int depth = 0;
          depth < limit
              && position.getY() > level.getMinBuildHeight() + 1
              && foundationReplaceable(level.getBlockState(position), wet);
          depth++) {
        level.setBlock(position, state, 2);
        position.move(Direction.DOWN);
      }
    }
  }
}
