package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.EndIslandFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Finite native Nether/End source, raw terrain and actual FULL biome observations. */
public final class DimensionBiomeCoverageRegression {
  private static final String SMALL_ISLANDS = "minecraft:small_end_islands";
  private static final String END_ISLAND_FEATURE = "minecraft:end_island_decorated";
  private static final long SLICE_NANOS = 10_000_000L;
  private static final TicketType<UUID> TICKET =
      TicketType.create("aeroscapes_dimension_biomes", UUID::compareTo);

  private DimensionBiomeCoverageRegression() {}

  public static void start(ServerLevel overworld, Path output) {
    require(
        overworld.getServer().isDedicatedServer()
            && overworld.getServer().isSameThread()
            && overworld.dimension().equals(Level.OVERWORLD),
        "Dimension biome coverage requires the dedicated Overworld server thread");
    require(
        output.isAbsolute() && output.normalize().toString().contains("/.runtime/"),
        "Dimension biome report must be absolute in ignored .runtime");
    Audit audit = new Audit(overworld, output.toAbsolutePath().normalize());
    audit.write("running", "started", null);
    NeoForge.EVENT_BUS.addListener(audit.tickListener);
    NeoForge.EVENT_BUS.addListener(audit.stopListener);
    LogUtils.getLogger()
        .info("Dimension biome coverage started: seed={} output={}", overworld.getSeed(), output);
  }

  private static final class Request {
    private final ServerLevel level;
    private final ChunkPos position;
    private final UUID key = UUID.randomUUID();
    private final long started = System.nanoTime();
    private CompletableFuture<ChunkResult<ChunkAccess>> future;

    private Request(ServerLevel level, ChunkPos position) {
      this.level = level;
      this.position = position;
      level
          .getChunkSource()
          .chunkMap
          .getDistanceManager()
          .addTicket(TICKET, position, ChunkLevel.byStatus(ChunkStatus.FULL), key);
    }

    private ChunkAccess poll(int timeoutSeconds) {
      var map = level.getChunkSource().chunkMap;
      if (future == null) {
        var holder = map.getVisibleChunkIfPresent(position.toLong());
        if (holder != null && holder.getTicketLevel() <= ChunkLevel.byStatus(ChunkStatus.FULL))
          future = holder.scheduleChunkGenerationTask(ChunkStatus.FULL, map);
      }
      if (future == null || !future.isDone()) {
        require(
            System.nanoTime() - started < timeoutSeconds * 1_000_000_000L,
            "Dimension biome FULL request timed out: " + level.dimension() + " " + position);
        return null;
      }
      var result = future.join();
      var chunk = result.orElse(null);
      require(
          result.isSuccess()
              && chunk != null
              && chunk.getPos().equals(position)
              && chunk.getPersistedStatus() == ChunkStatus.FULL,
          "Dimension biome request did not return the actual requested FULL chunk " + position);
      return chunk;
    }

    private void close() {
      level
          .getChunkSource()
          .chunkMap
          .getDistanceManager()
          .removeTicket(TICKET, position, ChunkLevel.byStatus(ChunkStatus.FULL), key);
    }
  }

  private static final class DimensionAudit {
    private final ServerLevel level;
    private final NoiseBasedChunkGenerator generator;
    private final BiomeSource source;
    private final RandomState random;
    private final RandomSource candidates;
    private final Set<String> possible = new TreeSet<>();
    private final Set<Long> requested = new HashSet<>();
    private final Set<String> sourceObserved = new TreeSet<>();
    private final TreeMap<String, JsonObject> material = new TreeMap<>();
    private final JsonArray completed = new JsonArray();
    private final JsonArray mismatches = new JsonArray();
    private final boolean end;
    private final boolean nativeSmallIslandWiring;
    private JsonObject featureLand;
    private int candidateColumns;
    private int rawColumns;
    private int fullRequests;
    private long sourceQueries;
    private long fullCells;
    private long mismatchCount;
    private long structureExcludedSmallIslandBlocks;
    private String termination = "not_started";

