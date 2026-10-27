package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Exact natural NOISE outputs, before surface rules, carving and placed features. */
public final class NoiseSnapshotRegression {
  private static final List<ChunkPos> SITES =
      List.of(
          new ChunkPos(764, 766), new ChunkPos(765, 766),
          new ChunkPos(764, 767), new ChunkPos(765, 767),
          new ChunkPos(162, -298), new ChunkPos(163, -298));
  private static volatile Capture active;

  private NoiseSnapshotRegression() {}

  static void verify(ServerLevel level) {
    require(level.getSeed() == 1, "NOISE snapshots require host seed 1");
    require(level.dimension().equals(Level.OVERWORLD), "NOISE snapshots require the Overworld");
    String mode = System.getProperty("aeroscapes.regression.noiseSnapshot");
    require("write".equals(mode) || "verify".equals(mode), "Use noiseSnapshot=write or verify");
    String filename = System.getProperty("aeroscapes.regression.noiseSnapshotFile");
    require(filename != null, "Supply an absolute noiseSnapshotFile in ignored .runtime");
    Path path = Path.of(filename).normalize();
    require(
        path.isAbsolute() && path.toString().contains("/.runtime/"),
        "noiseSnapshotFile must be absolute and inside ignored .runtime");
    Capture capture = new Capture(level, level.getChunkSource().randomState());
    require(active == null, "Another NOISE snapshot capture is active");
    active = capture;
    try {
      for (ChunkPos site : SITES) {
        ChunkAccess chunk =
            level.getChunkSource().getChunk(site.x, site.z, ChunkStatus.NOISE, true);
        require(chunk != null, "No natural NOISE chunk " + site);
        require(
            capture.chunks.containsKey(site.toLong()),
            "Missing stage-boundary capture; use fresh NOISE chunks at " + site);
      }
      verifyCaptured(level, mode, path, capture);
    } finally {
      active = null;
    }
  }

  /** Called before the native NOISE future completes and downstream surface work can start. */
  public static CompletableFuture<ChunkAccess> captureNoise(
      ServerLevel level, ChunkPos site, CompletableFuture<ChunkAccess> future) {
    Capture capture = active;
    if (capture == null || capture.level != level || !SITES.contains(site)) return future;
    return future.thenApply(
        chunk -> {
          captureChunk(capture, chunk);
          return chunk;
        });
  }

  /** OpenCL completes NOISE in batches, bypassing the native task for already generated chunks. */
  public static void captureOpenCl(
      RandomState state, StaticCache2D<ProtoChunk> chunks, ChunkPos start, int batchSize) {
    Capture capture = active;
    if (capture == null || capture.state != state) return;
    for (ChunkPos site : SITES)
      if (site.x >= start.x
          && site.x < start.x + batchSize
          && site.z >= start.z
          && site.z < start.z + batchSize) {
        if (capture.chunks.containsKey(site.toLong())) continue;
        ProtoChunk chunk = chunks.get(site.x, site.z);
        require(
            chunk.getPersistedStatus() == ChunkStatus.NOISE,
            "OpenCL capture must follow completed block writes and WG heightmaps at " + site);
        captureChunk(capture, chunk);
      }
  }

  private static void captureChunk(Capture capture, ChunkAccess chunk) {
    if (SITES.contains(chunk.getPos()))
      capture.chunks.computeIfAbsent(
          chunk.getPos().toLong(), key -> snapshot(capture.level, chunk));
  }

