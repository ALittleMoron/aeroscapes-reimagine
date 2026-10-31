package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Rechecks only planned FULL chunks on their actual host seed, without source discovery. */
public final class BiomeReplayRegression {
  private static final String VOID = "aeroscapes_reimagine:void";
  private static final long TIMEOUT_NANOS = 600_000_000_000L;
  private static final TicketType<ChunkPos> REPLAY_TICKET =
      TicketType.create("aeroscapes_biome_replay", Comparator.comparingLong(ChunkPos::toLong));

  private BiomeReplayRegression() {}

  private record PlannedChunk(ChunkPos position, Set<String> hints) {}

  private record Plan(List<PlannedChunk> chunks, Set<String> expected) {}

  public static void start(ServerLevel level, Path plan, Path output) {
    Path input = plan.toAbsolutePath().normalize();
    Path destination = output.toAbsolutePath().normalize();
    try {
      require(!input.equals(destination), "Replay output must differ from its plan");
      require(
          level.getServer().isDedicatedServer() && level.dimension() == Level.OVERWORLD,
          "Replay needs a disposable dedicated Overworld");
      require(level.getServer().isSameThread(), "Start replay on the server thread");
      require(
          level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
          "Replay needs the supported normal noise generator");
      Replay replay = new Replay(level, input, destination, readPlan(level, input));
      replay.checkSampler();
      replay.write("running", null);
      NeoForge.EVENT_BUS.addListener(replay::tick);
      NeoForge.EVENT_BUS.addListener(replay::stopping);
      LogUtils.getLogger()
          .info(
              "Biome FULL replay started: seed={} chunks={} expected={} output={}",
              level.getSeed(),
              replay.plan.chunks().size(),
              replay.plan.expected().size(),
              destination);
    } catch (IOException | RuntimeException failure) {
      JsonObject error = new JsonObject();
      error.addProperty("format", 1);
      error.addProperty("seed", level.getSeed());
      error.addProperty("plan", input.toString());
      error.addProperty("status", "error");
      error.addProperty("failure", failure.toString());
      if (!input.equals(destination)) write(destination, error);
      throw new IllegalStateException("Cannot start biome FULL replay", failure);
    }
  }

  private static Plan readPlan(ServerLevel level, Path input) throws IOException {
    JsonObject root = object(JsonParser.parseString(Files.readString(input)), "plan");
    require(number(root.get("format"), "format") == 1, "Unsupported replay plan format");
    Set<String> possible =
        level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
            .map(biome -> biome.unwrapKey().orElseThrow().location().toString())
            .collect(Collectors.toSet());
    Set<Long> seeds = new HashSet<>();
    Plan selected = null;
    for (JsonElement element : array(root.get("worlds"), "worlds")) {
      JsonObject world = object(element, "world");
      long seed = number(world.get("seed"), "seed");
      require(seeds.add(seed), "Duplicate replay host seed " + seed);
      Set<String> expected = ids(level, world.get("expected_biomes"), possible);
      require(!expected.isEmpty(), "Replay world has no expected biome IDs");
      List<PlannedChunk> chunks = new ArrayList<>();
      Set<ChunkPos> positions = new HashSet<>();
      for (JsonElement chunkElement : array(world.get("chunks"), "chunks")) {
        JsonObject chunk = object(chunkElement, "chunk");
        long x = number(chunk.get("x"), "chunk.x");
        long z = number(chunk.get("z"), "chunk.z");
        require(
            x >= -1_875_000 && x < 1_875_000 && z >= -1_875_000 && z < 1_875_000,
            "Replay chunk is outside native world coordinate bounds");
        ChunkPos position = new ChunkPos((int) x, (int) z);
        require(positions.add(position), "Duplicate replay chunk " + position);
        Set<String> hints = ids(level, chunk.get("expected_biomes"), possible);
        require(expected.containsAll(hints), "Chunk hints are outside world.expected_biomes");
        chunks.add(new PlannedChunk(position, hints));
      }
      require(!chunks.isEmpty(), "Replay world has no planned chunks");
      if (seed == level.getSeed()) selected = new Plan(List.copyOf(chunks), expected);
    }
    require(selected != null, "Replay plan has no actual host seed " + level.getSeed());
    return selected;
  }