    private DimensionAudit(ServerLevel level) {
      this.level = level;
      require(
          level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
          "Dimension biome audit needs the actual native noise generator " + level.dimension());
      generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
      source = generator.getBiomeSource();
      random = level.getChunkSource().randomState();
      end = level.dimension().equals(Level.END);
      candidates =
          RandomSource.create(level.getSeed() ^ (end ? 0x4e6f726d616c456eL : 0x4e657468657235L));
      source
          .possibleBiomes()
          .forEach(holder -> possible.add(holder.unwrapKey().orElseThrow().location().toString()));
      require(!possible.isEmpty(), "Dimension source has no possible biome IDs");
      PlacedFeature island =
          level
              .registryAccess()
              .registryOrThrow(Registries.PLACED_FEATURE)
              .get(ResourceLocation.parse(END_ISLAND_FEATURE));
      var small =
          level
              .registryAccess()
              .registryOrThrow(Registries.BIOME)
              .get(ResourceLocation.parse(SMALL_ISLANDS));
      nativeSmallIslandWiring =
          end
              && island != null
              && island.feature().value().feature() instanceof EndIslandFeature
              && small != null
              && small.getGenerationSettings().hasFeature(island);
    }

    private String selected(int x, int y, int z) {
      sourceQueries++;
      String id =
          source
              .getNoiseBiome(x >> 2, y >> 2, z >> 2, random.sampler())
              .unwrapKey()
              .orElseThrow()
              .location()
              .toString();
      require(
          possible.contains(id), "Actual dimension selected an ID outside possibleBiomes: " + id);
      sourceObserved.add(id);
      return id;
    }

    private boolean physicallyCovered(String id) {
      return material.containsKey(id) || id.equals(SMALL_ISLANDS) && featureLand != null;
    }

    private Set<String> missingPhysical() {
      Set<String> missing = new TreeSet<>(possible);
      missing.removeIf(this::physicallyCovered);
      return missing;
    }

    private BlockPos nextColumn() {
      int index = candidateColumns++;
      if (index == 0) return new BlockPos(2, 64, 2);
      if (!end)
        return new BlockPos(
            candidates.nextInt(4096) * 4 - 8190, 64, candidates.nextInt(4096) * 4 - 8190);
      int[] radii = {1280, 1536, 2048, 3072, 4096, 6144, 8192, 12288, 16384};
      int ring = (index - 1) / 64;
      if (ring < radii.length) {
        double angle = ((index - 1) % 64) * Math.PI / 32.0;
        int x = (int) Math.round(Math.cos(angle) * radii[ring]);
        int z = (int) Math.round(Math.sin(angle) * radii[ring]);
        return new BlockPos(Math.floorDiv(x, 4) * 4 + 2, 64, Math.floorDiv(z, 4) * 4 + 2);
      }
      return new BlockPos(
          candidates.nextInt(8192) * 4 - 16382, 64, candidates.nextInt(8192) * 4 - 16382);
    }

    private JsonObject report() {
      JsonObject report = new JsonObject();
      report.addProperty("dimension", level.dimension().location().toString());
      report.addProperty("seed", level.getSeed());
      report.addProperty("generator", generator.getClass().getName());
      report.addProperty("biome_source", source.getClass().getName());
      report.addProperty("min_y", level.getMinBuildHeight());
      report.addProperty("max_y_exclusive", level.getMaxBuildHeight());
      report.addProperty("termination", termination);
      report.add("possible_biomes", strings(possible));
      report.add("source_observed_biomes", strings(sourceObserved));
      report.add("missing_physical_biomes", strings(missingPhysical()));
      Set<String> missingRaw = new TreeSet<>(possible);
      missingRaw.removeAll(material.keySet());
      report.add("missing_raw_FULL_material_biomes", strings(missingRaw));
      JsonArray witnesses = new JsonArray();
      material.values().forEach(witnesses::add);
      report.add("raw_FULL_material_witnesses", witnesses);
      report.addProperty("native_small_end_island_feature_wired", nativeSmallIslandWiring);
      if (featureLand != null) report.add("small_end_island_feature_land_witness", featureLand);
      report.addProperty("candidate_columns", candidateColumns);
      report.addProperty("raw_columns", rawColumns);
      report.addProperty("source_queries", sourceQueries);
      report.addProperty("FULL_requests", fullRequests);
      report.addProperty("FULL_block_cells_examined", fullCells);
      report.add("completed_FULL_chunks", completed);
      report.addProperty("source_stored_mismatch_count", mismatchCount);
      report.add("source_stored_mismatch_examples", mismatches);
      report.addProperty(
          "structure_excluded_small_island_blocks", structureExcludedSmallIslandBlocks);
      return report;
    }
  }

