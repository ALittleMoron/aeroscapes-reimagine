package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Uses only Minecraft APIs so the same diagnostic JAR can capture the previous production mod. */
public final class SaveUpgradeRegression {
  private static final long SEED = -3603931612419901008L;
  private static final ChunkPos START = new ChunkPos(361, 116);
  private static final BoundingBox OLD_BOUNDS = new BoundingBox(5681, 160, 1746, 5879, 217, 1923);

  private record Site(String role, ChunkPos position) {}

  private SaveUpgradeRegression() {}

  public static void verify(ServerLevel level, Path output) {
    require(level.getSeed() == SEED, "Save upgrade fixture needs seed " + SEED);
    level.getServer().tickRateManager().setFrozen(true);
    List<Site> sites =
        List.of(
            new Site("crypt_start", START),
            new Site("crypt_entrance", new ChunkPos(360, 115)),
            new Site("crypt_loot_room", new ChunkPos(363, 114)),
            new Site("spawn", new ChunkPos(level.getSharedSpawnPos())));
    String baselinePath = System.getProperty("aeroscapes.regression.saveUpgradeBaseline");
    JsonObject expected = baselinePath == null ? null : readBaseline(Path.of(baselinePath));
    if (expected != null) {
      require(expected.get("seed").getAsLong() == SEED, "Save upgrade baseline has another seed");
      require(expected.get("schema").getAsInt() == 1, "Unsupported save upgrade baseline");
      for (Site site : sites) requireSavedFull(level, site.position());
    }
    Map<ChunkPos, LevelChunk> loaded = new HashMap<>();
    for (Site site : sites)
      loaded.put(site.position(), level.getChunk(site.position().x, site.position().z));
    require(loaded.size() == 4, "Save upgrade fixture needs four distinct sample chunks");
    JsonObject snapshot = new JsonObject();
    snapshot.add("spawn", position(level.getSharedSpawnPos()));
    snapshot.add("crypt", crypt(level, loaded.get(START)));
    JsonArray chunks = new JsonArray();
    int chestTables = 0;
    int vaultTables = 0;
    for (Site site : sites) {
      JsonObject chunk = chunk(level, site, loaded.get(site.position()));
      chunks.add(chunk);
      chestTables += chunk.get("chest_loot_tables").getAsInt();
      vaultTables += chunk.get("vault_loot_tables").getAsInt();
    }
    require(chestTables > 0, "Old FULL sample chunks have no unopened chest loot tables");
    require(vaultTables > 0, "Old FULL sample chunks have no native vault loot table");
    snapshot.add("chunks", chunks);
    JsonObject report = new JsonObject();
    report.addProperty("schema", 1);
    report.addProperty("seed", SEED);
    report.addProperty("block_hash_order", "section bottom-to-top; y,z,x; named state plus LF");
    report.addProperty("sample_full_chunks", sites.size());
    report.addProperty("world_ticks_frozen", level.getServer().tickRateManager().isFrozen());
    report.add("snapshot", snapshot);
    boolean matches = expected == null || snapshot.equals(expected.get("snapshot"));
    report.addProperty("comparison", expected == null ? "capture" : matches ? "matched" : "FAILED");
    if (baselinePath != null) report.addProperty("baseline", baselinePath);
    write(output, report);
    require(
        matches,
        "Saved FULL world changed: compare snapshot in " + output + " with " + baselinePath);
    LogUtils.getLogger()
        .info(
            "Save upgrade regression passed: mode={}, chunks=4, chest_tables={}, vault_tables={},"
                + " report={}",
            expected == null ? "capture" : "compare",
            chestTables,
            vaultTables,
            output);
  }

  private static JsonObject readBaseline(Path path) {
    try {
      return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read save upgrade baseline " + path, failure);
    }
  }

  private static void requireSavedFull(ServerLevel level, ChunkPos position) {
    var tag = level.getChunkSource().chunkMap.read(position).join().orElseThrow();
    require(
        ChunkSerializer.getChunkTypeFromTag(tag) == ChunkType.LEVELCHUNK
            && tag.getInt("xPos") == position.x
            && tag.getInt("zPos") == position.z,
        "Migration comparison requires an already saved FULL chunk at " + position);
  }