  private static Set<String> ids(ServerLevel level, JsonElement element, Set<String> possible) {
    Set<String> result = new TreeSet<>();
    var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
    for (JsonElement value : array(element, "expected_biomes")) {
      require(
          value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
          "Expected biome ID must be a string");
      String id = value.getAsString();
      ResourceLocation parsed = ResourceLocation.tryParse(id);
      require(parsed != null && id.equals(parsed.toString()), "Noncanonical biome ID " + id);
      require(
          registry.containsKey(parsed) && possible.contains(id),
          "Unregistered or impossible replay biome " + id);
      require(result.add(id), "Duplicate replay biome ID " + id);
    }
    return Set.copyOf(result);
  }

  private static JsonObject object(JsonElement element, String name) {
    require(element != null && element.isJsonObject(), "Expected object " + name);
    return element.getAsJsonObject();
  }

  private static JsonArray array(JsonElement element, String name) {
    require(element != null && element.isJsonArray(), "Expected array " + name);
    return element.getAsJsonArray();
  }

  private static long number(JsonElement element, String name) {
    require(
        element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber(),
        "Expected integer " + name);
    try {
      return element.getAsBigDecimal().longValueExact();
    } catch (ArithmeticException | NumberFormatException failure) {
      throw new IllegalArgumentException("Expected signed 64-bit integer " + name, failure);
    }
  }

  private static final class Replay {
    private final ServerLevel level;
    private final NoiseBasedChunkGenerator generator;
    private final BiomeSource source;
    private final RandomState random;
    private final Path input;
    private final Path output;
    private final Plan plan;
    private final long started = System.nanoTime();
    private final TreeMap<String, JsonObject> witnesses = new TreeMap<>();
    private final JsonArray completed = new JsonArray();
    private final JsonArray mismatches = new JsonArray();
    private final Set<String> chunkFound = new TreeSet<>();
    private CompletableFuture<ChunkResult<ChunkAccess>> pending;
    private ChunkPos ticket;
    private ChunkAccess current;
    private int index;
    private int column;
    private long cells;
    private long rawColumns;
    private long sourceQueries;
    private long mismatchCount;
    private long nativeTieCount;
    private long unclassifiedMismatchCount;
    private long nextProgress;
    private boolean finished;

    private Replay(ServerLevel level, Path input, Path output, Plan plan) {
      this.level = level;
      this.generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
      this.source = generator.getBiomeSource();
      this.random = level.getChunkSource().randomState();
      this.input = input;
      this.output = output;
      this.plan = plan;
    }

    private void checkSampler() {
      IslandBiomeSampler context = (IslandBiomeSampler) (Object) random.sampler();
      require(
          !context.aeroscapesReimagine$deferTerrainMask()
              && context.aeroscapesReimagine$terrainMask() != null,
          "Replay needs the normal caller sampler with its terrain mask");
      require(
          level.getChunkSource().getGenerator() == generator
              && generator.getBiomeSource() == source,
          "Actual replay generator/source changed");
    }

    private void tick(ServerTickEvent.Post event) {
      if (finished || event.getServer() != level.getServer()) return;
      try {
        require(
            System.nanoTime() - started < TIMEOUT_NANOS, "Biome FULL replay exceeded 600 seconds");
        checkSampler();
        advance();
        checkSampler();
        if (!finished && System.nanoTime() >= nextProgress) {
          nextProgress = System.nanoTime() + 15_000_000_000L;
          LogUtils.getLogger()
              .info(
                  "Biome FULL replay progress: seed={} chunks={}/{} confirmed={}/{}",
                  level.getSeed(),
                  index,
                  plan.chunks().size(),
                  witnesses.size(),
                  plan.expected().size());
        }
      } catch (RuntimeException failure) {
        if (!finished) {
          finished = true;
          release();
          write("error", failure.toString());
        }
        throw failure;
      }
    }