  private static void verifyCaptured(ServerLevel level, String mode, Path path, Capture capture) {
    // A getChunk(NOISE) result remains mutable: equal ticket radii allow SURFACE/CARVERS next.
    // Only stage-boundary hooks freeze data; all assertions and comparisons use those copies.
    basinPositive(level, capture.chunks);
    CompoundTag actual = new CompoundTag();
    actual.putInt("format", 2);
    actual.putLong("seed", level.getSeed());
    ListTag snapshots = new ListTag();
    for (ChunkPos site : SITES) snapshots.add(capture.chunks.get(site.toLong()));
    actual.put("chunks", snapshots);
    try {
      if ("write".equals(mode)) {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "noise-snapshot-", ".nbt.tmp");
        try {
          NbtIo.writeCompressed(actual, temporary);
          Files.move(
              temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
          Files.deleteIfExists(temporary);
        }
      } else {
        CompoundTag expected = NbtIo.readCompressed(path, NbtAccounter.create(64L * 1024 * 1024));
        compare(expected, actual);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot " + mode + " NOISE snapshot " + path, exception);
    }
    LogUtils.getLogger()
        .info(
            "NOISE snapshot regression passed: mode={} chunks={} path={}",
            mode,
            SITES.size(),
            path);
  }

  static CompoundTag snapshot(ServerLevel level, ChunkAccess chunk) {
    CompoundTag data = new CompoundTag();
    data.putInt("x", chunk.getPos().x);
    data.putInt("z", chunk.getPos().z);
    data.putInt("minY", chunk.getMinBuildHeight());
    data.putInt("height", chunk.getHeight());
    Palette blocks = new Palette();
    Map<BlockState, String> names = new HashMap<>();
    int[] states = new int[256 * chunk.getHeight()];
    BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
    int index = 0;
    for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++)
      for (int z = 0; z < 16; z++)
        for (int x = 0; x < 16; x++) {
          point.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
          BlockState state = chunk.getBlockState(point);
          states[index++] =
              blocks.index(names.computeIfAbsent(state, NoiseSnapshotRegression::stateName));
        }
    data.put("blocks", blocks.encode(states));
    Palette biomes = new Palette();
    int[] biomeStates = new int[16 * chunk.getHeight() / 4];
    index = 0;
    for (int y = Math.floorDiv(chunk.getMinBuildHeight(), 4);
        y < Math.floorDiv(chunk.getMaxBuildHeight(), 4);
        y++)
      for (int z = 0; z < 4; z++)
        for (int x = 0; x < 4; x++)
          biomeStates[index++] =
              biomes.index(
                  chunk
                      .getNoiseBiome(chunk.getPos().x * 4 + x, y, chunk.getPos().z * 4 + z)
                      .unwrapKey()
                      .orElseThrow()
                      .location()
                      .toString());
    data.put("biomes", biomes.encode(biomeStates));
    CompoundTag starts = new CompoundTag();
    var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    chunk
        .getAllStarts()
        .forEach(
            (structure, start) ->
                starts.put(
                    structures.getKey(structure).toString(),
                    start.createTag(serialization, chunk.getPos())));
    data.put("starts", starts);
    CompoundTag references = new CompoundTag();
    chunk
        .getAllReferences()
        .forEach(
            (structure, positions) -> {
              long[] sorted = positions.toLongArray();
              java.util.Arrays.sort(sorted);
              references.putLongArray(structures.getKey(structure).toString(), sorted);
            });
    data.put("references", references);
    CompoundTag heightmaps = new CompoundTag();
    for (var entry : chunk.getHeightmaps())
      heightmaps.putLongArray(entry.getKey().name(), entry.getValue().getRawData().clone());
    data.put("heightmaps", heightmaps);
    return data;
  }

  private static String stateName(BlockState state) {
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
    return name.toString();
  }

  private static <T extends Comparable<T>> String propertyValue(
      BlockState state, Property<T> property) {
    return property.getName(state.getValue(property));
  }

  private static void basinPositive(ServerLevel level, Map<Long, CompoundTag> chunks) {
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    require(terrain != null, "Missing owned terrain for basin snapshot");
    var column = terrain.column(2600, -4768);
    require(column.basin() != null && column.radial() < 1, "Lost existing natural basin fixture");
    CompoundTag chunk = chunks.get(ChunkPos.asLong(162, -298));
    int water = 0;
    int stone = 0;
    int floor = (int) Math.floor(column.floor());
    for (int y = floor + 1; y <= column.basin().waterline(); y++)
      if (blockAt(chunk, 2600, y, -4768).startsWith("minecraft:water[")) water++;
    for (int y = floor - 12; y <= floor; y++)
      if (blockAt(chunk, 2600, y, -4768).equals("minecraft:stone")) stone++;
    require(
        water > 0 && stone > 0,
        "NOISE basin fixture lacks real water or stone liner: " + water + "/" + stone);
    LogUtils.getLogger()
        .info(
            "NOISE basin positive: water={} liner-stone={} floor={} waterline={}",
            water,
            stone,
            floor,
            column.basin().waterline());
  }

