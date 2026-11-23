package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

final class CliffDungeonRegression {
  private static final long CRYPT_SEED = -786160350173141704L;
  private static final ChunkPos CRYPT_START = new ChunkPos(-88, 37);
  private static final BoundingBox REPORTED_ROOM =
      new BoundingBox(-1434, 216, 607, -1414, 226, 643);
  private static final ChunkPos SHRINE_START = new ChunkPos(-294, -267);

  private CliffDungeonRegression() {}

  static void verify(ServerLevel level, String mode) {
    switch (mode) {
      case "crypt" -> crypt(level);
      case "shrine" -> shrine(level);
      default -> throw new IllegalArgumentException("Use crypt or shrine");
    }
    LogUtils.getLogger().info("Cliff dungeon regression passed: {}", mode);
  }

  private static void crypt(ServerLevel level) {
    if (level.getSeed() != CRYPT_SEED)
      throw new IllegalArgumentException("Wrong seed for cliff crypt");
    var holder =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .getHolder(ResourceLocation.parse("nova_structures:undead_crypt"))
            .orElseThrow();
    var requested = level.getChunk(CRYPT_START.x, CRYPT_START.z, ChunkStatus.STRUCTURE_STARTS);
    var start = requested.getStartForStructure(holder.value());
    boolean relocated = start != null && start.isValid();
    if (relocated
        && start.getPieces().stream()
            .anyMatch(piece -> piece.getBoundingBox().equals(REPORTED_ROOM))) {
      throw new IllegalStateException(
          "The reported cliff-edge statue room still generates at " + REPORTED_ROOM);
    }
    LogUtils.getLogger()
        .info("Reported crypt start {}: {}", CRYPT_START, relocated ? "relocated" : "rejected");
    if (start == null || !start.isValid()) {
      var placement =
          level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).stream()
              .filter(RandomSpreadStructurePlacement.class::isInstance)
              .map(RandomSpreadStructurePlacement.class::cast)
              .findFirst()
              .orElseThrow();
      int cellX = Math.floorDiv(CRYPT_START.x, placement.spacing());
      int cellZ = Math.floorDiv(CRYPT_START.z, placement.spacing());
      for (int radius = 1; radius <= 5 && (start == null || !start.isValid()); radius++) {
        for (int x = -radius; x <= radius && (start == null || !start.isValid()); x++) {
          for (int z = -radius; z <= radius && (start == null || !start.isValid()); z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
            var position =
                placement.getPotentialStructureChunk(
                    level.getSeed(),
                    (cellX + x) * placement.spacing(),
                    (cellZ + z) * placement.spacing());
            start =
                level
                    .getChunk(position.x, position.z, ChunkStatus.STRUCTURE_STARTS)
                    .getStartForStructure(holder.value());
          }
        }
      }
    }
    if (start == null || !start.isValid())
      throw new IllegalStateException("Crypt has no natural positive near the reported region");
    int rooms = 0;
    for (var piece : start.getPieces()) {
      var box = piece.getBoundingBox();
      if (box.getXSpan() < 8 || box.getZSpan() < 8 || box.getYSpan() < 5) continue;
      verifyCryptFloor(level, start, box);
      rooms++;
    }
    if (rooms < 8) throw new IllegalStateException("Crypt lost its larger rooms");
    var largest =
        start.getPieces().stream()
            .map(StructurePiece::getBoundingBox)
            .max(Comparator.comparingInt(box -> box.getXSpan() * box.getZSpan()))
            .orElseThrow();
    int centerX = largest.getCenter().getX();
    int centerZ = largest.getCenter().getZ();
    level.getChunk(centerX >> 4, centerZ >> 4);
    int savedBed = 0;
    for (int x = largest.minX(); x <= largest.maxX(); x++) {
      for (int z = largest.minZ(); z <= largest.maxZ(); z++) {
        if (solid(level.getBlockState(new BlockPos(x, largest.minY() - 1, z)))) savedBed++;
      }
    }
    if (savedBed * 20 < largest.getXSpan() * largest.getZSpan() * 19) {
      throw new IllegalStateException(
          "Largest crypt room has an exposed FULL base: " + largest + ", solid=" + savedBed);
    }
    LogUtils.getLogger()
        .info(
            "Crypt natural start {} rooms={} largest={} saved bed={}",
            start.getChunkPos(),
            rooms,
            largest,
            savedBed);
  }

  private static void verifyCryptFloor(ServerLevel level, StructureStart start, BoundingBox box) {
    var source = level.getChunkSource();
    for (int x = box.minX(); ; x = Math.min(x + 4, box.maxX())) {
      for (int z = box.minZ(); ; z = Math.min(z + 4, box.maxZ())) {
        var point = new BlockPos(x, box.minY() - 1, z);
        if (start.getPieces().stream().noneMatch(piece -> piece.getBoundingBox().isInside(point))) {
          var column = source.getGenerator().getBaseColumn(x, z, level, source.randomState());
          if (!solid(column.getBlock(point.getY()))) {
            throw new IllegalStateException("Crypt room has an exposed natural floor at " + point);
          }
        }
        if (z == box.maxZ()) break;
      }
      if (x == box.maxX()) break;
    }
  }

  private static void shrine(ServerLevel level) {
    if (level.getSeed() != 1)
      throw new IllegalArgumentException("Shrine shell fixture needs seed 1");
    var structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .getHolder(ResourceLocation.parse("galosphere:pink_salt_shrine"))
            .orElseThrow()
            .value();
    var start =
        level
            .getChunk(SHRINE_START.x, SHRINE_START.z, ChunkStatus.STRUCTURE_STARTS)
            .getStartForStructure(structure);
    if (start == null || !start.isValid())
      throw new IllegalStateException("Missing natural shrine");
    var arena =
        start.getPieces().stream()
            .filter(
                piece ->
                    piece instanceof PoolElementStructurePiece pool
                        && pool.getElement() instanceof SinglePoolElement single
                        && single
                            .template
                            .left()
                            .filter(id -> id.getPath().endsWith("/boss_arenas/boss_arena"))
                            .isPresent())
            .findFirst()
            .orElseThrow();
    var box = arena.getBoundingBox();
    int checked = 0;
    int exposed = 0;
    for (BlockPos point :
        BlockPos.betweenClosed(
            box.minX() - 1,
            box.minY() - 1,
            box.minZ() - 1,
            box.maxX() + 1,
            box.maxY() + 1,
            box.maxZ() + 1)) {
      int axes =
          (point.getX() < box.minX() || point.getX() > box.maxX() ? 1 : 0)
              + (point.getY() < box.minY() || point.getY() > box.maxY() ? 1 : 0)
              + (point.getZ() < box.minZ() || point.getZ() > box.maxZ() ? 1 : 0);
      if (axes != 1
          || start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().isInside(point)))
        continue;
      checked++;
      if (!solid(level.getBlockState(point))) exposed++;
    }
    if (checked < 1800 || exposed > 8) {
      throw new IllegalStateException(
          "Shrine has exposed exterior masonry: " + box + ", uncovered " + exposed + "/" + checked);
    }
    int clearance = 0;
    for (BlockPos point :
        BlockPos.betweenClosed(
            box.minX() + 2,
            box.minY() + 3,
            box.minZ() + 2,
            box.maxX() - 2,
            box.minY() + 3,
            box.maxZ() - 2)) {
      if (level.getBlockState(point).isAir() && level.getBlockState(point.above()).isAir())
        clearance++;
    }
    if (clearance < 250)
      throw new IllegalStateException("Burial blocked the native arena interior");
    LogUtils.getLogger()
        .info(
            "Shrine natural start {} arena={} outside exposed={}/{} two-block spans={}",
            start.getChunkPos(),
            box,
            exposed,
            checked,
            clearance);
  }

  private static boolean solid(BlockState state) {
    return state.getFluidState().isEmpty()
        && state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP);
  }
}