    private void advance() {
      if (index == plan.chunks().size()) {
        finished = true;
        release();
        Set<String> missing = new TreeSet<>(plan.expected());
        missing.removeAll(witnesses.keySet());
        write(missing.isEmpty() ? "complete" : "missing_expected_biomes", null);
        require(missing.isEmpty(), "Missing expected replay biomes: " + missing);
        LogUtils.getLogger()
            .info(
                "Biome FULL replay regression passed: seed={} chunks={} biomes={} output={}",
                level.getSeed(),
                index,
                witnesses.size(),
                output);
        return;
      }
      if (current != null) {
        scanColumn();
        if (++column == 16) {
          PlannedChunk planned = plan.chunks().get(index);
          JsonObject result = new JsonObject();
          result.addProperty("x", current.getPos().x);
          result.addProperty("z", current.getPos().z);
          result.add("planned_hints", strings(planned.hints()));
          result.add("observed_expected_biomes", strings(chunkFound));
          Set<String> missingHints = new TreeSet<>(planned.hints());
          missingHints.removeAll(chunkFound);
          result.add("missing_planned_hints", strings(missingHints));
          completed.add(result);
          index++;
          release();
          write("running", null);
        }
        return;
      }
      if (pending != null) {
        if (!pending.isDone()) return;
        current =
            pending
                .join()
                .orElseThrow(() -> new IllegalStateException("Replay FULL chunk unavailable"));
        pending = null;
        require(
            current.getPos().equals(ticket) && current.getPersistedStatus() == ChunkStatus.FULL,
            "Replay did not receive the requested actual FULL chunk");
        column = 0;
        chunkFound.clear();
        return;
      }
      ticket = plan.chunks().get(index).position();
      level.getChunkSource().addRegionTicket(REPLAY_TICKET, ticket, 0, ticket);
      ChunkPos requested = ticket;
      pending =
          CompletableFuture.supplyAsync(
                  () ->
                      level
                          .getChunkSource()
                          .getChunkFuture(requested.x, requested.z, ChunkStatus.FULL, true),
                  Util.backgroundExecutor())
              .thenCompose(future -> future);
    }

    private void scanColumn() {
      int x = current.getPos().getMinBlockX() + 2 + (column % 4) * 4;
      int z = current.getPos().getMinBlockZ() + 2 + (column / 4) * 4;
      var raw = generator.getBaseColumn(x, z, level, random);
      rawColumns++;
      for (int y = level.getMinBuildHeight() + 2; y < level.getMaxBuildHeight(); y += 4) {
        cells++;
        String actual =
            current
                .getNoiseBiome(x >> 2, y >> 2, z >> 2)
                .unwrapKey()
                .orElseThrow()
                .location()
                .toString();
        if (!plan.expected().contains(actual)) continue;
        BlockPos position = new BlockPos(x, y, z);
        BlockState block = current.getBlockState(position);
        boolean empty = actual.equals(VOID) && block.isAir() && raw.getBlock(y).isAir();
        if (!empty
            && (actual.equals(VOID)
                || !BiomeCoverageRegression.naturalMaterial(block)
                || !BiomeCoverageRegression.naturalMaterial(raw.getBlock(y)))) continue;
        sourceQueries++;
        String selected =
            source
                .getNoiseBiome(x >> 2, y >> 2, z >> 2, random.sampler())
                .unwrapKey()
                .orElseThrow()
                .location()
                .toString();
        if (!actual.equals(selected)) {
          mismatchCount++;
          JsonObject nativeTie =
              BiomeMismatchRegression.nativeTie(level, source, position, actual, selected);
          if (nativeTie == null) unclassifiedMismatchCount++;
          else nativeTieCount++;
          if (mismatches.size() < 16) {
            JsonObject mismatch = position(position);
            mismatch.addProperty("stored", actual);
            mismatch.addProperty("source", selected);
            mismatch.add("native_tie", nativeTie);
            mismatches.add(mismatch);
          }
          continue;
        }
        chunkFound.add(actual);
        if (witnesses.containsKey(actual)) continue;
        JsonObject witness = position(position);
        witness.addProperty("biome", actual);
        witness.addProperty("source_biome", selected);
        witness.addProperty("block", blockId(block));
        witness.addProperty("raw_block", blockId(raw.getBlock(y)));
        witness.addProperty("environment", empty ? "empty_space" : "natural_solid_or_water");
        witnesses.put(actual, witness);
      }
    }