  private static String blockAt(CompoundTag chunk, int x, int y, int z) {
    require(
        Math.floorDiv(x, 16) == chunk.getInt("x")
            && Math.floorDiv(z, 16) == chunk.getInt("z")
            && y >= chunk.getInt("minY")
            && y < chunk.getInt("minY") + chunk.getInt("height"),
        "Basin assertion lies outside its frozen chunk");
    CompoundTag blocks = chunk.getCompound("blocks");
    int index = (y - chunk.getInt("minY")) * 256 + Math.floorMod(z, 16) * 16 + Math.floorMod(x, 16);
    return blocks.getList("palette", Tag.TAG_STRING).getString(blocks.getIntArray("data")[index]);
  }

  private static void compare(CompoundTag expected, CompoundTag actual) {
    require(
        expected.getInt("format") == 2 && expected.getLong("seed") == 1,
        "Invalid NOISE snapshot header");
    ListTag before = expected.getList("chunks", Tag.TAG_COMPOUND);
    ListTag after = actual.getList("chunks", Tag.TAG_COMPOUND);
    require(
        before.size() == SITES.size() && after.size() == SITES.size(),
        "Invalid NOISE snapshot chunk count");
    long blocks = 0;
    long biomes = 0;
    int metadata = 0;
    for (int index = 0; index < before.size(); index++) {
      CompoundTag source = before.getCompound(index);
      CompoundTag target = after.getCompound(index);
      for (String key : List.of("x", "z", "minY", "height"))
        require(source.getInt(key) == target.getInt(key), "NOISE chunk bounds differ: " + key);
      long blockChanges = differences(source.getCompound("blocks"), target.getCompound("blocks"));
      long biomeChanges = differences(source.getCompound("biomes"), target.getCompound("biomes"));
      blocks += blockChanges;
      biomes += biomeChanges;
      for (String key : List.of("starts", "references", "heightmaps"))
        if (!source.getCompound(key).equals(target.getCompound(key))) metadata++;
      LogUtils.getLogger()
          .info(
              "NOISE snapshot chunk {},{}: block-diff={} biome-diff={}",
              source.getInt("x"),
              source.getInt("z"),
              blockChanges,
              biomeChanges);
    }
    require(
        blocks == 0 && biomes == 0 && metadata == 0,
        "NOISE snapshot mismatch: blocks="
            + blocks
            + ", biomes="
            + biomes
            + ", metadata="
            + metadata);
  }

  private static long differences(CompoundTag expected, CompoundTag actual) {
    ListTag before = expected.getList("palette", Tag.TAG_STRING);
    ListTag after = actual.getList("palette", Tag.TAG_STRING);
    int[] source = expected.getIntArray("data");
    int[] target = actual.getIntArray("data");
    require(
        !before.isEmpty()
            && !after.isEmpty()
            && source.length == target.length
            && source.length > 0,
        "Invalid NOISE snapshot palette or volume");
    long differences = 0;
    for (int index = 0; index < source.length; index++) {
      require(
          source[index] >= 0
              && source[index] < before.size()
              && target[index] >= 0
              && target[index] < after.size(),
          "Invalid NOISE snapshot palette index");
      if (!before.getString(source[index]).equals(after.getString(target[index]))) differences++;
    }
    return differences;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class Capture {
    private final ServerLevel level;
    private final RandomState state;
    private final Map<Long, CompoundTag> chunks = new ConcurrentHashMap<>();

    private Capture(ServerLevel level, RandomState state) {
      this.level = level;
      this.state = state;
    }
  }

  private static final class Palette {
    private final List<String> names = new ArrayList<>();
    private final Map<String, Integer> indices = new HashMap<>();

    private int index(String name) {
      return indices.computeIfAbsent(
          name,
          value -> {
            names.add(value);
            return names.size() - 1;
          });
    }

    private CompoundTag encode(int[] values) {
      ListTag palette = new ListTag();
      for (String name : names) palette.add(StringTag.valueOf(name));
      CompoundTag encoded = new CompoundTag();
      encoded.put("palette", palette);
      encoded.putIntArray("data", values);
      return encoded;
    }
  }
}
