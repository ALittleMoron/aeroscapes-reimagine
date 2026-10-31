package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Natural large-room/ground coverage, independent of structure-authored foundations. */
public final class StructureFootprintRegression {
  private record Site(String id, int chunkX, int chunkZ, boolean underground) {}

  private static final List<Site> SITES =
      List.of(
          new Site("philipsruins:ocean_fortress", -133, -348, false),
          new Site("galosphere:pink_salt_shrine", -294, -267, true));

  private StructureFootprintRegression() {}

  public static void start(ServerLevel level, Path output) {
    if (level.getSeed() != 1) throw new IllegalArgumentException("Footprint fixture needs seed 1");
    Audit audit = new Audit(level, output);
    NeoForge.EVENT_BUS.addListener(audit::tick);
  }

  private static final class Audit {
    private final ServerLevel level;
    private final Path output;
    private final JsonArray results = new JsonArray();
    private final Map<Long, NoiseColumn> columns = new HashMap<>();
    private final Set<Long> seen = new HashSet<>();
    private final boolean strict = Boolean.getBoolean("aeroscapes.regression.footprintsStrict");
    private final boolean scan = Boolean.getBoolean("aeroscapes.regression.footprintsScan");
    private final boolean search =
        Boolean.getBoolean("aeroscapes.regression.footprintsSearch") || strict && !scan;
    private int targetIndex;
    private int candidateIndex;
    private int positives;
    private int failures;
    private boolean complete;

    private Audit(ServerLevel level, Path output) {
      this.level = level;
      this.output = output;
    }

