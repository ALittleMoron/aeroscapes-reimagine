package dev.aeroscapes.worldgen;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Inspect original loaded templates; retain native assembly, processors and projection unchanged.
 */
final class TemplateStructureSupport {
  private static final int SNOW_SURROUNDINGS_DISTANCE = 4;
  private static final int SNOWY_PLAINS_MIN_SURFACE = 128;
  private static final TagKey<Biome> SNOW_SHELTER_BIOMES =
      TagKey.create(
          Registries.BIOME,
          ResourceLocation.fromNamespaceAndPath(
              "aeroscapes_reimagine", "has_structure/snow_shelter"));
  private static final TagKey<Biome> PERMAFROST_BIOMES =
      TagKey.create(
          Registries.BIOME,
          ResourceLocation.fromNamespaceAndPath("immersive_weathering", "has_permafrost"));
  private static final ResourceKey<Biome> SNOWY_CHERRY_GROVE =
      ResourceKey.create(
          Registries.BIOME,
          ResourceLocation.fromNamespaceAndPath("terralith", "snowy_cherry_grove"));

  private record Bearing(int x, int y, int z, boolean projected) {}

  record Interval(int x, int z, int bottom, int top) {}

  private final Structure.GenerationContext context;
  private final NoiseBasedChunkGenerator generator;
  private final Map<Long, NativeTerrainVolume> volumes = new HashMap<>();
  private final Map<Long, List<Interval>> intervals = new HashMap<>();
  private final Map<Long, Integer> surfaces = new HashMap<>();