  private static JsonObject crypt(ServerLevel level, LevelChunk chunk) {
    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    var structure =
        registry.get(
            net.minecraft.resources.ResourceLocation.parse("nova_structures:creeping_crypt"));
    require(structure != null, "Missing native creeping crypt registry entry");
    StructureStart start = chunk.getStartForStructure(structure);
    require(start != null && start.isValid(), "Original saved crypt start was lost");
    if (start == null) throw new IllegalStateException("Original crypt start is null");
    require(start.getBoundingBox().equals(OLD_BOUNDS), "Original protruding crypt bounds changed");
    require(start.getPieces().size() == 257, "Original crypt piece count changed");
    JsonObject result = new JsonObject();
    result.addProperty("id", registry.getKey(start.getStructure()).toString());
    result.add("bounds", bounds(start.getBoundingBox()));
    JsonArray pieces = new JsonArray();
    for (var piece : start.getPieces()) {
      JsonObject item = new JsonObject();
      item.addProperty(
          "type", BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType()).toString());
      item.add("bounds", bounds(piece.getBoundingBox()));
      item.addProperty("depth", piece.getGenDepth());
      item.addProperty(
          "orientation",
          piece.getOrientation() == null ? "none" : piece.getOrientation().getSerializedName());
      pieces.add(item);
    }
    result.add("pieces", pieces);
    return result;
  }

  private static JsonObject chunk(ServerLevel level, Site site, LevelChunk chunk) {
    JsonObject result = new JsonObject();
    result.addProperty("role", site.role());
    result.addProperty("chunk_x", site.position().x);
    result.addProperty("chunk_z", site.position().z);
    result.addProperty("min_y", chunk.getMinBuildHeight());
    result.addProperty("height", chunk.getHeight());
    MessageDigest all = digest();
    JsonArray sections = new JsonArray();
    Map<BlockState, byte[]> names = new HashMap<>();
    BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
    int nonAir = 0;
    int masonry = 0;
    for (int minY = chunk.getMinBuildHeight(); minY < chunk.getMaxBuildHeight(); minY += 16) {
      MessageDigest section = digest();
      for (int y = minY; y < minY + 16; y++)
        for (int z = 0; z < 16; z++)
          for (int x = 0; x < 16; x++) {
            point.set(site.position().getMinBlockX() + x, y, site.position().getMinBlockZ() + z);
            BlockState state = chunk.getBlockState(point);
            byte[] encoded = names.computeIfAbsent(state, SaveUpgradeRegression::stateBytes);
            section.update(encoded);
            all.update(encoded);
            if (!state.isAir()) nonAir++;
            if (state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.MOSSY_STONE_BRICKS)
                || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE)) masonry++;
          }
      JsonObject slice = new JsonObject();
      slice.addProperty("min_y", minY);
      slice.addProperty("states_sha256", HexFormat.of().formatHex(section.digest()));
      sections.add(slice);
    }
    require(nonAir > 0, "Empty FULL migration sample " + site);
    if (!site.role().equals("spawn"))
      require(masonry >= 8, "Crypt sample lost its masonry: " + site);
    result.addProperty("non_air", nonAir);
    result.addProperty("masonry", masonry);
    result.addProperty("states_sha256", HexFormat.of().formatHex(all.digest()));
    result.add("sections", sections);
    loot(level, chunk, result);
    return result;
  }

  private static void loot(ServerLevel level, LevelChunk chunk, JsonObject result) {
    JsonArray loot = new JsonArray();
    int chests = 0;
    int vaults = 0;
    for (BlockPos point :
        chunk.getBlockEntitiesPos().stream()
            .sorted(Comparator.comparingLong(BlockPos::asLong))
            .toList()) {
      var entity = chunk.getBlockEntity(point);
      if (entity == null)
        throw new IllegalStateException("Saved block entity is missing at " + point);
      String table = null;
      String kind = null;
      if (entity instanceof RandomizableContainerBlockEntity container
          && container.getLootTable() != null) {
        table = container.getLootTable().location().toString();
        kind = "container";
        chests++;
      } else if (chunk.getBlockState(point).is(Blocks.VAULT)) {
        table =
            entity
                .saveWithFullMetadata(level.registryAccess())
                .getCompound("config")
                .getString("loot_table");
        kind = "vault";
        if (!table.isEmpty()) vaults++;
      }
      if (table == null || table.isEmpty()) continue;
      JsonObject item = new JsonObject();
      item.add("position", position(point));
      item.addProperty("kind", kind);
      item.addProperty(
          "block_entity_type",
          BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString());
      item.addProperty("loot_table", table);
      loot.add(item);
    }
    result.add("loot", loot);
    result.addProperty("chest_loot_tables", chests);
    result.addProperty("vault_loot_tables", vaults);
  }

  private static byte[] stateBytes(BlockState state) {
    StringBuilder name =
        new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    List<Property<?>> properties =
        state.getProperties().stream().sorted(Comparator.comparing(Property::getName)).toList();
    if (!properties.isEmpty()) {
      name.append('[');
      for (int index = 0; index < properties.size(); index++) {
        if (index > 0) name.append(',');
        Property<?> property = properties.get(index);
        name.append(property.getName()).append('=').append(propertyValue(state, property));
      }
      name.append(']');
    }
    return name.append('\n').toString().getBytes(StandardCharsets.UTF_8);
  }

  private static <T extends Comparable<T>> String propertyValue(
      BlockState state, Property<T> property) {
    return property.getName(state.getValue(property));
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 unavailable", failure);
    }
  }

  private static JsonArray position(BlockPos point) {
    JsonArray result = new JsonArray();
    for (int value : new int[] {point.getX(), point.getY(), point.getZ()}) result.add(value);
    return result;
  }

  private static JsonArray bounds(BoundingBox box) {
    JsonArray result = new JsonArray();
    for (int value :
        new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()})
      result.add(value);
    return result;
  }

  private static void write(Path output, JsonObject report) {
    try {
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write save upgrade report " + output, failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
