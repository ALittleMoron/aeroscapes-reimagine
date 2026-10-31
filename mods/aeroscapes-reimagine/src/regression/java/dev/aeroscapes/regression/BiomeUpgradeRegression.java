package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.Heightmap;

/** Saved FULL data survives an upgrade; new generation is checked only on the upgraded copy. */
public final class BiomeUpgradeRegression {
  private static final ResourceLocation VOID = ResourceLocation.parse("aeroscapes_reimagine:void");
  private static final List<ChunkPos> CENTERS =
      List.of(
          new ChunkPos(-176, -320),
          new ChunkPos(1754, -1294),
          new ChunkPos(-1773, 724),
          new ChunkPos(44, -1111),
          new ChunkPos(432, -352),
          new ChunkPos(1907, -1227),
          new ChunkPos(32, -32),
          new ChunkPos(-1248, 453));

  private record SavedSection(
      PalettedContainer<BlockState> blocks, PalettedContainerRO<Holder<Biome>> biomes) {}

  private BiomeUpgradeRegression() {}

  public static void verify(ServerLevel level, Path baselineFile, boolean capture) {
    require(level.getServer().isSameThread(), "Run upgrade fixture on server thread");
    require(level.getServer().isDedicatedServer(), "Disposable dedicated server only");
    require(level.getServer().getPlayerList().getPlayerCount() == 0, "No players allowed");
    require(
        level.getSeed() == 1 && level.dimension().equals(Level.OVERWORLD), "Seed-1 Overworld only");
    require(
        baselineFile.isAbsolute() && baselineFile.normalize().toString().contains("/.runtime/"),
        "Baseline must be an absolute ignored .runtime path");
    JsonObject contract = savedContract(level);
    JsonObject report = new JsonObject();
    report.addProperty("format", 1);
    report.addProperty("mode", capture ? "capture" : "verify");
    report.add("saved_contract", contract);
    report.add("current_source", sourceSummary(level));
    try {
      if (capture) {
        require(!Files.exists(baselineFile), "Do not overwrite captured baseline");
        write(baselineFile, report);
      } else {
        JsonElement parsed = JsonParser.parseString(Files.readString(baselineFile));
        require(parsed.isJsonObject(), "Invalid baseline JSON object");
        JsonObject expected = parsed.getAsJsonObject();
        require(
            expected.has("format")
                && expected.get("format").isJsonPrimitive()
                && expected.getAsJsonPrimitive("format").isNumber()
                && expected.get("format").getAsInt() == 1
                && expected.has("saved_contract")
                && expected.get("saved_contract").isJsonObject(),
            "Invalid upgrade baseline contract");
        boolean matches = expected.get("saved_contract").equals(contract);
        report.addProperty("saved_contract_matches", matches);
        boolean upgraded =
            level.registryAccess().registryOrThrow(Registries.BIOME).containsKey(VOID);
        boolean reload = Boolean.getBoolean("aeroscapes.regression.biomeUpgradeReload");
        report.addProperty("verification", upgraded ? "upgraded_copy" : "old_pack_rollback_copy");
        if (matches && upgraded)
          report.add("new_generation", newGeneration(level, baselineFile, reload));
        Path output =
            baselineFile.resolveSibling(
                baselineFile.getFileName()
                    + (upgraded
                        ? reload ? ".upgrade-reload.json" : ".upgrade.json"
                        : ".rollback.json"));
        write(output, report);
        require(
            matches, "Saved FULL block/biome samples, spawn or seed changed; inspect " + output);
      }
    } catch (IOException failure) {
      throw new IllegalStateException(
          "Cannot read/write biome upgrade evidence " + baselineFile, failure);
    }
    LogUtils.getLogger()
        .info(
            "Biome upgrade regression passed: mode={}, savedCenters={}, baseline={}; no client acceptance",
            capture ? "capture" : "verify",
            CENTERS.size(),
            baselineFile);
  }

  private static JsonObject savedContract(ServerLevel level) {
    JsonObject result = new JsonObject();
    result.addProperty("seed", level.getSeed());
    result.add("spawn", position(level.getSharedSpawnPos()));
    result.addProperty("spawn_angle", level.getSharedSpawnAngle());
    result.addProperty("min_y", level.getMinBuildHeight());
    result.addProperty("max_y_exclusive", level.getMaxBuildHeight());
    result.addProperty(
        "sampling", "Every quart cell: saved block at local 4q+2 and saved noise biome");
    JsonArray chunks = new JsonArray();
    for (ChunkPos center : CENTERS) chunks.add(snapshot(level, center));
    result.add("chunks", chunks);
    return result;
  }