  private static final class Audit {
    private final ServerLevel overworld;
    private final Path output;
    private final List<DimensionAudit> dimensions = new ArrayList<>();
    private final Consumer<ServerTickEvent.Post> tickListener = this::tick;
    private final Consumer<ServerStoppingEvent> stopListener = this::stopping;
    private final long started = System.nanoTime();
    private final int candidateBudget = integer("CandidateBudget", 8192, 100000);
    private final int rawBudget = integer("RawBudget", 8192, 100000);
    private final int fullBudget = integer("FullBudget", 128, 2048);
    private final int deadline = integer("DeadlineSeconds", 1200, 7200);
    private final int requestTimeout = integer("RequestTimeoutSeconds", 120, 600);
    private Request request;
    private ChunkAccess current;
    private int dimensionIndex;
    private int column;
    private int maximumTickets;
    private long nextProgress;
    private long maximumStep;
    private boolean done;

    private Audit(ServerLevel overworld, Path output) {
      this.overworld = overworld;
      this.output = output;
      for (var key : List.of(Level.NETHER, Level.END)) {
        ServerLevel level = overworld.getServer().getLevel(key);
        require(level != null, "Dimension biome audit requires loaded dimension " + key);
        require(level.getSeed() == overworld.getSeed(), "Dimension seed differs from actual host");
        dimensions.add(new DimensionAudit(level));
      }
    }