    private void release() {
      if (ticket != null) {
        level.getChunkSource().removeRegionTicket(REPLAY_TICKET, ticket, 0, ticket);
        ticket = null;
      }
      if (pending != null) {
        pending.cancel(false);
        pending = null;
      }
      current = null;
    }

    private void stopping(ServerStoppingEvent event) {
      if (finished || event.getServer() != level.getServer()) return;
      finished = true;
      release();
      write("aborted", "Server stopped before replay completion");
    }

    private void write(String status, String failure) {
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("seed", level.getSeed());
      report.addProperty("plan", input.toString());
      report.addProperty("status", status);
      if (failure != null) report.addProperty("failure", failure);
      report.addProperty("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
      report.addProperty("deadline_seconds", 600);
      report.addProperty("biome_source", source.getClass().getName());
      report.addProperty("generator", generator.getClass().getName());
      report.addProperty(
          "generator_settings",
          generator
              .generatorSettings()
              .unwrapKey()
              .map(key -> key.location().toString())
              .orElse("inline"));
      report.addProperty("min_y", level.getMinBuildHeight());
      report.addProperty("max_y_exclusive", level.getMaxBuildHeight());
      report.addProperty("planned_chunk_count", plan.chunks().size());
      report.add("expected_biomes", strings(plan.expected()));
      Set<String> missing = new TreeSet<>(plan.expected());
      missing.removeAll(witnesses.keySet());
      report.add("missing_expected_biomes", strings(missing));
      JsonArray actualWitnesses = new JsonArray();
      witnesses.values().forEach(actualWitnesses::add);
      report.add("witnesses", actualWitnesses);
      report.add("completed_FULL_chunks", completed);
      report.addProperty("raw_columns", rawColumns);
      report.addProperty("quart_centers_examined", cells);
      report.addProperty("normal_source_queries", sourceQueries);
      report.addProperty("skipped_source_label_mismatch_count", mismatchCount);
      report.addProperty("native_equal_fitness_mismatch_count", nativeTieCount);
      report.addProperty("unclassified_source_label_mismatch_count", unclassifiedMismatchCount);
      report.add("skipped_source_label_mismatch_examples", mismatches);
      IslandBiomeSampler sampler = (IslandBiomeSampler) (Object) random.sampler();
      report.addProperty("caller_sampler_defer", sampler.aeroscapesReimagine$deferTerrainMask());
      report.addProperty(
          "caller_sampler_mask_present", sampler.aeroscapesReimagine$terrainMask() != null);
      report.addProperty("ticket_active", ticket != null);
      report.addProperty(
          "limits",
          "Only planned host-seed FULL chunks are scanned, one raw column per tick."
              + " Native column generation may exceed one tick. World expected IDs are aggregate assertions;"
              + " chunk hints are diagnostic only. Witness coordinates are newly observed, never copied"
              + " from the plan. Source/stored mismatches do not count, including native ties."
              + " This is a finite recheck, not a source search, frequency estimate or visual acceptance.");
      BiomeReplayRegression.write(output, report);
    }
  }

  private static JsonArray strings(Set<String> values) {
    JsonArray result = new JsonArray();
    new TreeSet<>(values).forEach(result::add);
    return result;
  }

  private static JsonObject position(BlockPos position) {
    JsonObject result = new JsonObject();
    result.addProperty("x", position.getX());
    result.addProperty("y", position.getY());
    result.addProperty("z", position.getZ());
    return result;
  }

  private static String blockId(BlockState block) {
    return BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();
  }

  private static void write(Path output, JsonObject report) {
    try {
      Files.createDirectories(output.getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write biome FULL replay " + output, failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