  private static JsonObject snapshot(ServerLevel level, ChunkPos center) {
    Map<Integer, SavedSection> sections = decodeSections(level, readFull(level, center));
    StringBuilder samples = new StringBuilder();
    JsonArray witnesses = new JsonArray();
    int count = 0;
    int nonAir = 0;
    for (int y = level.getMinBuildHeight() + 2; y < level.getMaxBuildHeight(); y += 4) {
      SavedSection section = sections.get(Math.floorDiv(y, 16));
      require(section != null, "Missing saved section at " + center + " y=" + y);
      for (int z = 2; z < 16; z += 4)
        for (int x = 2; x < 16; x += 4) {
          BlockState state = section.blocks().get(x, Math.floorMod(y, 16), z);
          String biome = biomeId(section.biomes().get(x / 4, Math.floorMod(y, 16) / 4, z / 4));
          String block = stateName(state);
          samples.append(x).append(',').append(y).append(',').append(z).append(':');
          samples.append(block).append('|').append(biome).append('\n');
          count++;
          if (!state.isAir()) {
            nonAir++;
            if (witnesses.size() < 8) {
              JsonObject witness =
                  position(new BlockPos(center.getMinBlockX() + x, y, center.getMinBlockZ() + z));
              witness.addProperty("block", block);
              witness.addProperty("stored_biome", biome);
              witnesses.add(witness);
            }
          }
        }
    }
    JsonObject result = new JsonObject();
    result.addProperty("chunk_x", center.x);
    result.addProperty("chunk_z", center.z);
    result.addProperty("sample_count", count);
    result.addProperty("non_air_samples", nonAir);
    result.addProperty(
        "samples_sha256",
        ResourceFingerprints.sha256(samples.toString().getBytes(StandardCharsets.UTF_8)));
    result.add("material_examples", witnesses);
    return result;
  }

  private static CompoundTag readFull(ServerLevel level, ChunkPos center) {
    CompoundTag data =
        level
            .getChunkSource()
            .chunkMap
            .read(center)
            .orTimeout(30, TimeUnit.SECONDS)
            .join()
            .orElseThrow(() -> new IllegalStateException("Missing saved FULL chunk " + center));
    require(
        data.getString("Status").equals("minecraft:full"), "Saved chunk is not FULL: " + center);
    require(
        data.getInt("xPos") == center.x && data.getInt("zPos") == center.z,
        "Saved coordinate mismatch");
    return data;
  }