    private void tick(ServerTickEvent.Post event) {
      if (done || event.getServer() != overworld.getServer()) return;
      try {
        if (System.nanoTime() - started >= deadline * 1_000_000_000L) {
          finish("deadline_exhausted", null);
          return;
        }
        long slice = System.nanoTime();
        do {
          long step = System.nanoTime();
          boolean advanced = advance();
          maximumStep = Math.max(maximumStep, System.nanoTime() - step);
          if (!advanced || done) break;
        } while (System.nanoTime() - slice < SLICE_NANOS);
        if (!done && dimensionIndex < dimensions.size() && System.nanoTime() >= nextProgress) {
          nextProgress = System.nanoTime() + 15_000_000_000L;
          DimensionAudit dimension = dimensions.get(dimensionIndex);
          LogUtils.getLogger()
              .info(
                  "Dimension biome coverage progress: dimension={} physical={}/{} columns={} FULL={}",
                  dimension.level.dimension().location(),
                  dimension.possible.size() - dimension.missingPhysical().size(),
                  dimension.possible.size(),
                  dimension.candidateColumns,
                  dimension.fullRequests);
        }
      } catch (RuntimeException failure) {
        try {
          finish("exception", failure);
        } catch (RuntimeException cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
        throw failure;
      }
    }

    private boolean advance() {
      if (dimensionIndex == dimensions.size()) {
        finish("finite_dimension_searches_completed", null);
        return true;
      }
      DimensionAudit dimension = dimensions.get(dimensionIndex);
      require(
          dimension.level.getChunkSource().getGenerator() == dimension.generator
              && dimension.generator.getBiomeSource() == dimension.source
              && dimension.level.getChunkSource().randomState() == dimension.random,
          "Actual dimension generator/source/random state changed");
      if (current != null) {
        scanColumn(dimension);
        if (++column == 256 || dimension.missingPhysical().isEmpty()) {
          JsonObject chunk = new JsonObject();
          chunk.addProperty("x", current.getPos().x);
          chunk.addProperty("z", current.getPos().z);
          chunk.addProperty("scanned_columns", column);
          dimension.completed.add(chunk);
          release();
          write("running", "FULL_chunk_completed", null);
        }
        return true;
      }
      if (request != null) {
        current = request.poll(requestTimeout);
        if (current == null) return false;
        column = 0;
        return true;
      }
      if (dimension.missingPhysical().isEmpty()
          || dimension.candidateColumns >= candidateBudget
          || dimension.rawColumns >= rawBudget
          || dimension.fullRequests >= fullBudget) {
        dimension.termination =
            dimension.missingPhysical().isEmpty()
                ? "all_physical_routes_positive"
                : "finite_budget_exhausted";
        dimensionIndex++;
        return true;
      }
      selectCandidate(dimension);
      return true;
    }

    private void selectCandidate(DimensionAudit dimension) {
      BlockPos position = dimension.nextColumn();
      ChunkPos chunk = new ChunkPos(position);
      if (dimension.requested.contains(chunk.toLong())) return;
      int x = position.getX();
      int z = position.getZ();
      boolean missingSource = false;
      for (int y : dimension.end ? new int[] {64} : new int[] {16, 32, 48, 64, 80, 96, 112})
        if (!dimension.physicallyCovered(dimension.selected(x, y, z))) missingSource = true;
      if (!missingSource) return;
      var raw = dimension.generator.getBaseColumn(x, z, dimension.level, dimension.random);
      dimension.rawColumns++;
      boolean wanted = false;
      for (int y = dimension.level.getMinBuildHeight() + 2;
          y < dimension.level.getMaxBuildHeight();
          y += 4) {
        if (BiomeCoverageRegression.naturalMaterial(raw.getBlock(y))
            && !dimension.physicallyCovered(dimension.selected(x, y, z))) {
          wanted = true;
          break;
        }
      }
      if (!wanted
          && dimension.end
          && dimension.featureLand == null
          && dimension.nativeSmallIslandWiring
          && dimension.selected(x, 64, z).equals(SMALL_ISLANDS)) wanted = true;
      if (!wanted) return;
      dimension.requested.add(chunk.toLong());
      dimension.fullRequests++;
      dimension.termination = "searching";
      request = new Request(dimension.level, chunk);
      maximumTickets = 1;
    }

    private void scanColumn(DimensionAudit dimension) {
      int x = current.getPos().getMinBlockX() + column % 16;
      int z = current.getPos().getMinBlockZ() + column / 16;
      NoiseColumn raw = null;
      for (int y = dimension.level.getMinBuildHeight();
          y < dimension.level.getMaxBuildHeight();
          y++) {
        dimension.fullCells++;
        BlockPos position = new BlockPos(x, y, z);
        BlockState block = current.getBlockState(position);
        if (!BiomeCoverageRegression.naturalMaterial(block)) continue;
        String stored =
            current
                .getNoiseBiome(x >> 2, y >> 2, z >> 2)
                .unwrapKey()
                .orElseThrow()
                .location()
                .toString();
        if (dimension.physicallyCovered(stored)) continue;
        if (raw == null) {
          if (dimension.rawColumns >= rawBudget) return;
          raw = dimension.generator.getBaseColumn(x, z, dimension.level, dimension.random);
          dimension.rawColumns++;
        }
        boolean rawMaterial = BiomeCoverageRegression.naturalMaterial(raw.getBlock(y));
        boolean featureMaterial =
            dimension.end
                && stored.equals(SMALL_ISLANDS)
                && dimension.nativeSmallIslandWiring
                && block.is(Blocks.END_STONE)
                && raw.getBlock(y).isAir();
        if (!rawMaterial && !featureMaterial) continue;
        String selected = dimension.selected(x, y, z);
        if (!stored.equals(selected)) {
          dimension.mismatchCount++;
          if (dimension.mismatches.size() < 32) {
            JsonObject mismatch = witness(position, stored, selected, block, raw.getBlock(y));
            dimension.mismatches.add(mismatch);
          }
          continue;
        }
        if (rawMaterial) {
          JsonObject result = witness(position, stored, selected, block, raw.getBlock(y));
          result.addProperty("proof_kind", "matching_source_stored_raw_and_FULL_material");
          dimension.material.put(stored, result);
        } else if (featureMaterial) {
          boolean references =
              current.getAllReferences().values().stream().anyMatch(set -> !set.isEmpty());
          boolean starts =
              current.getAllStarts().values().stream().anyMatch(start -> start.isValid());
          if (references || starts) {
            dimension.structureExcludedSmallIslandBlocks++;
            continue;
          }
          JsonObject result = witness(position, stored, selected, block, raw.getBlock(y));
          result.addProperty(
              "proof_kind", "natural_FULL_end_stone_on_raw_air_without_structure_references");
          result.addProperty("native_feature_wired", END_ISLAND_FEATURE);
          result.addProperty("source_feature_commit_observed", false);
          result.addProperty("structure_references_empty", true);
          result.addProperty("valid_structure_starts_absent", true);
          result.addProperty("counts_as_raw_terrain_material", false);
          result.addProperty(
              "route_attribution",
              "Native feature wiring plus physical observation; no per-write feature observer");
          dimension.featureLand = result;
        }
      }
    }

    private void release() {
      if (request != null) {
        request.close();
        request = null;
      }
      current = null;
    }

    private void stopping(ServerStoppingEvent event) {
      if (!done && event.getServer() == overworld.getServer())
        finish("server_stopping", new IllegalStateException("Dimension biome audit interrupted"));
    }

    private void finish(String reason, RuntimeException failure) {
      if (done) return;
      done = true;
      release();
      NeoForge.EVENT_BUS.unregister(tickListener);
      NeoForge.EVENT_BUS.unregister(stopListener);
      boolean complete =
          dimensions.stream().allMatch(dimension -> dimension.missingPhysical().isEmpty());
      write(failure != null ? "error" : complete ? "complete" : "finite_missing", reason, failure);
      if (failure == null && complete)
        LogUtils.getLogger().info("Dimension biome coverage regression passed: output={}", output);
      else
        LogUtils.getLogger()
            .info(
                "Dimension biome coverage regression completed: complete={} reason={} output={}",
                complete,
                reason,
                output);
    }

    private void write(String status, String termination, RuntimeException failure) {
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("status", status);
      report.addProperty("termination", termination);
      report.addProperty("host_seed", overworld.getSeed());
      report.addProperty("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
      report.addProperty("deadline_seconds", deadline);
      report.addProperty("candidate_column_budget_per_dimension", candidateBudget);
      report.addProperty("raw_column_budget_per_dimension", rawBudget);
      report.addProperty("FULL_request_budget_per_dimension", fullBudget);
      report.addProperty("request_timeout_seconds", requestTimeout);
      report.addProperty("maximum_single_step_ms", maximumStep / 1_000_000.0);
      report.addProperty("cooperative_slice_ms", SLICE_NANOS / 1_000_000.0);
      report.addProperty("active_owned_FULL_tickets", request == null ? 0 : 1);
      report.addProperty("maximum_owned_FULL_tickets", maximumTickets);
      JsonArray reports = new JsonArray();
      dimensions.forEach(dimension -> reports.add(dimension.report()));
      report.add("dimensions", reports);
      if (failure != null) report.addProperty("failure", failure.toString());
      report.addProperty(
          "limits",
          "Actual loaded dimension sources and their own randomState are used. Candidate randomness only selects search coordinates; native feature/structure RNG, placement, biome rules and topology are unchanged. Each regular witness requires current source==stored FULL ID and both raw/FULL natural material. Small End island FULL end_stone on raw AIR is a separate physical feature-land route, never raw-terrain proof; structure-referenced chunks are excluded and exact per-write feature attribution is not claimed. Finite misses do not establish absence. No client, decoration completeness, frequency, mono-island, island-tier, lifecycle or visual acceptance.");
      try {
        Files.createDirectories(output.getParent());
        Files.writeString(
            output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      } catch (IOException failureWriting) {
        throw new IllegalStateException(
            "Cannot write dimension biome report " + output, failureWriting);
      }
    }
  }

  private static JsonObject witness(
      BlockPos position, String stored, String source, BlockState block, BlockState raw) {
    JsonObject result = new JsonObject();
    result.addProperty("x", position.getX());
    result.addProperty("y", position.getY());
    result.addProperty("z", position.getZ());
    result.addProperty("stored_biome", stored);
    result.addProperty("source_biome", source);
    result.addProperty("block", BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString());
    result.addProperty("raw_block", BuiltInRegistries.BLOCK.getKey(raw.getBlock()).toString());
    return result;
  }

  private static int integer(String suffix, int fallback, int maximum) {
    int value = Integer.getInteger("aeroscapes.regression.dimensionBiome" + suffix, fallback);
    require(value > 0 && value <= maximum, "Invalid dimension biome budget " + suffix);
    return value;
  }

  private static JsonArray strings(Set<String> values) {
    JsonArray result = new JsonArray();
    new TreeSet<>(values).forEach(result::add);
    return result;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
