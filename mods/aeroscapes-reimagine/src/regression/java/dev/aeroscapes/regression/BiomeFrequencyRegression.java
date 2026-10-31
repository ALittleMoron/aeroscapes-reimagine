package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Collects a fixed FULL sample using only APIs also available with the published 0.1.7 mod. */
public final class BiomeFrequencyRegression {
  private static final long DEADLINE_NANOS = 360_000_000_000L;
  private static final TicketType<ChunkPos> SAMPLE_TICKET =
      TicketType.create("aeroscapes_biome_frequency", Comparator.comparingLong(ChunkPos::toLong));

  private BiomeFrequencyRegression() {}

  private record Plan(List<ChunkPos> chunks, String sha256) {}

  public static void start(ServerLevel level, Path plan, Path output) {
    Path input = plan.toAbsolutePath().normalize();
    Path destination = output.toAbsolutePath().normalize();
    try {
      require(!input.equals(destination), "Frequency output must differ from its plan");
      require(
          level.getServer().isDedicatedServer() && level.dimension() == Level.OVERWORLD,
          "Frequency sampling needs a disposable dedicated Overworld");
      require(level.getServer().isSameThread(), "Start frequency sampling on the server thread");
      require(
          level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
          "Frequency sampling needs the supported normal noise generator");
      require(
          level.getMinBuildHeight() == -128 && level.getMaxBuildHeight() == 384,
          "Frequency sampling needs the supported -128..383 build range");
      Sample sample = new Sample(level, input, destination, readPlan(level, input));
      sample.write("running", null);
      NeoForge.EVENT_BUS.addListener(sample::tick);
      NeoForge.EVENT_BUS.addListener(sample::stopping);
      LogUtils.getLogger()
          .info(
              "Biome frequency sample started: seed={} chunks={} output={}",
              level.getSeed(),
              sample.plan.chunks().size(),
              destination);
    } catch (IOException | RuntimeException failure) {
      JsonObject error = new JsonObject();
      error.addProperty("format", 1);
      error.addProperty("seed", level.getSeed());
      error.addProperty("plan", input.toString());
      error.addProperty("status", "error");
      error.addProperty("failure", failure.toString());
      if (!input.equals(destination)) write(destination, error);
      throw new IllegalStateException("Cannot start biome frequency sampling", failure);
    }
  }