    private void tick(ServerTickEvent.Post event) {
      if (complete || event.getServer() != level.getServer()) return;
      Site site = SITES.get(targetIndex);
      var holder =
          level
              .registryAccess()
              .registryOrThrow(Registries.STRUCTURE)
              .getHolder(ResourceLocation.parse(site.id()))
              .orElseThrow();
      ChunkPos candidate = new ChunkPos(site.chunkX(), site.chunkZ());
      if (candidateIndex > 0) {
        var placement =
            level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).stream()
                .filter(RandomSpreadStructurePlacement.class::isInstance)
                .map(RandomSpreadStructurePlacement.class::cast)
                .findFirst()
                .orElseThrow();
        int index = candidateIndex - 1;
        candidate =
            placement.getPotentialStructureChunk(
                level.getSeed(),
                (Math.floorDiv(site.chunkX(), placement.spacing()) - 8 + index % 17)
                    * placement.spacing(),
                (Math.floorDiv(site.chunkZ(), placement.spacing()) - 8 + index / 17)
                    * placement.spacing());
      }
      boolean unsupported = false;
      if (seen.add(candidate.toLong())) {
        var chunk =
            level
                .getChunkSource()
                .getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS, true);
        if (chunk == null) throw new IllegalStateException("Footprint candidate unavailable");
        StructureStart start = chunk.getStartForStructure(holder.value());
        if (start != null && start.isValid()) {
          JsonObject row = describe(site, start);
          results.add(row);
          if (row.get("coverage_ok").getAsBoolean()) {
            if (search && positives < 2) {
              freshAssembly(holder.value(), candidate, start, row);
              full(site, start, row);
            }
            positives++;
          } else {
            failures++;
            unsupported = true;
          }
        }
      }
      candidateIndex++;
      if ((!search && !scan)
          || scan && unsupported
          || search && positives >= 2
          || candidateIndex > 17 * 17) {
        if (strict && search && positives < 2) failures++;
        targetIndex++;
        candidateIndex = 0;
        positives = 0;
        columns.clear();
        seen.clear();
        if (targetIndex == SITES.size()) finish();
      }
    }

    private JsonObject describe(Site site, StructureStart start) {
      // A shared world-aligned grid weights a giant arena by its exposed area,
      // without counting overlapping rooms or tiny coffin decorations repeatedly.
      Map<Long, BlockPos> envelope = new HashMap<>();
      int measuredPieces = 0;
      int rootFloor = floor(start.getPieces().getFirst());
      for (StructurePiece piece : start.getPieces()) {
        if (piece instanceof PoolElementStructurePiece pool
            && pool.getElement() instanceof FeaturePoolElement) continue;
        var bounds = piece.getBoundingBox();
        if (!site.underground()
            && (Math.abs(floor(piece) - rootFloor) > 3
                || bounds.getXSpan() < 8
                || bounds.getZSpan() < 8)) continue;
        measuredPieces++;
        for (int x = Math.floorDiv(bounds.minX() + 3, 4) * 4; x <= bounds.maxX(); x += 4)
          for (int z = Math.floorDiv(bounds.minZ() + 3, 4) * 4; z <= bounds.maxZ(); z += 4) {
            long key = ChunkPos.asLong(x, z);
            int y = site.underground() ? bounds.maxY() : floor(piece);
            BlockPos previous = envelope.get(key);
            if (previous == null
                || (site.underground() ? previous.getY() < y : previous.getY() > y))
              envelope.put(key, new BlockPos(x, y, z));
          }
      }
      int covered = 0;
      for (BlockPos point : envelope.values()) {
        NoiseColumn column = column(point.getX(), point.getZ());
        if (site.underground()) {
          if (solid(column, point.getY() + 2) && solid(column, point.getY() + 6)) covered++;
        } else {
          for (int depth = 1; depth <= 12; depth++)
            if (solid(column, point.getY() - depth)) {
              covered++;
              break;
            }
        }
      }
      JsonObject row = new JsonObject();
      row.addProperty("id", site.id());
      row.addProperty("chunk_x", start.getChunkPos().x);
      row.addProperty("chunk_z", start.getChunkPos().z);
      row.addProperty("pieces", start.getPieces().size());
      row.addProperty("measured_pieces", measuredPieces);
      row.addProperty("bounds", start.getBoundingBox().toString());
      row.addProperty("natural_samples", envelope.size());
      row.addProperty("natural_covered", covered);
      row.addProperty(
          "coverage_ok",
          !envelope.isEmpty()
              && (site.underground() || measuredPieces == 4)
              && covered * (site.underground() ? 4 : 5) >= envelope.size() * 3);
      LogUtils.getLogger().info("Structure footprint {}", row);
      return row;
    }

    private void full(Site site, StructureStart start, JsonObject row) {
      var bounds = start.getBoundingBox();
      if (!site.underground()) {
        var root = start.getPieces().getFirst();
        var rootBounds = root.getBoundingBox();
        int centerX = (rootBounds.minX() + rootBounds.maxX()) / 2;
        int centerZ = (rootBounds.minZ() + rootBounds.maxZ()) / 2;
        int seabed =
            level
                .getChunkSource()
                .getGenerator()
                .getBaseHeight(
                    centerX,
                    centerZ,
                    Heightmap.Types.OCEAN_FLOOR_WG,
                    level,
                    level.getChunkSource().randomState());
        if (floor(root) != seabed - 5 || start.getPieces().size() != 4) {
          row.addProperty("native_assembly_error", "Changed burial or four-part assembly");
          failures++;
        }
      }
      for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
        for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) level.getChunk(x, z);
      int masonry = 0;
      int air = 0;
      int water = 0;
      int loot = 0;
      for (BlockPos position :
          BlockPos.betweenClosed(
              bounds.minX(),
              bounds.minY(),
              bounds.minZ(),
              bounds.maxX(),
              bounds.maxY(),
              bounds.maxZ())) {
        var state = level.getBlockState(position);
        String id =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock())
                .toString();
        if (site.underground()
            ? id.equals("galosphere:pink_salt_bricks")
            : state.is(Blocks.STONE_BRICKS) || state.is(Blocks.PRISMARINE_BRICKS)) masonry++;
        if (state.isAir()) air++;
        if (state.is(Blocks.WATER)) water++;
        if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
          if (start.getPieces().stream()
              .noneMatch(piece -> piece.getBoundingBox().isInside(position))) continue;
          var entity = level.getBlockEntity(position);
          if (entity != null) {
            var data = entity.saveWithFullMetadata(level.registryAccess());
            String table = data.getString("LootTable");
            if (site.underground()
                ? table.equals("galosphere:chests/pink_salt_shrine")
                    || table.equals("galosphere:chests/pink_salt_shrine_library")
                    || !data.getList("Items", 10).isEmpty()
                : table.equals("philipsruins:chest/ocean_ruin_fortress")) loot++;
          }
        }
      }
      row.addProperty("full_masonry", masonry);
      row.addProperty("full_air", air);
      row.addProperty("full_water", water);
      row.addProperty("full_loot_containers", loot);
      if (masonry < 100 || (site.underground() ? air : air + water) < 100 || loot == 0) {
        row.addProperty("full_error", "Missing rooms or loot");
        failures++;
      }
      if (!site.underground() && loot != 11) {
        row.addProperty("native_loot_error", "Expected eleven native fortress chests");
        failures++;
      }
      JsonArray rooms = new JsonArray();
      for (StructurePiece piece : start.getPieces()) {
        var box = piece.getBoundingBox();
        if (piece instanceof PoolElementStructurePiece pool
                && pool.getElement() instanceof FeaturePoolElement
            || box.getXSpan() < 8
            || box.getZSpan() < 8
            || box.getYSpan() < 5) continue;
        int roomMasonry = 0;
        int clear = 0;
        for (BlockPos position :
            BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
          var state = level.getBlockState(position);
          String block =
              net.minecraft.core.registries.BuiltInRegistries.BLOCK
                  .getKey(state.getBlock())
                  .toString();
          if (site.underground()
              ? block.equals("galosphere:pink_salt_bricks")
              : state.is(Blocks.STONE_BRICKS) || state.is(Blocks.PRISMARINE_BRICKS)) roomMasonry++;
          if (state.isAir() || !site.underground() && state.is(Blocks.WATER)) clear++;
        }
        JsonObject room = new JsonObject();
        room.addProperty("bounds", box.toString());
        room.addProperty("masonry", roomMasonry);
        room.addProperty("clear", clear);
        rooms.add(room);
        if (roomMasonry < 8 || clear < 8) {
          row.addProperty("full_room_error", "Missing authored room walls or interior");
          failures++;
        }
      }
      row.add("full_rooms", rooms);
    }

    private void freshAssembly(
        Structure structure, ChunkPos candidate, StructureStart saved, JsonObject row) {
      var generator = level.getChunkSource().getGenerator();
      var context =
          new Structure.GenerationContext(
              level.registryAccess(),
              generator,
              generator.getBiomeSource(),
              level.getChunkSource().randomState(),
              level.getStructureManager(),
              level.getSeed(),
              candidate,
              level,
              structure.biomes()::contains);
      var fresh = structure.findValidGenerationPoint(context);
      if (fresh.isEmpty()) {
        row.addProperty("fresh_assembly_error", "Saved start no longer passes current placement");
        failures++;
        return;
      }
      try {
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        ListTag expected = new ListTag();
        for (var piece : fresh.orElseThrow().getPiecesBuilder().build().pieces())
          expected.add(piece.createTag(serialization));
        ListTag actual = new ListTag();
        for (var piece : saved.getPieces()) actual.add(piece.createTag(serialization));
        String digest = ResourceFingerprints.nbt(actual);
        row.addProperty("assembly_sha256", digest);
        if (!digest.equals(ResourceFingerprints.nbt(expected))) {
          row.addProperty(
              "fresh_assembly_error", "Saved pieces differ from current native assembly");
          failures++;
        }
      } catch (IOException failure) {
        throw new IllegalStateException("Cannot fingerprint native structure pieces", failure);
      }
    }

    private NoiseColumn column(int x, int z) {
      return columns.computeIfAbsent(
          ChunkPos.asLong(x, z),
          ignored ->
              level
                  .getChunkSource()
                  .getGenerator()
                  .getBaseColumn(x, z, level, level.getChunkSource().randomState()));
    }

    private void finish() {
      JsonObject report = new JsonObject();
      report.addProperty("seed", level.getSeed());
      report.addProperty("natural_selected_starts", true);
      report.addProperty("failures", failures);
      report.add("starts", results);
      try {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
      } catch (IOException failure) {
        throw new IllegalStateException("Cannot write structure footprint report", failure);
      }
      complete = true;
      if (strict && failures > 0)
        throw new IllegalStateException("Large structure footprint failures: " + failures);
      LogUtils.getLogger().info("Structure footprint regression complete: {}", output);
    }
  }

  private static int floor(StructurePiece piece) {
    return piece.getBoundingBox().minY()
        + (piece instanceof PoolElementStructurePiece pool ? pool.getGroundLevelDelta() : 0);
  }

  private static boolean solid(NoiseColumn column, int y) {
    var state = column.getBlock(y);
    return state.getFluidState().isEmpty()
        && Block.isFaceFull(
            state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP);
  }
}
