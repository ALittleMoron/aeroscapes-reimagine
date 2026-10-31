package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Diagnoses the owner's exposed dungeon using naturally selected starts on the actual host seed.
 */
public final class Release021WorldgenRegression {
  private static final long SEED = -3603931612419901008L;
  private static final BlockPos REPORTED = new BlockPos(5800, 172, 1900);

  private Release021WorldgenRegression() {}

  public static void start(ServerLevel level, Path output) {
    if (level.getSeed() != SEED)
      throw new IllegalArgumentException("Release 0.2.1 dungeon diagnostic needs seed " + SEED);
    Audit audit = new Audit(level, output);
    NeoForge.EVENT_BUS.addListener(audit::tick);
  }

  private static final class Audit {
    private final ServerLevel level;
    private final Path output;
    private final JsonArray starts = new JsonArray();
    private final JsonArray positiveCrypts = new JsonArray();
    private final Map<Long, NoiseColumn> columns = new HashMap<>();
    private final boolean search =
        Boolean.getBoolean("aeroscapes.regression.release021CryptSearch");
    private final boolean strict = Boolean.getBoolean("aeroscapes.regression.release021Strict");
    private int cursor;
    private int searched;
    private boolean complete;

    private Audit(ServerLevel level, Path output) {
      this.level = level;
      this.output = output;
    }

    private void tick(ServerTickEvent.Post event) {
      if (complete || event.getServer() != level.getServer()) return;
      if (cursor == 17 * 17) {
        search();
        return;
      }
      int chunkX = (REPORTED.getX() >> 4) - 8 + cursor % 17;
      int chunkZ = (REPORTED.getZ() >> 4) - 8 + cursor / 17;
      var chunk =
          level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.STRUCTURE_STARTS, true);
      if (chunk == null)
        throw new IllegalStateException("Dungeon diagnostic chunk was unavailable");
      var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      for (var entry : chunk.getAllStarts().entrySet()) {
        StructureStart start = entry.getValue();
        if (start.isValid() && start.getBoundingBox().inflatedBy(16).isInside(REPORTED)) {
          starts.add(describe(registry.getKey(entry.getKey()).toString(), start));
        }
      }
      cursor++;
      if (cursor == 17 * 17 && !search) finish();
    }