  TemplateStructureSupport(Structure.GenerationContext context) {
    this.context = context;
    if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise))
      throw new IllegalStateException("Template terrain support requires a noise generator");
    generator = noise;
  }

  static boolean accepts(
      Structure.GenerationContext context,
      Structure delegate,
      PiecesContainer pieces,
      boolean seabed) {
    return new TemplateStructureSupport(context).test(delegate, pieces, seabed);
  }

  boolean acceptsDiscovery(PiecesContainer pieces, SupportedStructure.TerrainCheck check) {
    if (!(pieces.pieces().getFirst() instanceof PoolElementStructurePiece root)) return false;
    int grade = root.getBoundingBox().minY() + root.getGroundLevelDelta();
    BoundingBox bounds = pieces.calculateBoundingBox();
    if (check == SupportedStructure.TerrainCheck.DISCOVERY_SNOW_SHELTER
        && !hasSnowSetting(bounds, grade)) return false;
    Map<Long, Bearing> bearings = new HashMap<>();
    Map<BlockPos, BlockState> layout = new HashMap<>();
    for (var piece : pieces.pieces()) {
      if (!(piece instanceof PoolElementStructurePiece pool)
          || !(pool.getElement() instanceof SinglePoolElement single)) return false;
      boolean projected =
          single.getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;
      var settings = new StructurePlaceSettings().setRotation(pool.getRotation());
      var template =
          single.template.map(context.structureTemplateManager()::getOrCreate, value -> value);
      var palette = settings.getRandomPalette(template.palettes, pool.getPosition());
      for (var info : palette.blocks()) {
        var state = material(info);
        if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.STRUCTURE_BLOCK))
          continue;
        var pos =
            StructureTemplate.calculateRelativePosition(settings, info.pos())
                .offset(pool.getPosition());
        int ground = projected ? surface(pos.getX(), pos.getZ()) : grade;
        int y = projected ? ground - 1 + info.pos().getY() : pos.getY();
        if (context.heightAccessor().isOutsideBuildHeight(y)) return false;
        if (projected && Math.abs(ground - grade) > 2) return false;
        if (Math.abs(y - grade) <= 5) {
          if (state.getBlock() instanceof DoorBlock) {
            state =
                state.setValue(
                    DoorBlock.FACING, pool.getRotation().rotate(state.getValue(DoorBlock.FACING)));
          }
          layout.put(pos.atY(y), state);
        }
        if (y > ground) {
          // A hillside may support the footings but still fill the usable space above them.
          if (y > ground + 1 && solidAt(pos.getX(), y, pos.getZ())) return false;
          continue;
        }
        var bearing = new Bearing(pos.getX(), y, pos.getZ(), projected);
        bearings.merge(
            ChunkPos.asLong(pos.getX(), pos.getZ()),
            bearing,
            (first, second) -> first.y() <= second.y() ? first : second);
      }
    }
    if (bearings.isEmpty()) return false;
    Set<Interval> targets = new HashSet<>();
    for (var bearing : bearings.values()) {
      int floor = bearing.projected() ? surface(bearing.x(), bearing.z()) - 1 : bearing.y() - 1;
      if (!solidAt(bearing.x(), floor, bearing.z())
          || !solidAt(bearing.x(), floor - 1, bearing.z())
          || !solidAt(bearing.x(), floor - 2, bearing.z())) return false;
      var interval = support(intervals(bearing.x(), bearing.z()), floor);
      if (interval == null) return false;
      targets.add(interval);
    }
    return connected(targets, bearings.values())
        && hasDiscoveryApproach(
            bounds, grade, layout, check != SupportedStructure.TerrainCheck.DISCOVERY_SURFACE);
  }

  private boolean hasSnowSetting(BoundingBox bounds, int grade) {
    var biomes =
        new BiomeManager(
            (x, y, z) ->
                context.biomeSource().getNoiseBiome(x, y, z, context.randomState().sampler()),
            BiomeManager.obfuscateSeed(context.seed()));
    int margin = SNOW_SURROUNDINGS_DISTANCE;
    int centerX = bounds.getCenter().getX();
    int centerZ = bounds.getCenter().getZ();
    for (int x : new int[] {bounds.minX() + 1, centerX, bounds.maxX() - 1})
      for (int z : new int[] {bounds.minZ() + 1, centerZ, bounds.maxZ() - 1})
        if (!snowSurface(biomes, x, z, grade)) return false;

    int snowy = 0;
    for (int x : new int[] {bounds.minX() + 1, centerX, bounds.maxX() - 1}) {
      if (snowSurface(biomes, x, bounds.minZ() - margin, grade)) snowy++;
      if (snowSurface(biomes, x, bounds.maxZ() + margin, grade)) snowy++;
    }
    for (int z : new int[] {bounds.minZ() + 1, centerZ, bounds.maxZ() - 1}) {
      if (snowSurface(biomes, bounds.minX() - margin, z, grade)) snowy++;
      if (snowSurface(biomes, bounds.maxX() + margin, z, grade)) snowy++;
    }
    return snowy >= 8;
  }

  @SuppressWarnings("removal") // The pinned NoiseBasedCountPlacement uses this native noise.
  private static boolean permafrostCanReach(int x, int z) {
    // Immersive Weathering's size-32 ore can reach 7 blocks from an in-chunk origin.
    int reach = 7;
    for (int chunkX = (x - reach) >> 4; chunkX <= (x + reach) >> 4; chunkX++) {
      for (int chunkZ = (z - reach) >> 4; chunkZ <= (z + reach) >> 4; chunkZ++) {
        if (Biome.BIOME_INFO_NOISE.getValue((chunkX << 4) / 100.0, (chunkZ << 4) / 100.0, false)
            > -0.1) return true;
      }
    }
    return false;
  }

  private boolean snowSurface(BiomeManager biomes, int x, int z, int grade) {
    int exposedSurface = surface(x, z);
    if (Math.abs(exposedSurface - grade) > 4
        || volume(x, z).block(x, exposedSurface - 1, z)
            != generator.generatorSettings().value().defaultBlock()) return false;
    var point = new BlockPos(x, exposedSurface, z);
    var biome = biomes.getBiome(point.below());
    if (!biome.is(SNOW_SHELTER_BIOMES)
        || (biome.is(PERMAFROST_BIOMES) && permafrostCanReach(x, z))
        || biome.value().getPrecipitationAt(point) != Biome.Precipitation.SNOW) return false;
    // Plains rely on late snow layers, which were absent on the surveyed lower islands.
    if (biome.is(Biomes.SNOWY_PLAINS))
      return exposedSurface >= SNOWY_PLAINS_MIN_SURFACE
          && biomes.getBiome(point).is(Biomes.SNOWY_PLAINS);
    // The pinned surface rules leave stone on steep peaks and powder snow in snowy groves.
    boolean stoneOnSteep = biome.is(Biomes.JAGGED_PEAKS) || biome.is(SNOWY_CHERRY_GROVE);
    if ((stoneOnSteep || biome.is(Biomes.SNOWY_SLOPES))
        && (Math.abs(surface(x - 1, z) - surface(x + 1, z)) >= 4
            || Math.abs(surface(x, z - 1) - surface(x, z + 1)) >= 4)) return false;
    if (stoneOnSteep || biome.is(Biomes.ICE_SPIKES)) return true;
    double powderSnow =
        context.randomState().getOrCreateNoise(Noises.POWDER_SNOW).getValue(x, 0, z);
    return powderSnow < 0.35 || powderSnow > 0.6;
  }

  private boolean solidAt(int x, int y, int z) {
    return !context.heightAccessor().isOutsideBuildHeight(y)
        && NativeTerrainVolume.solid(volume(x, z).block(x, y, z));
  }

  private boolean hasDiscoveryApproach(
      BoundingBox bounds, int grade, Map<BlockPos, BlockState> layout, boolean sheltered) {
    boolean hasDoor = false;
    for (var entry : layout.entrySet()) {
      var door = entry.getValue();
      if (!(door.getBlock() instanceof DoorBlock) || door.is(Blocks.IRON_DOOR)) continue;
      if (door.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
      var position = entry.getKey();
      if (Math.abs(position.getY() - grade) > 2) continue;
      hasDoor = true;
      if (!standingRoom(position, layout)) continue;
      var facing = door.getValue(DoorBlock.FACING);
      if (doorApproach(position, facing, grade, bounds, layout)
          || doorApproach(position, facing.getOpposite(), grade, bounds, layout)) return true;
    }
    if (hasDoor) return false;
    if (sheltered) {
      for (var roof : layout.keySet()) {
        if (roof.getY() < grade + 2 || roof.getY() > grade + 4) continue;
        BlockPos interior = roof.below(2);
        if (standingRoom(interior, layout) && reachesIsland(interior, grade, bounds, layout))
          return true;
      }
      return false;
    }
    int distance = SupportedStructure.DISCOVERY_APPROACH_LENGTH;
    for (int x = bounds.minX(); x <= bounds.maxX(); x++)
      if (walkableApproach(
              new BlockPos(x, grade, bounds.minZ()), Direction.NORTH, distance, grade, layout)
          || walkableApproach(
              new BlockPos(x, grade, bounds.maxZ()), Direction.SOUTH, distance, grade, layout))
        return true;
    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
      if (walkableApproach(
              new BlockPos(bounds.minX(), grade, z), Direction.WEST, distance, grade, layout)
          || walkableApproach(
              new BlockPos(bounds.maxX(), grade, z), Direction.EAST, distance, grade, layout))
        return true;
    return false;
  }

  private boolean doorApproach(
      BlockPos door,
      Direction outward,
      int grade,
      BoundingBox bounds,
      Map<BlockPos, BlockState> layout) {
    var inside = door.relative(outward.getOpposite());
    if (inside.getX() < bounds.minX()
        || inside.getX() > bounds.maxX()
        || inside.getZ() < bounds.minZ()
        || inside.getZ() > bounds.maxZ()
        || step(inside, grade, layout) == null) return false;
    var outside = step(door.relative(outward), grade, layout);
    return outside != null && reachesIsland(outside, grade, bounds, layout);
  }

  private boolean reachesIsland(
      BlockPos start, int grade, BoundingBox bounds, Map<BlockPos, BlockState> layout) {
    int padding = SupportedStructure.DISCOVERY_APPROACH_LENGTH;
    var visited = new HashSet<BlockPos>();
    var open = new ArrayDeque<BlockPos>();
    visited.add(start);
    open.add(start);
    while (!open.isEmpty()) {
      var point = open.removeFirst();
      if (point.getX() <= bounds.minX() - padding
          || point.getX() >= bounds.maxX() + padding
          || point.getZ() <= bounds.minZ() - padding
          || point.getZ() >= bounds.maxZ() + padding) return true;
      for (Direction direction : Direction.Plane.HORIZONTAL) {
        var next = step(point.relative(direction), grade, layout);
        if (next != null && visited.add(next)) open.addLast(next);
      }
    }
    return false;
  }

  private boolean walkableApproach(
      BlockPos start,
      Direction direction,
      int length,
      int grade,
      Map<BlockPos, BlockState> layout) {
    var previous = start;
    for (int distance = 0; distance < length; distance++) {
      previous = step(previous.relative(direction), grade, layout);
      if (previous == null) return false;
    }
    return true;
  }

  private BlockPos step(BlockPos position, int grade, Map<BlockPos, BlockState> layout) {
    for (int offset : new int[] {0, 1, -1}) {
      var next = position.offset(0, offset, 0);
      if (Math.abs(next.getY() - grade) <= 3 && standingRoom(next, layout)) return next;
    }
    return null;
  }

  private boolean standingRoom(BlockPos position, Map<BlockPos, BlockState> layout) {
    var floor = discoveryBlock(position.below(), layout);
    return !context.heightAccessor().isOutsideBuildHeight(position.getY() + 1)
        && floor.getFluidState().isEmpty()
        && (NativeTerrainVolume.solid(floor)
            || floor.is(Blocks.DIRT_PATH)
            || floor.getBlock() instanceof StairBlock
            || floor.getBlock() instanceof SlabBlock)
        && canStepThrough(discoveryBlock(position, layout))
        && canStepThrough(discoveryBlock(position.above(), layout));
  }

  private BlockState discoveryBlock(BlockPos position, Map<BlockPos, BlockState> layout) {
    var placed = layout.get(position);
    return placed != null
        ? placed
        : volume(position.getX(), position.getZ())
            .block(position.getX(), position.getY(), position.getZ());
  }

  private static boolean canStepThrough(BlockState state) {
    return state.getFluidState().isEmpty()
        && (state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
            || state.getBlock() instanceof CarpetBlock
            || state.getBlock() instanceof DoorBlock && !state.is(Blocks.IRON_DOOR));
  }

  private boolean test(Structure delegate, PiecesContainer pieces, boolean seabed) {
    var heightmap =
        delegate instanceof JigsawStructure jigsaw
            ? jigsaw.projectStartToHeightmap
            : delegate instanceof dev.aeroscapes.archeology.ArcheologyProjection archeology
                ? archeology.aeroscapes$heightmap()
                : java.util.Optional.<Heightmap.Types>empty();
    if (heightmap.isEmpty())
      throw new IllegalStateException("Template support requires a projected native jigsaw");
    var root = pieces.pieces().getFirst();
    var bounds = root.getBoundingBox();
    // JigsawPlacement uses integer division, not BoundingBox.getCenter's upper midpoint.
    BlockPos center =
        new BlockPos((bounds.minX() + bounds.maxX()) / 2, 0, (bounds.minZ() + bounds.maxZ()) / 2);
    int projection =
        context
            .chunkGenerator()
            .getBaseHeight(
                center.getX(),
                center.getZ(),
                heightmap.orElseThrow(),
                context.heightAccessor(),
                context.randomState());
    if (seabed)
      projection =
          SeabedProjection.floor(
              context.chunkGenerator(),
              context.randomState(),
              context.heightAccessor(),
              center.getX(),
              center.getZ(),
              projection);
    Map<Long, Bearing> bearings = new HashMap<>();
    for (var piece : pieces.pieces()) {
      if (!(piece instanceof PoolElementStructurePiece pool)
          || !(pool.getElement() instanceof SinglePoolElement single))
        throw new IllegalStateException(
            "Re-audit unsupported template element: " + piece.getClass().getName());
      StructureTemplate template =
          single.template.map(context.structureTemplateManager()::getOrCreate, value -> value);
      StructurePlaceSettings settings =
          new StructurePlaceSettings().setRotation(pool.getRotation());
      var palette = settings.getRandomPalette(template.palettes, pool.getPosition());
      boolean projected =
          pool.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;
      for (var info : palette.blocks()) {
        BlockState material = material(info);
        BlockPos position =
            StructureTemplate.calculateRelativePosition(settings, info.pos())
                .offset(pool.getPosition());
        if (projected
            && !material.is(Blocks.STRUCTURE_VOID)
            && !material.is(Blocks.STRUCTURE_BLOCK)) {
          int projectedY = surface(position.getX(), position.getZ()) - 1 + info.pos().getY();
          if (projectedY < context.heightAccessor().getMinBuildHeight()
              || projectedY >= context.heightAccessor().getMaxBuildHeight()) return false;
        }
        if (material.isAir()) {
          if (!seabed && position.getY() < projection - 4 && !insideBody(position)) return false;
          continue;
        }
        if (material.is(Blocks.STRUCTURE_VOID) || material.is(Blocks.STRUCTURE_BLOCK)) continue;
        if (!projected && position.getY() >= projection) continue;
        long key = ChunkPos.asLong(position.getX(), position.getZ());
        Bearing existing = bearings.get(key);
        if (existing == null || position.getY() < existing.y())
          bearings.put(
              key, new Bearing(position.getX(), position.getY(), position.getZ(), projected));
      }
    }
    if (bearings.isEmpty()) return false;
    Set<Interval> targets = new HashSet<>();
    for (Bearing bearing : bearings.values()) {
      int floor = bearing.projected() ? surface(bearing.x(), bearing.z()) - 1 : bearing.y() - 1;
      Interval support = support(intervals(bearing.x(), bearing.z()), floor);
      if (support == null) return false;
      if (seabed) {
        IslandTerrain terrain = IslandTerrain.from(context.randomState());
        var basin = terrain == null ? null : terrain.basin(bearing.x(), bearing.z());
        if (basin == null
            || !volume(bearing.x(), bearing.z())
                .block(bearing.x(), root.getBoundingBox().maxY(), bearing.z())
                .getFluidState()
                .is(FluidTags.WATER)) return false;
      }
      targets.add(support);
    }
    return connected(targets, bearings.values());
  }

  static Interval support(List<Interval> intervals, int floor) {
    Interval selected = null;
    for (Interval interval : intervals)
      if (interval.bottom() <= floor
          && interval.top() >= floor - 3
          && (selected == null || interval.top() > selected.top())) selected = interval;
    return selected;
  }

  private int surface(int x, int z) {
    return surfaces.computeIfAbsent(
        ChunkPos.asLong(x, z),
        ignored -> {
          var volume = volume(x, z);
          int height = context.heightAccessor().getMaxBuildHeight();
          while (height > context.heightAccessor().getMinBuildHeight()
              && !Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(volume.block(x, height - 1, z)))
            height--;
          return height;
        });
  }

  private boolean insideBody(BlockPos position) {
    IslandTerrain terrain = IslandTerrain.from(context.randomState());
    return terrain == null
        || ExposedIslandSurfaces.interpolated(
                position.getX(),
                position.getY(),
                position.getZ(),
                (x, y, z) ->
                    terrain.biomeDensity(
                        new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(
                            x, y, z)))
            > 0;
  }

  private BlockState material(StructureTemplate.StructureBlockInfo info) {
    if (!info.state().is(Blocks.JIGSAW)) return info.state();
    if (info.nbt() == null) throw new IllegalStateException("Jigsaw has no final-state data");
    try {
      return BlockStateParser.parseForBlock(
              context.registryAccess().lookupOrThrow(Registries.BLOCK),
              info.nbt().getString("final_state"),
              true)
          .blockState();
    } catch (CommandSyntaxException exception) {
      throw new IllegalStateException("Invalid native jigsaw final state", exception);
    }
  }

  private NativeTerrainVolume volume(int x, int z) {
    return volumes.computeIfAbsent(
        ChunkPos.asLong(x >> 4, z >> 4),
        ignored -> {
          int left = Math.floorDiv(x, 16) * 16;
          int front = Math.floorDiv(z, 16) * 16;
          return NativeTerrainVolume.nativeColumns(
              generator,
              context.randomState(),
              context.heightAccessor(),
              new BoundingBox(
                  left,
                  context.heightAccessor().getMinBuildHeight(),
                  front,
                  left + 15,
                  context.heightAccessor().getMaxBuildHeight() - 1,
                  front + 15));
        });
  }

  private List<Interval> intervals(int x, int z) {
    return intervals.computeIfAbsent(
        ChunkPos.asLong(x, z),
        ignored -> {
          var result = new java.util.ArrayList<Interval>();
          NativeTerrainVolume volume = volume(x, z);
          int bottom = Integer.MIN_VALUE;
          for (int y = context.heightAccessor().getMinBuildHeight();
              y <= context.heightAccessor().getMaxBuildHeight();
              y++) {
            boolean solid =
                y < context.heightAccessor().getMaxBuildHeight()
                    && NativeTerrainVolume.solid(volume.block(x, y, z));
            if (solid && bottom == Integer.MIN_VALUE) bottom = y;
            if (!solid && bottom != Integer.MIN_VALUE) {
              result.add(new Interval(x, z, bottom, y - 1));
              bottom = Integer.MIN_VALUE;
            }
          }
          return List.copyOf(result);
        });
  }

  private boolean connected(Set<Interval> targets, java.util.Collection<Bearing> bearings) {
    int minX = bearings.stream().mapToInt(Bearing::x).min().orElseThrow() - 1;
    int maxX = bearings.stream().mapToInt(Bearing::x).max().orElseThrow() + 1;
    int minZ = bearings.stream().mapToInt(Bearing::z).min().orElseThrow() - 1;
    int maxZ = bearings.stream().mapToInt(Bearing::z).max().orElseThrow() + 1;
    Set<Interval> visited = new HashSet<>();
    ArrayDeque<Interval> queue = new ArrayDeque<>();
    Interval first = targets.iterator().next();
    queue.add(first);
    visited.add(first);
    while (!queue.isEmpty()) {
      Interval current = queue.removeFirst();
      targets.remove(current);
      if (targets.isEmpty()) return true;
      for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
        int x = current.x() + direction[0];
        int z = current.z() + direction[1];
        if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
        for (Interval neighbor : intervals(x, z))
          if (neighbor.bottom() <= current.top()
              && neighbor.top() >= current.bottom()
              && visited.add(neighbor)) queue.addLast(neighbor);
      }
    }
    return false;
  }
}
