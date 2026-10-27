package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Positive natural generation cases found in the pinned seed-one survival world. */
final class VanillaRegression {
  private static final String SHELL_FLAG = "aeroscapes_reimagine:complete_shell";
  private static final ArrayDeque<Runnable> JOBS = new ArrayDeque<>();
  private static int ticks;

  private VanillaRegression() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(VanillaRegression::tick);
  }

  static void begin(ServerLevel level) {
    require(level.getSeed() == 1, "Use the verified seed-one vanilla fixture");
    String selected = System.getProperty("aeroscapes.regression.vanilla", "all");
    if (selected.equals("region")) {
      JOBS.add(() -> RegionScanRegression.verify(level));
      return;
    }
    if (selected.equals("city")) {
      JOBS.add(() -> AncientCityRegression.verify(level));
      JOBS.add(() -> LogUtils.getLogger().info("Vanilla city regression passed"));
      return;
    }
    if (selected.equals("templates")) {
      JOBS.add(() -> GroundedColumnRegression.verifyToxic(level));
      JOBS.add(
          () ->
              NativeSurfaceRegression.oceanRuin(
                  level, "ocean_ruin_cold", 5186, -1720, Blocks.SUSPICIOUS_GRAVEL));
      JOBS.add(
          () ->
              NativeSurfaceRegression.oceanRuin(
                  level, "ocean_ruin_warm", 3080, -6892, Blocks.SUSPICIOUS_SAND));
      JOBS.add(() -> LogUtils.getLogger().info("Template placement regression passed"));
      return;
    }
    boolean natural = selected.equals("natural");
    require(selected.equals("all") || natural, "Unknown vanilla regression selection " + selected);
    JOBS.add(() -> RegionScanRegression.verify(level));
    if (!natural) JOBS.add(() -> stronghold(level));
    JOBS.add(() -> AncientCityRegression.verify(level));
    JOBS.add(() -> trialChambers(level));
    JOBS.add(() -> NativeSurfaceRegression.nativeNegatives(level));
    JOBS.add(() -> NativeSurfaceRegression.pyramid(level));
    JOBS.add(() -> NativeSurfaceRegression.generateIgloo(level));
    JOBS.add(() -> NativeSurfaceRegression.swampHut(level));
    JOBS.add(() -> NativeSurfaceRegression.generateMansion(level));
    JOBS.add(() -> GroundedColumnRegression.verifyToxic(level));
    JOBS.add(() -> NativeSurfaceRegression.oceanNegative(level));
    JOBS.add(
        () ->
            NativeSurfaceRegression.oceanRuin(
                level, "ocean_ruin_cold", 5186, -1720, Blocks.SUSPICIOUS_GRAVEL));
    JOBS.add(
        () ->
            NativeSurfaceRegression.oceanRuin(
                level, "ocean_ruin_warm", 3080, -6892, Blocks.SUSPICIOUS_SAND));
    JOBS.add(() -> MonumentRegression.generate(level, 614, 270));
    JOBS.add(() -> MonumentRegression.settled(level, 614, 270));
    JOBS.add(() -> MonumentRegression.generate(level, -206, 723));
    JOBS.add(() -> MonumentRegression.settled(level, -206, 723));
    JOBS.add(iglooCompletion(level));
    JOBS.add(mansionCompletion(level));
    JOBS.add(
        () ->
            LogUtils.getLogger()
                .info(
                    natural
                        ? "Natural generation regression passed"
                        : "Worldgen data regression passed"));
  }

  private static Runnable iglooCompletion(ServerLevel level) {
    return new Runnable() {
      private int attempts;

      @Override
      public void run() {
        // Generation completion does not imply that entity sections are visible yet.
        // Retry on normal ticks, then fail with the original content assertions intact.
        if (!NativeSurfaceRegression.iglooWhenReady(level, ++attempts == 6)) JOBS.addFirst(this);
      }
    };
  }

  private static Runnable mansionCompletion(ServerLevel level) {
    return new Runnable() {
      private int attempts;

      @Override
      public void run() {
        if (!NativeSurfaceRegression.mansionWhenReady(level, ++attempts == 6)) JOBS.addFirst(this);
      }
    };
  }

  private static void tick(ServerTickEvent.Post event) {
    if (!JOBS.isEmpty() && ++ticks % 100 == 0) {
      // Let the previous job's temporary chunk tickets expire between distant sites.
      JOBS.removeFirst().run();
    }
  }

  private static StructureStart start(ServerLevel level, String id, int x, int z) {
    var structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.parse(id));
    require(structure != null, "Missing structure " + id);
    StructureStart start = level.getChunk(x, z).getStartForStructure(structure);
    require(start != null && start.isValid(), "Missing natural start " + id + " at " + x + "," + z);
    require(
        start.getBoundingBox().minY() >= level.getMinBuildHeight() + 4
            && start.getBoundingBox().maxY() < level.getMaxBuildHeight(),
        "Clipped assembly " + id);
    return start;
  }

  private static void load(ServerLevel level, BoundingBox bounds) {
    for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
      for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) level.getChunk(x, z);
    }
  }

  private static int count(ServerLevel level, BoundingBox bounds, Block block) {
    load(level, bounds);
    int count = 0;
    for (BlockPos position :
        BlockPos.betweenClosed(
            bounds.minX(),
            bounds.minY(),
            bounds.minZ(),
            bounds.maxX(),
            bounds.maxY(),
            bounds.maxZ())) {
      var state = level.getBlockState(position);
      if (state.is(block)) count++;
    }
    return count;
  }

  private static void stronghold(ServerLevel level) {
    StructureStart start = start(level, "minecraft:stronghold", 48, -874);
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    for (StructurePiece piece : start.getPieces()) {
      require(
          piece.createTag(serialization).getBoolean(SHELL_FLAG), "Unmarked accepted native room");
    }
    var tag = start.createTag(serialization, start.getChunkPos());
    StructureStart reloaded =
        Objects.requireNonNull(StructureStart.loadStaticStart(serialization, tag, level.getSeed()));
    require(reloaded.getBoundingBox().equals(start.getBoundingBox()), "Reload moved stronghold");
    for (StructurePiece piece : reloaded.getPieces()) {
      require(
          piece.createTag(serialization).getBoolean(SHELL_FLAG), "Lost room shell flag on reload");
    }
    var nativeTag = tag.copy();
    var children = nativeTag.getList("Children", 10);
    for (int index = 0; index < children.size(); index++)
      children.getCompound(index).remove(SHELL_FLAG);
    StructureStart unmarked =
        Objects.requireNonNull(
            StructureStart.loadStaticStart(serialization, nativeTag, level.getSeed()));
    require(
        unmarked.getPieces().stream()
            .noneMatch(piece -> piece.createTag(serialization).getBoolean(SHELL_FLAG)),
        "Changed unmarked native semantics");
    StructurePiece portal =
        start.getPieces().stream()
            .filter(piece -> piece instanceof StrongholdPieces.PortalRoom)
            .findFirst()
            .orElseThrow();
    require(
        count(level, portal.getBoundingBox(), Blocks.END_PORTAL_FRAME) == 12,
        "Incomplete End portal");
    int exposedFloors = 0;
    for (StructurePiece piece : start.getPieces()) {
      if (!(piece instanceof StrongholdPieces.Straight)) continue;
      BlockPos floor = world(piece, 2, 0, 3);
      if (!level
          .getChunkSource()
          .getGenerator()
          .getBaseColumn(floor.getX(), floor.getZ(), level, level.getChunkSource().randomState())
          .getBlock(floor.getY())
          .isAir()) continue;
      load(level, piece.getBoundingBox());
      require(
          !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty(),
          "Missing native corridor floor over raw air");
      if (++exposedFloors == 3) break;
    }
    require(exposedFloors == 3, "Stronghold fixture no longer exercises exposed corridors");
    load(level, start.getBoundingBox());
    require(
        walkPath(
            level, start, world(start.getPieces().getFirst(), 2, 1, 4), world(portal, 5, 1, 0)),
        "Native doorways do not connect to the portal room");
  }

  private static boolean passable(ServerLevel level, BlockPos position) {
    var state = level.getBlockState(position);
    return state.getFluidState().isEmpty()
        && (state.getBlock() instanceof DoorBlock
            || state.getCollisionShape(level, position).isEmpty());
  }

  private static boolean standing(ServerLevel level, StructureStart start, BlockPos position) {
    return start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().isInside(position))
        && passable(level, position)
        && passable(level, position.above())
        && !level
            .getBlockState(position.below())
            .getCollisionShape(level, position.below())
            .isEmpty();
  }

  private static boolean walkPath(
      ServerLevel level, StructureStart start, BlockPos origin, BlockPos goal) {
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    Set<Long> seen = new HashSet<>();
    queue.add(origin);
    seen.add(origin.asLong());
    while (!queue.isEmpty() && seen.size() < 100_000) {
      BlockPos current = queue.removeFirst();
      if (current.equals(goal)) return true;
      for (Direction direction : Direction.Plane.HORIZONTAL) {
        for (int offset : new int[] {0, 1, -1, -2, -3}) {
          BlockPos next = current.relative(direction).offset(0, offset, 0);
          if (seen.contains(next.asLong())
              || !standing(level, start, next)
              || offset == 1 && !passable(level, current.above(2))) continue;
          boolean clear = true;
          for (int fall = 1; fall < -offset; fall++) {
            if (!passable(level, next.above(fall + 1))) clear = false;
          }
          if (clear) {
            seen.add(next.asLong());
            queue.addLast(next);
          }
        }
      }
    }
    return false;
  }

  private static BlockPos world(StructurePiece piece, int x, int y, int z) {
    BoundingBox box = piece.getBoundingBox();
    Direction direction = Objects.requireNonNull(piece.getOrientation());
    int worldX =
        switch (direction) {
          case NORTH, SOUTH -> box.minX() + x;
          case WEST -> box.maxX() - z;
          case EAST -> box.minX() + z;
          default -> throw new IllegalStateException("Vertical stronghold orientation");
        };
    int worldZ =
        switch (direction) {
          case NORTH -> box.maxZ() - z;
          case SOUTH -> box.minZ() + z;
          case WEST, EAST -> box.minZ() + x;
          default -> throw new IllegalStateException("Vertical stronghold orientation");
        };
    return new BlockPos(worldX, box.minY() + y, worldZ);
  }

  private static void trialChambers(ServerLevel level) {
    StructureStart start = start(level, "minecraft:trial_chambers", 175, -126);
    require(
        templateBlock(level, start, Blocks.TRIAL_SPAWNER), "Trial chamber has no actual spawner");
    require(templateBlock(level, start, Blocks.VAULT), "Trial chamber has no actual vault");
  }

  private static boolean templateBlock(ServerLevel level, StructureStart start, Block block) {
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    for (StructurePiece piece : start.getPieces()) {
      var element = piece.createTag(serialization).getCompound("pool_element");
      if (!element.contains("location", 8)) continue;
      var template =
          level
              .getStructureManager()
              .getOrCreate(ResourceLocation.parse(element.getString("location")));
      if (!template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).isEmpty()
          && count(level, piece.getBoundingBox(), block) > 0) return true;
    }
    return false;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