  private static Plan readPlan(ServerLevel level, Path input) throws IOException {
    byte[] bytes = Files.readAllBytes(input);
    JsonObject root =
        object(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)), "plan");
    require(number(root.get("format"), "format") == 1, "Unsupported frequency plan format");
    JsonObject sampling = object(root.get("sampling"), "sampling");
    require(number(sampling.get("quart_y_step"), "quart_y_step") == 4, "Expected quart Y step 4");
    require(
        number(sampling.get("quart_y_offset"), "quart_y_offset") == 2, "Expected quart Y offset 2");
    JsonArray offsets = array(sampling.get("quart_xz_offsets"), "quart_xz_offsets");
    require(offsets.size() == 4, "Expected four quart X/Z offsets");
    for (int index = 0; index < 4; index++)
      require(
          number(offsets.get(index), "quart offset") == 2 + index * 4, "Unexpected quart offset");
    Set<Long> seeds = new HashSet<>();
    List<ChunkPos> selected = null;
    for (JsonElement element : array(root.get("worlds"), "worlds")) {
      JsonObject world = object(element, "world");
      long seed = number(world.get("seed"), "seed");
      require(seeds.add(seed), "Duplicate frequency host seed " + seed);
      List<ChunkPos> chunks = new ArrayList<>();
      Set<ChunkPos> positions = new HashSet<>();
      for (JsonElement chunkElement : array(world.get("chunks"), "chunks")) {
        JsonObject chunk = object(chunkElement, "chunk");
        long x = number(chunk.get("x"), "chunk.x");
        long z = number(chunk.get("z"), "chunk.z");
        require(
            x >= -1_875_000 && x < 1_875_000 && z >= -1_875_000 && z < 1_875_000,
            "Frequency chunk is outside native world coordinate bounds");
        ChunkPos position = new ChunkPos((int) x, (int) z);
        require(positions.add(position), "Duplicate frequency chunk " + position);
        chunks.add(position);
      }
      require(chunks.size() == 16, "Expected sixteen frozen stratified chunks per world");
      if (seed == level.getSeed()) selected = List.copyOf(chunks);
    }
    require(seeds.size() == 10, "Expected ten frequency host seeds");
    require(selected != null, "Frequency plan has no actual host seed " + level.getSeed());
    return new Plan(selected, sha256(bytes));
  }

  private static final class Sample {
    private final ServerLevel level;
    private final NoiseBasedChunkGenerator generator;
    private final BiomeSource source;
    private final RandomState random;
    private final Path input;
    private final Path output;
    private final Plan plan;
    private final long started = System.nanoTime();
    private final Set<String> registered = new TreeSet<>();
    private final Set<String> possible = new TreeSet<>();
    private final TreeMap<String, TreeMap<String, Long>> histograms = new TreeMap<>();
    private final JsonArray completed = new JsonArray();
    private JsonArray columns = new JsonArray();
    private CompletableFuture<ChunkResult<ChunkAccess>> pending;
    private ChunkPos ticket;
    private ChunkAccess current;
    private int index;
    private int column;
    private long cells;
    private long rawColumns;
    private long mismatchCount;
    private long materialMismatchCount;
    private long nextProgress;
    private boolean finished;

    private Sample(ServerLevel level, Path input, Path output, Plan plan) {
      this.level = level;
      this.generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
      this.source = generator.getBiomeSource();
      this.random = level.getChunkSource().randomState();
      this.input = input;
      this.output = output;
      this.plan = plan;
      level
          .registryAccess()
          .registryOrThrow(Registries.BIOME)
          .keySet()
          .forEach(id -> registered.add(id.toString()));
      source.possibleBiomes().forEach(biome -> possible.add(id(biome)));
      require(registered.containsAll(possible), "Possible biomes are outside the actual registry");
    }

    private void tick(ServerTickEvent.Post event) {
      if (finished || event.getServer() != level.getServer()) return;
      try {
        require(
            System.nanoTime() - started < DEADLINE_NANOS,
            "Biome frequency sampling exceeded 360 seconds");
        require(
            level.getChunkSource().getGenerator() == generator
                && generator.getBiomeSource() == source
                && level.getChunkSource().randomState() == random,
            "Actual frequency generator, source or seeded state changed");
        advance();
        if (!finished && System.nanoTime() >= nextProgress) {
          nextProgress = System.nanoTime() + 15_000_000_000L;
          LogUtils.getLogger()
              .info(
                  "Biome frequency progress: seed={} chunks={}/{} rawColumns={} cells={} mismatches={}",
                  level.getSeed(),
                  index,
                  plan.chunks().size(),
                  rawColumns,
                  cells,
                  mismatchCount);
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
        boolean complete = rawColumns == 16L * plan.chunks().size() && cells == rawColumns * 128;
        write(complete ? "complete" : "invalid_counts", null);
        require(complete, "Frequency sample counts differ from the frozen plan");
        LogUtils.getLogger()
            .info(
                "Biome frequency regression passed: seed={} chunks={} cells={} output={}",
                level.getSeed(),
                index,
                cells,
                output);
        return;
      }
      if (current != null) {
        scanColumn();
        if (++column == 16) {
          JsonObject chunk = new JsonObject();
          chunk.addProperty("x", current.getPos().x);
          chunk.addProperty("z", current.getPos().z);
          chunk.addProperty("game_time", level.getGameTime());
          chunk.add("columns", columns);
          completed.add(chunk);
          columns = new JsonArray();
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
                .orElseThrow(() -> new IllegalStateException("Frequency FULL chunk unavailable"));
        pending = null;
        require(
            current.getPos().equals(ticket) && current.getPersistedStatus() == ChunkStatus.FULL,
            "Frequency sample did not receive the requested actual FULL chunk");
        column = 0;
        return;
      }
      ticket = plan.chunks().get(index);
      level.getChunkSource().addRegionTicket(SAMPLE_TICKET, ticket, 0, ticket);
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
      StringBuilder rawKinds = new StringBuilder(512);
      StringBuilder fullKinds = new StringBuilder(512);
      int highestRaw = Integer.MIN_VALUE;
      int highestSupported = Integer.MIN_VALUE;
      for (int y = -128; y < 384; y++) {
        char rawKind = kind(raw.getBlock(y));
        char fullKind = kind(current.getBlockState(new BlockPos(x, y, z)));
        rawKinds.append(rawKind);
        fullKinds.append(fullKind);
        if (natural(rawKind)) highestRaw = y;
        if (natural(rawKind) && natural(fullKind)) highestSupported = y;
      }
      JsonArray stored = new JsonArray();
      JsonArray selected = new JsonArray();
      StringBuilder rawQuart = new StringBuilder(128);
      StringBuilder fullQuart = new StringBuilder(128);
      for (int y = -126; y < 384; y += 4) {
        String actual = id(current.getNoiseBiome(x >> 2, y >> 2, z >> 2));
        String normal = id(source.getNoiseBiome(x >> 2, y >> 2, z >> 2, random.sampler()));
        require(
            registered.contains(actual) && registered.contains(normal),
            "Observed biome is outside the actual registry");
        stored.add(actual);
        selected.add(normal);
        char rawKind = rawKinds.charAt(y + 128);
        char fullKind = fullKinds.charAt(y + 128);
        rawQuart.append(rawKind);
        fullQuart.append(fullKind);
        bump("stored_all_quart_centers", actual);
        bump("source_all_quart_centers", normal);
        if (!actual.equals(normal)) {
          mismatchCount++;
          if (natural(rawKind) && natural(fullKind)) materialMismatchCount++;
        }
        if (rawKind == 'S' && fullKind == 'S') {
          bump("stored_raw_and_FULL_solid", actual);
          bump("source_raw_and_FULL_solid", normal);
        }
        if (rawKind == 'W' && fullKind == 'W') {
          bump("stored_raw_and_FULL_water", actual);
          bump("source_raw_and_FULL_water", normal);
        }
        cells++;
      }
      JsonObject result = new JsonObject();
      result.addProperty("x", x);
      result.addProperty("z", z);
      result.addProperty("raw_quart_kinds", rawQuart.toString());
      result.addProperty("FULL_quart_kinds", fullQuart.toString());
      result.addProperty("raw_block_kinds", rawKinds.toString());
      result.addProperty("FULL_block_kinds", fullKinds.toString());
      result.add("stored", stored);
      result.add("source", selected);
      result.add(
          "highest_raw_natural",
          surface(x, z, highestRaw, rawKinds, fullKinds, stored, selected, raw));
      JsonObject surface =
          surface(x, z, highestSupported, rawKinds, fullKinds, stored, selected, raw);
      result.add("highest_raw_supported_FULL_natural", surface);
      if (surface != null) {
        String category =
            surface.get("raw_kind").getAsString() + surface.get("FULL_kind").getAsString();
        bump("stored_surface_" + category, surface.get("stored_at_ground").getAsString());
        bump("source_surface_" + category, surface.get("source_at_ground").getAsString());
        bump("level_surface_above_" + category, surface.get("level_biome_above").getAsString());
      }
      columns.add(result);
    }

    private JsonObject surface(
        int x,
        int z,
        int y,
        StringBuilder rawKinds,
        StringBuilder fullKinds,
        JsonArray stored,
        JsonArray selected,
        net.minecraft.world.level.NoiseColumn raw) {
      if (y == Integer.MIN_VALUE) return null;
      JsonObject result = new JsonObject();
      result.addProperty("y", y);
      result.addProperty("raw_kind", String.valueOf(rawKinds.charAt(y + 128)));
      result.addProperty("FULL_kind", String.valueOf(fullKinds.charAt(y + 128)));
      result.addProperty("raw_block", blockId(raw.getBlock(y)));
      BlockPos position = new BlockPos(x, y, z);
      result.addProperty("FULL_block", blockId(current.getBlockState(position)));
      int quartIndex = (y + 128) / 4;
      result.addProperty("stored_at_ground", stored.get(quartIndex).getAsString());
      result.addProperty("source_at_ground", selected.get(quartIndex).getAsString());
      result.addProperty("level_biome_above", id(level.getBiome(position.above())));
      result.addProperty("sky_visible_above", level.canSeeSky(position.above()));
      result.addProperty("raw_fluid_above", !raw.getBlock(y + 1).getFluidState().isEmpty());
      result.addProperty(
          "FULL_fluid_above", !current.getBlockState(position.above()).getFluidState().isEmpty());
      return result;
    }

    private void bump(String metric, String biome) {
      histograms.computeIfAbsent(metric, ignored -> new TreeMap<>()).merge(biome, 1L, Long::sum);
    }

    private void release() {
      if (ticket != null) {
        level.getChunkSource().removeRegionTicket(SAMPLE_TICKET, ticket, 0, ticket);
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
      write("aborted", "Server stopped before frequency sampling completed");
    }

    private void write(String status, String failure) {
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("seed", level.getSeed());
      report.addProperty("plan", input.toString());
      report.addProperty("plan_sha256", plan.sha256());
      report.addProperty("status", status);
      if (failure != null) report.addProperty("failure", failure);
      report.addProperty("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
      report.addProperty("deadline_seconds", 360);
      report.addProperty("biome_source", source.getClass().getName());
      report.addProperty("generator", generator.getClass().getName());
      report.addProperty("min_y", -128);
      report.addProperty("max_y_exclusive", 384);
      report.addProperty("quart_y_start", -126);
      report.addProperty("quart_y_step", 4);
      report.addProperty("planned_chunk_count", plan.chunks().size());
      report.addProperty("raw_columns", rawColumns);
      report.addProperty("quart_centers_examined", cells);
      report.addProperty("normal_source_queries", cells);
      report.addProperty("source_stored_mismatch_count", mismatchCount);
      report.addProperty("material_source_stored_mismatch_count", materialMismatchCount);
      report.addProperty("ticket_active", ticket != null);
      report.add("registered_ids", strings(registered));
      report.add("possible_ids", strings(possible));
      report.add("completed_FULL_chunks", completed);
      if (columns.size() > 0) report.add("partial_chunk_columns", columns);
      JsonObject counts = new JsonObject();
      for (Map.Entry<String, TreeMap<String, Long>> metric : histograms.entrySet()) {
        JsonObject values = new JsonObject();
        metric.getValue().forEach(values::addProperty);
        counts.add(metric.getKey(), values);
      }
      report.add("histograms", counts);
      report.addProperty(
          "kind_legend",
          "A=air; W=Blocks.WATER; S=fluid-empty full upper collision face; O=other. Natural material=W or S.");
      report.addProperty(
          "surface_definition",
          "Highest raw natural cell, and separately highest raw+FULL natural cell, per sampled X/Z. SS is supported solid ground, WW supported water, SW/WS material transitions. Dry-land surface comparison also excludes fluid above on either artifact using the retained flags (including lava). Features above raw terrain (canopy, spikes, buildings) are excluded from supported height. Ground labels use the containing stored/source quart; level_biome_above uses native Level.getBiome at the block above that ground/water. Lower stacked bodies and cave surfaces are not the highest-surface metric.");
      report.addProperty(
          "sampling_policy",
          "Frozen chunks, quart columns X then Z, Y ascending; one cached native base column per tick. Actual RandomState.sampler is passed unchanged: no new main-mod API, carrier, source clone, synthetic state or terrain-mask deferral. normal_source_queries counts explicit quart calls only, excluding native generation and Level.getBiome internals. FULL generation can prime native caches; this does not claim identical cache histories across artifacts.");
      report.addProperty(
          "limits",
          "Finite stratified sample, not biome-target discovery. No rare-ID positive assertion. Zero counts do not prove global absence. FULL stored and normal source histograms are separate; disagreements remain in both complete arrays and FULL frequencies, without automatic native-tie classification. Offline before/after comparison must intersect identical XYZ with raw+FULL S on both artifacts for paired land, and W for paired water; air/void never enters the land denominator. Whole 512-block masks and all 128 quart labels preserve surface/volume pairing. Passing means the requested sample completed, not biome balance or visual acceptance. Native generation dependencies are outside the explicit planned FULL count.");
      BiomeFrequencyRegression.write(output, report);
    }
  }

  private static char kind(BlockState state) {
    if (state.isAir()) return 'A';
    if (state.is(Blocks.WATER)) return 'W';
    return state.getFluidState().isEmpty()
            && Block.isFaceFull(
                state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP)
        ? 'S'
        : 'O';
  }

  private static boolean natural(char kind) {
    return kind == 'S' || kind == 'W';
  }

  private static String id(Holder<Biome> biome) {
    return biome.unwrapKey().orElseThrow().location().toString();
  }

  private static String blockId(BlockState block) {
    return BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();
  }

  private static JsonArray strings(Set<String> values) {
    JsonArray result = new JsonArray();
    values.forEach(result::add);
    return result;
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

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 unavailable", failure);
    }
  }

  private static void write(Path output, JsonObject report) {
    try {
      Files.createDirectories(output.getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write biome frequency report " + output, failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