    private void search() {
      var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      var crypt =
          registry
              .getHolder(ResourceLocation.parse("nova_structures:creeping_crypt"))
              .orElseThrow();
      var placement =
          level.getChunkSource().getGeneratorState().getPlacementsForStructure(crypt).stream()
              .filter(RandomSpreadStructurePlacement.class::isInstance)
              .map(RandomSpreadStructurePlacement.class::cast)
              .findFirst()
              .orElseThrow();
      int originX = Math.floorDiv(REPORTED.getX() >> 4, placement.spacing());
      int originZ = Math.floorDiv(REPORTED.getZ() >> 4, placement.spacing());
      int regionX = originX - 8 + searched % 17;
      int regionZ = originZ - 8 + searched / 17;
      ChunkPos candidate =
          placement.getPotentialStructureChunk(
              level.getSeed(), regionX * placement.spacing(), regionZ * placement.spacing());
      var chunk =
          level
              .getChunkSource()
              .getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS, true);
      if (chunk == null) throw new IllegalStateException("Crypt search chunk was unavailable");
      var start = chunk.getStartForStructure(crypt.value());
      if (start != null && start.isValid()) {
        JsonObject positive = describe("nova_structures:creeping_crypt", start);
        fullEntrance(start, positive);
        fullRooms(start, positive);
        positiveCrypts.add(positive);
      }
      searched++;
      if (positiveCrypts.size() >= 2 || searched == 17 * 17) finish();
    }

    private void fullEntrance(StructureStart start, JsonObject report) {
      BoundingBox entrance = start.getPieces().getFirst().getBoundingBox();
      for (int x = entrance.minX() >> 4; x <= entrance.maxX() >> 4; x++)
        for (int z = entrance.minZ() >> 4; z <= entrance.maxZ() >> 4; z++) level.getChunk(x, z);
      int air = 0;
      int masonry = 0;
      for (BlockPos position :
          BlockPos.betweenClosed(
              entrance.minX(),
              entrance.minY(),
              entrance.minZ(),
              entrance.maxX(),
              entrance.maxY(),
              entrance.maxZ())) {
        var state = level.getBlockState(position);
        if (state.isAir()) air++;
        if (state.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS)
            || state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)
            || state.is(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE)) masonry++;
      }
      if (air < 8 || masonry < 8)
        throw new IllegalStateException("Crypt FULL entrance template is missing");
      report.addProperty("full_entrance_air", air);
      report.addProperty("full_entrance_masonry", masonry);
      report.addProperty("full_entrance_only", true);
    }

    private void fullRooms(StructureStart start, JsonObject report) {
      int entranceFloor = start.getPieces().getFirst().getBoundingBox().minY();
      List<StructurePiece> rooms =
          start.getPieces().stream()
              .filter(
                  piece ->
                      piece.getBoundingBox().maxY() < entranceFloor - 4
                          && piece.getBoundingBox().getXSpan() >= 8
                          && piece.getBoundingBox().getZSpan() >= 8
                          && piece.getBoundingBox().getYSpan() >= 5)
              .toList();
      if (rooms.size() < 2)
        throw new IllegalStateException("Crypt lost its main underground rooms");
      LinkedHashSet<StructurePiece> selected = new LinkedHashSet<>();
      selected.add(
          rooms.stream()
              .min(
                  Comparator.comparingDouble(
                      piece ->
                          piece
                              .getBoundingBox()
                              .getCenter()
                              .distSqr(start.getBoundingBox().getCenter())))
              .orElseThrow());
      selected.add(
          rooms.stream()
              .min(Comparator.comparingInt(piece -> piece.getBoundingBox().minX()))
              .orElseThrow());
      selected.add(
          rooms.stream()
              .max(Comparator.comparingInt(piece -> piece.getBoundingBox().maxX()))
              .orElseThrow());
      JsonArray samples = new JsonArray();
      JsonArray checkedRooms = new JsonArray();
      for (StructurePiece piece : selected) {
        BoundingBox box = piece.getBoundingBox();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++)
          for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) level.getChunk(x, z);
        int air = 0;
        int masonry = 0;
        int floor = 0;
        for (BlockPos position :
            BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
          var state = level.getBlockState(position);
          boolean authored =
              state.is(Blocks.STONE_BRICKS)
                  || state.is(Blocks.MOSSY_STONE_BRICKS)
                  || state.is(Blocks.CRACKED_STONE_BRICKS)
                  || state.is(Blocks.COBBLESTONE)
                  || state.is(Blocks.MOSSY_COBBLESTONE);
          if (state.isAir()) {
            if (air < 3) samples.add(sample(position));
            air++;
          }
          if (authored) {
            if (masonry < 3) samples.add(sample(position));
            masonry++;
          }
          if (position.getY() == box.minY() && state.isFaceSturdy(level, position, Direction.UP))
            floor++;
        }
        if (air < 8 || masonry < 8 || floor < 8)
          throw new IllegalStateException("Crypt FULL room lost air, walls or floor: " + box);
        JsonObject room = new JsonObject();
        room.add("bounds", bounds(box));
        room.addProperty("air", air);
        room.addProperty("masonry", masonry);
        room.addProperty("floor", floor);
        checkedRooms.add(room);
      }
      boolean loot = false;
      for (StructurePiece piece : start.getPieces()) {
        if (!(piece instanceof PoolElementStructurePiece pool)
            || !(pool.getElement() instanceof SinglePoolElement)) continue;
        var encoded =
            StructurePoolElement.CODEC
                .encodeStart(
                    RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), pool.getElement())
                .getOrThrow()
                .getAsJsonObject();
        if (!encoded.has("location")
            || !encoded.get("location").getAsString().endsWith("/creeping_vault")) continue;
        BoundingBox box = piece.getBoundingBox();
        for (BlockPos position :
            BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
          level.getChunk(position.getX() >> 4, position.getZ() >> 4);
          var entity = level.getBlockEntity(position);
          if (level.getBlockState(position).is(Blocks.VAULT) && entity != null) {
            String table =
                entity
                    .saveWithFullMetadata(level.registryAccess())
                    .getCompound("config")
                    .getString("loot_table");
            if (table.startsWith("nova_structures:")) {
              samples.add(sample(position));
              report.addProperty("full_native_loot_table", table);
              loot = true;
              break;
            }
          }
        }
        if (loot) break;
      }
      if (!loot) throw new IllegalStateException("Crypt lost its native vault loot");
      report.add("full_rooms", checkedRooms);
      report.add("static_samples", samples);
      report.addProperty("full_entrance_only", false);
    }

    private JsonObject sample(BlockPos position) {
      JsonObject result = new JsonObject();
      result.addProperty("x", position.getX());
      result.addProperty("y", position.getY());
      result.addProperty("z", position.getZ());
      result.addProperty("state", level.getBlockState(position).toString());
      return result;
    }

    private void reloadBaseline() {
      String previous = System.getProperty("aeroscapes.regression.release021ReloadBaseline");
      if (previous == null) return;
      try {
        var baseline =
            JsonParser.parseString(Files.readString(Path.of(previous))).getAsJsonObject();
        for (var value : baseline.getAsJsonArray("positive_crypt_starts")) {
          var expected = value.getAsJsonObject();
          JsonObject actual = null;
          for (var current : positiveCrypts) {
            var candidate = current.getAsJsonObject();
            if (candidate.get("chunk_x").equals(expected.get("chunk_x"))
                && candidate.get("chunk_z").equals(expected.get("chunk_z"))) actual = candidate;
          }
          if (actual == null
              || !actual.get("bounds").equals(expected.get("bounds"))
              || actual.getAsJsonArray("pieces").size() != expected.getAsJsonArray("pieces").size())
            throw new IllegalStateException("Saved crypt assembly moved or lost pieces");
          if (!actual.get("full_native_loot_table").equals(expected.get("full_native_loot_table")))
            throw new IllegalStateException("Saved crypt loot changed");
          for (var item : expected.getAsJsonArray("static_samples")) {
            var expectedState = item.getAsJsonObject();
            BlockPos position =
                new BlockPos(
                    expectedState.get("x").getAsInt(),
                    expectedState.get("y").getAsInt(),
                    expectedState.get("z").getAsInt());
            if (!sample(position).equals(expectedState))
              throw new IllegalStateException("Saved crypt block changed at " + position);
          }
        }
      } catch (IOException failure) {
        throw new IllegalStateException("Cannot read crypt reload baseline " + previous, failure);
      }
    }

    private JsonObject describe(String id, StructureStart start) {
      JsonObject result = new JsonObject();
      result.addProperty("id", id);
      result.addProperty("chunk_x", start.getChunkPos().x);
      result.addProperty("chunk_z", start.getChunkPos().z);
      result.add("bounds", bounds(start.getBoundingBox()));
      JsonArray pieces = new JsonArray();
      int tested = 0;
      int floors = 0;
      int roofs = 0;
      int rootFloor = start.getPieces().getFirst().getBoundingBox().minY();
      for (var piece : start.getPieces()) {
        BoundingBox box = piece.getBoundingBox();
        JsonObject item = new JsonObject();
        item.addProperty("type", piece.getClass().getName());
        item.add("bounds", bounds(box));
        if (piece
            instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool)
          item.addProperty("element", pool.getElement().toString());
        boolean underground = box.maxY() < rootFloor;
        item.addProperty("below_entrance", underground);
        if (underground && box.getXSpan() >= 5 && box.getZSpan() >= 5) {
          for (int ix = 0; ix < 3; ix++)
            for (int iz = 0; iz < 3; iz++) {
              int x = box.minX() + (box.getXSpan() - 1) * ix / 2;
              int z = box.minZ() + (box.getZSpan() - 1) * iz / 2;
              NoiseColumn column = column(x, z);
              tested++;
              if (!column.getBlock(box.minY() - 2).isAir()) floors++;
              if (!column.getBlock(box.maxY() + 2).isAir()) roofs++;
            }
        }
        pieces.add(item);
      }
      result.addProperty("underground_samples", tested);
      result.addProperty("natural_floor_samples", floors);
      result.addProperty("natural_roof_samples", roofs);
      result.add("pieces", pieces);
      LogUtils.getLogger().info("Release 0.2.1 dungeon candidate {}", result);
      return result;
    }

    private NoiseColumn column(int x, int z) {
      long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
      return columns.computeIfAbsent(
          key,
          ignored ->
              level
                  .getChunkSource()
                  .getGenerator()
                  .getBaseColumn(x, z, level, level.getChunkSource().randomState()));
    }

    private void finish() {
      JsonObject report = new JsonObject();
      report.addProperty("seed", level.getSeed());
      report.addProperty("chunks_checked", cursor);
      report.addProperty("natural_starts_only", true);
      report.addProperty("full_structure_generation", false);
      report.addProperty(
          "full_generation_scope",
          positiveCrypts.isEmpty()
              ? "none"
              : "positive entrance, central/edge rooms and native vault");
      report.add("matching_starts", starts);
      report.addProperty("crypt_candidates_searched", searched);
      report.add("positive_crypt_starts", positiveCrypts);
      reloadBaseline();
      if (strict) {
        for (var value : starts) {
          JsonObject start = value.getAsJsonObject();
          if (!start.get("id").getAsString().equals("nova_structures:creeping_crypt")) continue;
          int tested = start.get("underground_samples").getAsInt();
          if (start.get("natural_floor_samples").getAsInt() * 2 < tested
              || start.get("natural_roof_samples").getAsInt() * 2 < tested)
            throw new IllegalStateException(
                "Reported crypt still mostly extends into natural void");
        }
        if (search && positiveCrypts.isEmpty())
          throw new IllegalStateException(
              "No positive naturally selected crypt survived the bounded search");
      }
      try {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
      } catch (IOException failure) {
        throw new IllegalStateException("Cannot write dungeon diagnostic " + output, failure);
      }
      complete = true;
      columns.clear();
      LogUtils.getLogger().info("Release 0.2.1 worldgen diagnostic complete: {}", output);
    }
  }

  private static JsonArray bounds(BoundingBox bounds) {
    JsonArray result = new JsonArray();
    for (int value :
        new int[] {
          bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()
        }) result.add(value);
    return result;
  }
}