  private static Map<Integer, SavedSection> decodeSections(ServerLevel level, CompoundTag data) {
    var blocks =
        PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY,
            BlockState.CODEC,
            PalettedContainer.Strategy.SECTION_STATES,
            Blocks.AIR.defaultBlockState());
    var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
    var biomes =
        PalettedContainer.codecRO(
            registry.asHolderIdMap(),
            registry.holderByNameCodec(),
            PalettedContainer.Strategy.SECTION_BIOMES,
            registry.getHolderOrThrow(Biomes.PLAINS));
    Map<Integer, SavedSection> result = new HashMap<>();
    for (Tag element : data.getList("sections", Tag.TAG_COMPOUND)) {
      CompoundTag section = (CompoundTag) element;
      int y = section.getByte("Y");
      if (y < level.getMinSection() || y >= level.getMaxSection()) continue;
      require(
          section.contains("block_states", Tag.TAG_COMPOUND)
              && section.contains("biomes", Tag.TAG_COMPOUND),
          "Missing saved palettes at section " + y);
      SavedSection decoded =
          new SavedSection(
              blocks.parse(NbtOps.INSTANCE, section.getCompound("block_states")).getOrThrow(),
              biomes.parse(NbtOps.INSTANCE, section.getCompound("biomes")).getOrThrow());
      require(result.put(y, decoded) == null, "Duplicate saved section " + y);
    }
    return result;
  }

  private static JsonObject sourceSummary(ServerLevel level) {
    var generator = level.getChunkSource().getGenerator();
    var source = generator.getBiomeSource();
    var operations = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    JsonObject summary = new JsonObject();
    summary.addProperty("generator_class", generator.getClass().getName());
    summary.addProperty("biome_source_class", source.getClass().getName());
    fingerprint(summary, "generator", ChunkGenerator.CODEC.encodeStart(operations, generator));
    fingerprint(summary, "biome_source", BiomeSource.CODEC.encodeStart(operations, source));
    JsonArray possible = new JsonArray();
    source.possibleBiomes().stream()
        .map(BiomeUpgradeRegression::biomeId)
        .sorted()
        .forEach(possible::add);
    summary.add("possible_biomes", possible);
    JsonArray selected = new JsonArray();
    for (ChunkPos center : CENTERS)
      for (int y : new int[] {0, 128, 304}) {
        BlockPos point = new BlockPos(center.getMiddleBlockX(), y, center.getMiddleBlockZ());
        JsonObject sample = position(point);
        sample.addProperty("source_biome", sourceBiome(level, point));
        selected.add(sample);
      }
    summary.add("selected_source_queries", selected);
    return summary;
  }

  private static void fingerprint(
      JsonObject output, String name, com.mojang.serialization.DataResult<JsonElement> encoded) {
    encoded
        .result()
        .ifPresent(value -> output.addProperty(name + "_sha256", ResourceFingerprints.json(value)));
    encoded
        .error()
        .ifPresent(error -> output.addProperty(name + "_serialization_error", error.message()));
  }

  private static JsonObject newGeneration(ServerLevel level, Path baselineFile, boolean reload)
      throws IOException {
    JsonObject expected = null;
    if (reload) {
      Path firstPhase = baselineFile.resolveSibling(baselineFile.getFileName() + ".upgrade.json");
      JsonElement parsed = JsonParser.parseString(Files.readString(firstPhase));
      require(parsed.isJsonObject(), "Invalid first-phase upgrade report");
      JsonObject prior = parsed.getAsJsonObject();
      require(
          prior.has("new_generation") && prior.get("new_generation").isJsonObject(),
          "Missing first-phase new-generation contract");
      expected = prior.getAsJsonObject("new_generation");
      require(
          expected.has("evidence")
              && expected.get("evidence").isJsonPrimitive()
              && expected.getAsJsonPrimitive("evidence").isString()
              && expected.get("evidence").getAsString().equals("FULL_in_memory"),
          "Reload requires the in-memory first-phase contract");
    }
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
    var generator = level.getChunkSource().getGenerator();
    var random = level.getChunkSource().randomState();
    for (int index = 0; index < 64; index++) {
      require(System.nanoTime() < deadline, "Far-island search timed out");
      int x = 50002 + (index % 8) * 128;
      int z = 50002 + (index / 8) * 128;
      var raw = generator.getBaseColumn(x, z, level, random);
      boolean land = false;
      for (int y = level.getMinBuildHeight() + 2; y < level.getMaxBuildHeight(); y += 4)
        land |= naturalSolid(raw.getBlock(y));
      if (!land) continue;
      ChunkPos center = new ChunkPos(new BlockPos(x, 0, z));
      if (reload) {
        require(expected != null, "Missing reload witness contract");
        return verifyNewGenerationReload(level, center, x, z, index, expected);
      }
      require(
          level
              .getChunkSource()
              .chunkMap
              .read(center)
              .orTimeout(30, TimeUnit.SECONDS)
              .join()
              .isEmpty(),
          "Far candidate already saved; use untouched baseline copy");
      var full = level.getChunk(center.x, center.z);
      require(
          full.getPersistedStatus() == net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
          "Far generation did not reach FULL");
      JsonObject result = new JsonObject();
      for (int y = level.getMinBuildHeight() + 2; y < level.getMaxBuildHeight(); y += 4) {
        BlockPos point = new BlockPos(x, y, z);
        BlockState state = full.getBlockState(point);
        String stored = biomeId(full.getNoiseBiome(x >> 2, y >> 2, z >> 2));
        String selected = sourceBiome(level, point);
        if (!result.has("natural_solid") && naturalSolid(raw.getBlock(y)) && naturalSolid(state)) {
          require(
              !stored.equals(VOID.toString()) && !selected.equals(VOID.toString()),
              "Void biome on new natural island material");
          result.add("natural_solid", newWitness(point, state, stored, selected));
        }
        if (!result.has("open_sky")
            && raw.getBlock(y).isAir()
            && state.isAir()
            && y > full.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15)
            && selected.equals(VOID.toString())) {
          require(stored.equals(VOID.toString()), "New FULL sky biome differs from source void");
          result.add("open_sky", newWitness(point, state, stored, selected));
        }
      }
      require(
          result.has("natural_solid") && result.has("open_sky"), "Missing far-island/sky witness");
      result.addProperty("raw_columns_searched", index + 1);
      result.addProperty("column_x", x);
      result.addProperty("column_z", z);
      result.addProperty("chunk_x", center.x);
      result.addProperty("chunk_z", center.z);
      result.addProperty("candidate_absent_before_generation", true);
      result.addProperty("evidence", "FULL_in_memory");
      result.addProperty("in_memory_status", "minecraft:full");
      result.addProperty(
          "pending", "save-all flush, clean stop and biomeUpgradeReload=true restart");
      LogUtils.getLogger()
          .info(
              "Biome upgrade new generation checked in memory: chunk={}; save-all flush, clean stop"
                  + " and biomeUpgradeReload=true restart remain required",
              center);
      return result;
    }
    throw new IllegalStateException("No natural island in 64 bounded far columns");
  }

  private static JsonObject verifyNewGenerationReload(
      ServerLevel level, ChunkPos center, int x, int z, int index, JsonObject expected) {
    require(
        integer(expected, "column_x") == x
            && integer(expected, "column_z") == z
            && integer(expected, "chunk_x") == center.x
            && integer(expected, "chunk_z") == center.z
            && integer(expected, "raw_columns_searched") == index + 1,
        "Reload selected a different deterministic far column");
    require(
        expected.has("candidate_absent_before_generation")
            && expected.get("candidate_absent_before_generation").isJsonPrimitive()
            && expected.getAsJsonPrimitive("candidate_absent_before_generation").isBoolean()
            && expected.get("candidate_absent_before_generation").getAsBoolean(),
        "First phase did not establish that the far chunk was absent");
    Map<Integer, SavedSection> saved = decodeSections(level, readFull(level, center));
    Map<Integer, String> witnesses = new HashMap<>();
    for (String name : List.of("natural_solid", "open_sky")) {
      require(
          expected.has(name) && expected.get(name).isJsonObject(),
          "Missing first-phase witness " + name);
      JsonObject witness = expected.getAsJsonObject(name);
      int y = integer(witness, "y");
      require(
          integer(witness, "x") == x
              && integer(witness, "z") == z
              && y >= level.getMinBuildHeight() + 2
              && y < level.getMaxBuildHeight()
              && Math.floorMod(y - level.getMinBuildHeight() - 2, 4) == 0,
          "Invalid first-phase witness position " + name);
      require(witnesses.put(y, name) == null, "First-phase witnesses overlap");
    }
    JsonObject result = new JsonObject();
    for (int y = level.getMinBuildHeight() + 2; y < level.getMaxBuildHeight(); y += 4) {
      BlockPos point = new BlockPos(x, y, z);
      String selected = sourceBiome(level, point);
      String name = witnesses.get(y);
      if (name == null) continue;
      SavedSection section = saved.get(Math.floorDiv(y, 16));
      require(section != null, "Missing reloaded FULL section at y=" + y);
      BlockState state = section.blocks().get(x & 15, y & 15, z & 15);
      String stored = biomeId(section.biomes().get((x & 15) / 4, (y & 15) / 4, (z & 15) / 4));
      JsonObject actual = newWitness(point, state, stored, selected);
      require(
          actual.equals(expected.getAsJsonObject(name)),
          "Saved new-generation witness changed after restart: " + name);
      result.add(name, actual);
    }
    require(
        result.has("natural_solid") && result.has("open_sky"),
        "Saved new-generation witnesses were not checked");
    result.addProperty("column_x", x);
    result.addProperty("column_z", z);
    result.addProperty("chunk_x", center.x);
    result.addProperty("chunk_z", center.z);
    result.addProperty("evidence", "saved_FULL_section_palettes_after_restart");
    result.addProperty("persisted_status", "minecraft:full");
    result.addProperty("first_phase_witnesses_match", true);
    LogUtils.getLogger().info("Biome upgrade reload persistence passed: chunk={}", center);
    return result;
  }

  private static int integer(JsonObject object, String name) {
    require(
        object.has(name)
            && object.get(name).isJsonPrimitive()
            && object.getAsJsonPrimitive(name).isNumber(),
        "Missing integer " + name);
    try {
      return object.get(name).getAsBigDecimal().intValueExact();
    } catch (ArithmeticException | NumberFormatException failure) {
      throw new IllegalStateException("Invalid integer " + name, failure);
    }
  }

  private static JsonObject newWitness(
      BlockPos point, BlockState state, String stored, String source) {
    JsonObject result = position(point);
    result.addProperty("block", stateName(state));
    result.addProperty("biome", stored);
    result.addProperty("source_biome", source);
    return result;
  }

  private static boolean naturalSolid(BlockState state) {
    return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE);
  }

  private static String sourceBiome(ServerLevel level, BlockPos point) {
    return biomeId(
        level
            .getChunkSource()
            .getGenerator()
            .getBiomeSource()
            .getNoiseBiome(
                point.getX() >> 2,
                point.getY() >> 2,
                point.getZ() >> 2,
                level.getChunkSource().randomState().sampler()));
  }

  private static String biomeId(Holder<Biome> biome) {
    return biome.unwrapKey().orElseThrow().location().toString();
  }

  private static String stateName(BlockState state) {
    StringBuilder result =
        new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    for (Property<?> property :
        state.getProperties().stream().sorted(Comparator.comparing(Property::getName)).toList())
      result
          .append(';')
          .append(property.getName())
          .append('=')
          .append(propertyValue(state, property));
    return result.toString();
  }

  private static <T extends Comparable<T>> String propertyValue(
      BlockState state, Property<T> property) {
    return property.getName(state.getValue(property));
  }

  private static JsonObject position(BlockPos point) {
    JsonObject result = new JsonObject();
    result.addProperty("x", point.getX());
    result.addProperty("y", point.getY());
    result.addProperty("z", point.getZ());
    return result;
  }

  private static void write(Path path, JsonObject report) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(
        path,
        new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n",
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
