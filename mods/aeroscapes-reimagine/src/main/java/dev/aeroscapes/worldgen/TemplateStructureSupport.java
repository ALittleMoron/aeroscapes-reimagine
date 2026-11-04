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
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
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
  private record Bearing(int x, int y, int z, boolean projected) {}

  record Interval(int x, int z, int bottom, int top) {}

  private final Structure.GenerationContext context;
  private final NoiseBasedChunkGenerator generator;
  private final Map<Long, NativeTerrainVolume> volumes = new HashMap<>();
  private final Map<Long, List<Interval>> intervals = new HashMap<>();
  private final Map<Long, Integer> surfaces = new HashMap<>();

  private TemplateStructureSupport(Structure.GenerationContext context) {
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

  private boolean test(Structure delegate, PiecesContainer pieces, boolean seabed) {
    if (!(delegate instanceof JigsawStructure jigsaw) || jigsaw.projectStartToHeightmap.isEmpty())
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
                jigsaw.projectStartToHeightmap.orElseThrow(),
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
