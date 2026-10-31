package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Requires natural support beneath the actual native assembly of selected structures. */
public final class SupportedStructure extends Structure {
  public static final MapCodec<SupportedStructure> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      Structure.DIRECT_CODEC.fieldOf("delegate").forGetter(value -> value.delegate),
                      StringRepresentable.fromEnum(TerrainCheck::values)
                          .optionalFieldOf("terrain_check", TerrainCheck.FLOOR)
                          .forGetter(value -> value.terrainCheck))
                  .apply(instance, SupportedStructure::new));

  public enum TerrainCheck implements StringRepresentable {
    FLOOR,
    SURFACE,
    GROUNDED_SURFACE,
    MARINE_SURFACE,
    CAVERN,
    ANCHORED_COMPLEX,
    WET_RUIN,
    ENCLOSED,
    CORE_ENCLOSED,
    BURIED_COMPLEX,
    PORTAL_ENCLOSED;

    @Override
    public String getSerializedName() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<SupportedStructure>> TYPE =
      TYPES.register("supported", () -> () -> CODEC);
  private static final int SAMPLE_STEP = 4;
  // The pinned surface definitions have start offsets <= 0, except coconut village's +1.
  private static final int SURFACE_START_ALLOWANCE = 1;

  private final Structure delegate;
  private final TerrainCheck terrainCheck;

  public SupportedStructure(Structure delegate, TerrainCheck terrainCheck) {
    super(
        new StructureSettings(
            delegate.biomes(),
            delegate.spawnOverrides(),
            delegate.step(),
            delegate.terrainAdaptation()));
    this.delegate = delegate;
    this.terrainCheck = terrainCheck;
  }

  /** Call once from the existing mod constructor with its injected mod event bus. */
  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Override
  public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context) {
    // Native climate eligibility is checked before relocation, using the scoped prefit context.
    return embedsUnderground()
        ? findGenerationPoint(context)
        : super.findValidGenerationPoint(context);
  }

  private boolean embedsUnderground() {
    return terrainCheck == TerrainCheck.CAVERN
        || terrainCheck == TerrainCheck.ENCLOSED
        || terrainCheck == TerrainCheck.CORE_ENCLOSED
        || terrainCheck == TerrainCheck.BURIED_COMPLEX
        || terrainCheck == TerrainCheck.PORTAL_ENCLOSED;
  }

  @Override
  protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    Optional<GenerationStub> nativeStub =
        delegate.findValidGenerationPoint(
            embedsUnderground() ? StructureBiomeCompatibility.prefitContext(context) : context);
    if (nativeStub.isEmpty()) {
      return Optional.empty();
    }
    GenerationStub stub = nativeStub.orElseThrow();
    // The native consumer draws random child pieces. Evaluate it exactly once.
    StructurePiecesBuilder builder = stub.getPiecesBuilder();
    PiecesContainer pieces = builder.build();
    if (pieces.isEmpty()) {
      return Optional.empty();
    }
    BoundingBox assemblyBounds = builder.getBoundingBox();
    if (!embedsUnderground()
        && (context.heightAccessor().isOutsideBuildHeight(assemblyBounds.minY())
            || context.heightAccessor().isOutsideBuildHeight(assemblyBounds.maxY()))) {
      return Optional.empty();
    }
    Map<Long, NoiseColumn> columns = new HashMap<>();
    if (embedsUnderground()) return fitAssembly(context, stub, builder, pieces, columns);
    if (terrainCheck == TerrainCheck.GROUNDED_SURFACE)
      return fitSurface(context, stub, builder, pieces, columns);
    if (terrainCheck == TerrainCheck.ANCHORED_COMPLEX) {
      if (!supportedSurfaceRoot(context, pieces.pieces().getFirst(), columns)
          || !assemblySupport(context, pieces, 0, 0, 0, new StructureTerrain(context), false))
        return Optional.empty();
      return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
    }
    if (terrainCheck == TerrainCheck.WET_RUIN) {
      if (!wetRuin(context, pieces, columns)) return Optional.empty();
      return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
    }
    int rootFloorY = pieces.pieces().getFirst().getBoundingBox().minY();
    if (terrainCheck == TerrainCheck.MARINE_SURFACE
        && supportedMarineSurface(context, pieces.pieces().getFirst(), columns))
      return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
    boolean surface =
        terrainCheck == TerrainCheck.SURFACE
            || terrainCheck == TerrainCheck.GROUNDED_SURFACE
            || terrainCheck == TerrainCheck.MARINE_SURFACE;
    if (surface && !supportedSurfaceRoot(context, pieces.pieces().getFirst(), columns)) {
      return Optional.empty();
    }
    if ((terrainCheck == TerrainCheck.GROUNDED_SURFACE
            || terrainCheck == TerrainCheck.MARINE_SURFACE)
        && !supportedSurfaceFootprint(pieces, 0, 0, 0, new StructureTerrain(context)))
      return Optional.empty();
    for (StructurePiece piece : pieces.pieces()) {
      BoundingBox bounds = piece.getBoundingBox();
      if (surface) {
        // Child roofs, bridges and decorations need not sit on the root's ground plane.
        // Their complete bounds are still checked above.
        continue;
      }
      // Nova's support features may attach inside a room, above its floor. Their
      // exact X/Z must have a natural bed below the root, where the columns stop.
      int floorY =
          piece instanceof PoolElementStructurePiece poolPiece
                  && poolPiece.getElement() instanceof FeaturePoolElement
              ? rootFloorY
              : piece.getBoundingBox().minY();
      if (!supported(context, bounds, floorY, columns)) {
        return Optional.empty();
      }
    }
    // Returning the original lazy consumer would reroll its assembly when generate() asks again.
    return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
  }

  private static boolean supportedMarineSurface(
      GenerationContext context, StructurePiece root, Map<Long, NoiseColumn> columns) {
    IslandTerrain terrain = IslandTerrain.from(context.randomState());
    if (terrain == null) return false;
    BoundingBox bounds = root.getBoundingBox();
    BlockPos center = bounds.getCenter();
    IslandLayout.Basin basin = terrain.basin(center.getX(), center.getZ());
    if (basin == null) return false;
    int floor =
        bounds.minY()
            + (root instanceof PoolElementStructurePiece pool ? pool.getGroundLevelDelta() : 0);
    // The pinned galleon starts three blocks below WORLD_SURFACE_WG. Preserve its
    // draft and native assembly, but require water under its complete rotated hull.
    if (floor < basin.waterline() - 8 || floor > basin.waterline() + 1) return false;
    for (int x = bounds.minX(); ; x = Math.min(x + SAMPLE_STEP, bounds.maxX())) {
      for (int z = bounds.minZ(); ; z = Math.min(z + SAMPLE_STEP, bounds.maxZ())) {
        if (!basin.water(x, basin.waterline(), z)) return false;
        NoiseColumn column = column(context, x, z, columns);
        if (!column.getBlock(basin.waterline()).getFluidState().is(FluidTags.WATER)
            || !column.getBlock(floor - 4).getFluidState().is(FluidTags.WATER)) return false;
        if (z == bounds.maxZ()) break;
      }
      if (x == bounds.maxX()) break;
    }
    return true;
  }

  private static boolean supportedSurfaceRoot(
      GenerationContext context, StructurePiece root, Map<Long, NoiseColumn> columns) {
    int groundY =
        root.getBoundingBox().minY()
            + (root instanceof PoolElementStructurePiece poolPiece
                ? poolPiece.getGroundLevelDelta()
                : 0);
    BlockPos center = root.getBoundingBox().getCenter();
    // Nature's Spirit's coconut village deliberately starts one block above
    // the heightmap. Permit only that bounded gap; a lower island must not
    // count as support for a root suspended far above it.
    int supportY =
        Math.max(
            groundY - SURFACE_START_ALLOWANCE,
            Math.min(
                groundY,
                context
                    .chunkGenerator()
                    .getBaseHeight(
                        center.getX(),
                        center.getZ(),
                        Heightmap.Types.OCEAN_FLOOR_WG,
                        context.heightAccessor(),
                        context.randomState())));
    return supported(
        context,
        new BoundingBox(
            center.getX(), supportY, center.getZ(), center.getX(), supportY, center.getZ()),
        supportY,
        columns);
  }

  private static boolean supportedSurfaceFootprint(
      PiecesContainer pieces, int dx, int dy, int dz, StructureTerrain terrain) {
    StructurePiece root = pieces.pieces().getFirst();
    int rootFloor =
        root.getBoundingBox().minY()
            + (root instanceof PoolElementStructurePiece pool ? pool.getGroundLevelDelta() : 0);
    int totalTested = 0;
    int totalSupported = 0;
    for (StructurePiece piece : pieces.pieces()) {
      if (!(piece instanceof PoolElementStructurePiece pool)
          || pool.getElement() instanceof FeaturePoolElement) continue;
      BoundingBox bounds = piece.getBoundingBox();
      int floor = bounds.minY() + pool.getGroundLevelDelta();
      if (Math.abs(floor - rootFloor) > 3 || bounds.getXSpan() < 8 || bounds.getZSpan() < 8)
        continue;
      int tested = 0;
      int supported = 0;
      for (int x = bounds.minX(); ; x = Math.min(x + SAMPLE_STEP, bounds.maxX())) {
        for (int z = bounds.minZ(); ; z = Math.min(z + SAMPLE_STEP, bounds.maxZ())) {
          tested++;
          for (int depth = 1; depth <= 12; depth++) {
            if (terrain.solid(x + dx, floor + dy - depth, z + dz)) {
              supported++;
              break;
            }
          }
          if (z == bounds.maxZ()) break;
        }
        if (x == bounds.maxX()) break;
      }
      if (supported * 3 < tested) return false;
      totalTested += tested;
      totalSupported += supported;
    }
    return totalTested == 0 || totalSupported * 5 >= totalTested * 3;
  }

  private static Optional<GenerationStub> fitSurface(
      GenerationContext context,
      GenerationStub stub,
      StructurePiecesBuilder builder,
      PiecesContainer pieces,
      Map<Long, NoiseColumn> columns) {
    StructurePiece root = pieces.pieces().getFirst();
    int rootFloor =
        root.getBoundingBox().minY()
            + (root instanceof PoolElementStructurePiece pool ? pool.getGroundLevelDelta() : 0);
    BlockPos center = root.getBoundingBox().getCenter();
    BoundingBox bounds = builder.getBoundingBox();
    StructureTerrain terrain = new StructureTerrain(context);
    int[][] offsets = {
      {0, 0},
      {8, 0},
      {0, 8},
      {-8, 0},
      {0, -8},
      {16, 0},
      {0, 16},
      {-16, 0},
      {0, -16},
      {16, 16},
      {-16, 16},
      {-16, -16},
      {16, -16},
      {32, 0},
      {0, 32},
      {-32, 0},
      {0, -32}
    };
    for (int[] offset : offsets) {
      int dx = offset[0];
      int dz = offset[1];
      if (bounds.minX() + dx - 12 < context.chunkPos().getMinBlockX() - 128
          || bounds.maxX() + dx + 12 > context.chunkPos().getMaxBlockX() + 128
          || bounds.minZ() + dz - 12 < context.chunkPos().getMinBlockZ() - 128
          || bounds.maxZ() + dz + 12 > context.chunkPos().getMaxBlockZ() + 128) continue;
      int targetFloor =
          context
              .chunkGenerator()
              .getBaseHeight(
                  center.getX() + dx,
                  center.getZ() + dz,
                  Heightmap.Types.WORLD_SURFACE_WG,
                  context.heightAccessor(),
                  context.randomState());
      int dy = targetFloor - rootFloor;
      if (Math.abs(dy) > 12
          || context.heightAccessor().isOutsideBuildHeight(bounds.minY() + dy)
          || context.heightAccessor().isOutsideBuildHeight(bounds.maxY() + dy)) continue;
      NoiseColumn natural = column(context, center.getX() + dx, center.getZ() + dz, columns);
      if (!solidFloor(natural.getBlock(targetFloor - 1))) continue;
      if (!supportedSurfaceFootprint(pieces, dx, dy, dz, terrain)) continue;
      BlockPos target = stub.position().offset(dx, dy, dz);
      if (!context
          .validBiome()
          .test(
              context
                  .biomeSource()
                  .getNoiseBiome(
                      target.getX() >> 2,
                      target.getY() >> 2,
                      target.getZ() >> 2,
                      context.randomState().sampler()))) continue;
      for (StructurePiece piece : pieces.pieces()) movePiece(piece, dx, dy, dz);
      return Optional.of(
          new GenerationStub(stub.position().offset(dx, dy, dz), Either.right(builder)));
    }
    return Optional.empty();
  }

  @Override
  public BoundingBox adjustBoundingBox(BoundingBox bounds) {
    return needsCavern() ? bounds.inflatedBy(12) : super.adjustBoundingBox(bounds);
  }

  public boolean needsCavern() {
    return terrainCheck == TerrainCheck.CAVERN;
  }

  private Optional<GenerationStub> fitAssembly(
      GenerationContext context,
      GenerationStub stub,
      StructurePiecesBuilder builder,
      PiecesContainer pieces,
      Map<Long, NoiseColumn> columns) {
    Optional<StructurePiece> core =
        terrainCheck == TerrainCheck.PORTAL_ENCLOSED
            ? pieces.pieces().stream()
                .filter(piece -> piece instanceof StrongholdPieces.PortalRoom)
                .findFirst()
            : Optional.of(pieces.pieces().getFirst());
    if (core.isEmpty()) return Optional.empty();
    StructureTerrain terrain = new StructureTerrain(context);
    BoundingBox coreBounds = core.orElseThrow().getBoundingBox();
    BoundingBox bounds = builder.getBoundingBox();
    List<BlockPos> roof =
        terrainCheck == TerrainCheck.BURIED_COMPLEX ? exteriorRoof(pieces) : List.of();
    int[][] offsets = {
      {0, 0},
      {16, 0},
      {0, 16},
      {-16, 0},
      {0, -16},
      {16, 16},
      {-16, 16},
      {-16, -16},
      {16, -16},
      {32, 0},
      {0, 32},
      {-32, 0},
      {0, -32}
    };
    for (int[] horizontal : offsets) {
      int dx = horizontal[0];
      int dz = horizontal[1];
      // Native structure references look only eight chunks around their owning start.
      if (bounds.minX() + dx - (needsCavern() ? 12 : 0) < context.chunkPos().getMinBlockX() - 128
          || bounds.maxX() + dx + (needsCavern() ? 12 : 0) > context.chunkPos().getMaxBlockX() + 128
          || bounds.minZ() + dz - (needsCavern() ? 12 : 0) < context.chunkPos().getMinBlockZ() - 128
          || bounds.maxZ() + dz + (needsCavern() ? 12 : 0)
              > context.chunkPos().getMaxBlockZ() + 128) continue;
      BlockPos center = coreBounds.getCenter().offset(dx, 0, dz);
      NoiseColumn coreColumn = column(context, center.getX(), center.getZ(), columns);
      int lowest = context.heightAccessor().getMinBuildHeight() + 5 - bounds.minY();
      int highest =
          context.heightAccessor().getMaxBuildHeight() - (needsCavern() ? 20 : 2) - bounds.maxY();
      for (int offset = highest; offset >= lowest; offset -= SAMPLE_STEP) {
        int floor = coreBounds.minY() + offset;
        if (!solidFloor(coreColumn.getBlock(floor - 1))
            || !solidFloor(coreColumn.getBlock(floor - 4))) continue;
        if (!roof.isEmpty() && !buriedRoof(roof, dx, offset, dz, terrain)) continue;
        if (!assemblySupport(context, pieces, dx, offset, dz, terrain, true)) continue;
        if (needsCavern()
            && !cavernRoof(
                pieces, dx, offset, dz, terrain, IslandTerrain.from(context.randomState())))
          continue;
        for (StructurePiece piece : pieces.pieces()) {
          movePiece(piece, dx, offset, dz);
          if (terrainCheck == TerrainCheck.PORTAL_ENCLOSED
              && piece instanceof StrongholdPieces.StrongholdPiece)
            ((CompleteStrongholdPiece) piece).aeroscapesReimagine$completeShell();
        }
        return Optional.of(
            new GenerationStub(stub.position().offset(dx, offset, dz), Either.right(builder)));
      }
    }
    return Optional.empty();
  }

  private static boolean cavernRoof(
      PiecesContainer pieces,
      int dx,
      int dy,
      int dz,
      StructureTerrain terrain,
      IslandTerrain islands) {
    var shifted =
        pieces.pieces().stream().map(piece -> piece.getBoundingBox().moved(dx, dy, dz)).toList();
    CityCavern cavern = new CityCavern(shifted);
    int tested = 0;
    int covered = 0;
    for (int index = 0; index < shifted.size(); index++) {
      StructurePiece piece = pieces.pieces().get(index);
      if (piece instanceof PoolElementStructurePiece pool
          && pool.getElement() instanceof FeaturePoolElement) continue;
      BlockPos center = shifted.get(index).getCenter();
      int roof = (int) Math.ceil(cavern.ceiling(center.getX(), center.getZ()));
      boolean solid =
          terrain.solid(center.getX(), roof + 2, center.getZ())
              && terrain.solid(center.getX(), roof + 6, center.getZ());
      if (tested == 0 && !solid) return false;
      tested++;
      if (solid) covered++;
    }
    if (tested == 0 || covered * 5 < tested * 3) return false;
    if (islands != null) {
      BoundingBox bounds = BoundingBox.encapsulatingBoxes(shifted).orElseThrow().inflatedBy(12);
      // Basin eligibility is constant within a layout cell. Avoid sampling any
      // cavity geometry in dry cells, but check every column near actual basins:
      // a sparse roof grid cannot establish a watertight rim or template boundary.
      for (int cellX = Math.floorDiv(bounds.minX(), 1024);
          cellX <= Math.floorDiv(bounds.maxX(), 1024);
          cellX++)
        for (int cellZ = Math.floorDiv(bounds.minZ(), 1024);
            cellZ <= Math.floorDiv(bounds.maxZ(), 1024);
            cellZ++) {
          var basin = islands.basin(cellX * 1024 + 512, cellZ * 1024 + 512);
          if (basin == null) continue;
          int extentX = (int) Math.ceil(basin.radiusX() * 1.56 + 45);
          int extentZ = (int) Math.ceil(basin.radiusZ() * 1.56 + 45);
          for (int x = Math.max(bounds.minX(), basin.centerX() - extentX);
              x <= Math.min(bounds.maxX(), basin.centerX() + extentX);
              x++)
            for (int z = Math.max(bounds.minZ(), basin.centerZ() - extentZ);
                z <= Math.min(bounds.maxZ(), basin.centerZ() + extentZ);
                z++) if (!cavern.separatesBasin(basin, x, z)) return false;
        }
    }
    return true;
  }

  private static List<BlockPos> exteriorRoof(PiecesContainer pieces) {
    Map<Long, BlockPos> roof = new HashMap<>();
    for (StructurePiece piece : pieces.pieces()) {
      if (piece instanceof PoolElementStructurePiece pool
          && pool.getElement() instanceof FeaturePoolElement) continue;
      BoundingBox bounds = piece.getBoundingBox();
      // One shared four-block grid weights large rooms by area, rather than
      // counting tiny decorations as nine samples each. Only the highest roof
      // at each X/Z is external; floors and stacked-room interiors are separate.
      for (int x = Math.floorDiv(bounds.minX() + SAMPLE_STEP - 1, SAMPLE_STEP) * SAMPLE_STEP;
          x <= bounds.maxX();
          x += SAMPLE_STEP)
        for (int z = Math.floorDiv(bounds.minZ() + SAMPLE_STEP - 1, SAMPLE_STEP) * SAMPLE_STEP;
            z <= bounds.maxZ();
            z += SAMPLE_STEP) {
          long key = ChunkPos.asLong(x, z);
          BlockPos prior = roof.get(key);
          if (prior == null || prior.getY() < bounds.maxY())
            roof.put(key, new BlockPos(x, bounds.maxY(), z));
        }
    }
    return List.copyOf(roof.values());
  }

  private static boolean buriedRoof(
      List<BlockPos> roof, int dx, int dy, int dz, StructureTerrain terrain) {
    int covered = 0;
    int remaining = roof.size();
    for (BlockPos point : roof) {
      if (terrain.solid(point.getX() + dx, point.getY() + dy + 2, point.getZ() + dz)
          && terrain.solid(point.getX() + dx, point.getY() + dy + 6, point.getZ() + dz)) covered++;
      remaining--;
      if ((covered + remaining) * 4 < roof.size() * 3) return false;
    }
    return covered * 4 >= roof.size() * 3;
  }

  private static void movePiece(StructurePiece piece, int dx, int dy, int dz) {
    piece.move(dx, dy, dz);
    // Native move() leaves jigsaw terrain junctions at their former coordinates.
    if (piece instanceof PoolElementStructurePiece pool)
      pool.getJunctions()
          .replaceAll(
              junction ->
                  new net.minecraft.world.level.levelgen.structure.pools.JigsawJunction(
                      junction.getSourceX() + dx,
                      junction.getSourceGroundY() + dy,
                      junction.getSourceZ() + dz,
                      junction.getDeltaY(),
                      junction.getDestProjection()));
  }

  private static boolean assemblySupport(
      GenerationContext context,
      PiecesContainer pieces,
      int dx,
      int dy,
      int dz,
      StructureTerrain terrain,
      boolean underground) {
    int tested = 0;
    int supported = 0;
    int covered = 0;
    for (StructurePiece piece : pieces.pieces()) {
      if (piece instanceof PoolElementStructurePiece pool
          && pool.getElement() instanceof FeaturePoolElement) continue;
      BoundingBox bounds = piece.getBoundingBox();
      int pieceTested = 0;
      int pieceSupported = 0;
      for (int ix = 0; ix < 3; ix++)
        for (int iz = 0; iz < 3; iz++) {
          int x = bounds.minX() + (bounds.getXSpan() - 1) * ix / 2 + dx;
          int z = bounds.minZ() + (bounds.getZSpan() - 1) * iz / 2 + dz;

          int floor = bounds.minY() + dy;
          // A room stacked above another room need not have natural stone in that room.
          boolean internal = false;
          for (StructurePiece other : pieces.pieces()) {
            if (other == piece) continue;
            BoundingBox below = other.getBoundingBox();
            if (below.isInside(new BlockPos(x - dx, bounds.minY() - 1, z - dz))) {
              internal = true;
              break;
            }
          }
          if (internal) continue;
          tested++;
          pieceTested++;
          boolean floorSupport = false;
          for (int depth = 1; depth <= 5; depth++)
            if (terrain.solid(x, floor - depth, z)) {
              floorSupport = true;
              break;
            }
          if (floorSupport) {
            supported++;
            pieceSupported++;
          }
          if (terrain.solid(x, bounds.maxY() + dy + 2, z)) covered++;
        }
      if (bounds.getXSpan() >= 8
          && bounds.getZSpan() >= 8
          && pieceTested > 0
          && pieceSupported * 3 < pieceTested) return false;
    }
    return tested > 0 && supported * 5 >= tested * 3 && (!underground || covered * 5 >= tested * 2);
  }

  private static boolean wetRuin(
      GenerationContext context, PiecesContainer pieces, Map<Long, NoiseColumn> columns) {
    IslandTerrain terrain = IslandTerrain.from(context.randomState());
    if (terrain == null) return false;
    int wet = 0;
    int tested = 0;
    for (StructurePiece piece : pieces.pieces()) {
      if (piece instanceof PoolElementStructurePiece pool
          && pool.getElement() instanceof FeaturePoolElement) continue;
      BoundingBox bounds = piece.getBoundingBox();
      BlockPos center = bounds.getCenter();
      IslandLayout.Basin basin = terrain.basin(center.getX(), center.getZ());
      if (basin == null || bounds.maxY() > basin.waterline() + 8) return false;
      tested++;
      NoiseColumn natural = column(context, center.getX(), center.getZ(), columns);
      // Native conduit children attach below their ground plane, inside the seabed.
      // Verify the surrounding ocean water, not air/water inside an unplaced room.
      if (natural.getBlock(basin.waterline() - 1).getFluidState().is(FluidTags.WATER)) wet++;
    }
    return tested > 0 && wet * 4 >= tested * 3;
  }

  private static boolean supported(
      GenerationContext context, BoundingBox bounds, int floorY, Map<Long, NoiseColumn> columns) {
    int upperY = floorY - 1;
    int lowerY = floorY - 4;
    if (context.heightAccessor().isOutsideBuildHeight(lowerY)
        || context.heightAccessor().isOutsideBuildHeight(bounds.maxY())) {
      return false;
    }
    return solidPlane(context, bounds, upperY, columns)
        && solidPlane(context, bounds, lowerY, columns);
  }

  private static boolean solidPlane(
      GenerationContext context, BoundingBox bounds, int y, Map<Long, NoiseColumn> columns) {
    // Every actual rotated piece contributes its own rectangle. Always include both edges,
    // even when its width or depth is not divisible by the sampling step.
    for (int x = bounds.minX(); ; x = Math.min(x + SAMPLE_STEP, bounds.maxX())) {
      for (int z = bounds.minZ(); ; z = Math.min(z + SAMPLE_STEP, bounds.maxZ())) {
        // Pack block X/Z as two integers; this does not convert them to chunk coordinates.
        NoiseColumn column = column(context, x, z, columns);
        if (!solidFloor(column.getBlock(y))) {
          return false;
        }
        if (z == bounds.maxZ()) {
          break;
        }
      }
      if (x == bounds.maxX()) {
        break;
      }
    }
    return true;
  }

  private static NoiseColumn column(
      GenerationContext context, int x, int z, Map<Long, NoiseColumn> columns) {
    long key = ChunkPos.asLong(x, z);
    NoiseColumn column = columns.get(key);
    if (column == null) {
      column =
          context
              .chunkGenerator()
              .getBaseColumn(x, z, context.heightAccessor(), context.randomState());
      columns.put(key, column);
    }
    return column;
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
