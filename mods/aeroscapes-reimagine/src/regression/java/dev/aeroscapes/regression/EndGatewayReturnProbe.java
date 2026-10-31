package dev.aeroscapes.regression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.regression.noise.mixin.FeatureIndexAccess;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.EndGatewayFeature;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.RandomOffsetPlacement;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Native return-gateway existence probe, separate from dragon/gateway-use lifecycle. */
public final class EndGatewayReturnProbe {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson JSON = new Gson();
  private static final String FEATURE = "minecraft:end_gateway_return";
  private static final TicketType<UUID> TICKET =
      TicketType.create("aeroscapes_return_gateway_probe", UUID::compareTo);

  private EndGatewayReturnProbe() {}

  public static void start(ServerLevel level, Path planFile, Path output) {
    if (!level.getServer().isDedicatedServer()
        || !level.getServer().isSameThread()
        || !level.dimension().equals(Level.END))
      throw new IllegalStateException(
          "Return gateway probe requires the dedicated End server thread");
    if (!level.getServer().getWorldData().worldGenOptions().generateStructures())
      throw new IllegalStateException("Gateway probe preserves generateStructures=true");
    Audit audit = new Audit(level, read(planFile), output.toAbsolutePath().normalize());
    NeoForge.EVENT_BUS.addListener(audit.tickListener);
    NeoForge.EVENT_BUS.addListener(audit.stopListener);
    LOGGER.info(
        "End gateway return probe started: seed={} index={} output={}",
        level.getSeed(),
        audit.index,
        output);
  }

  private static final class Request {
    private final ServerLevel level;
    private final ChunkPos pos;
    private final UUID key = UUID.randomUUID();
    private final long started = System.nanoTime();
    private CompletableFuture<ChunkResult<ChunkAccess>> future;

    private Request(ServerLevel level, ChunkPos pos) {
      this.level = level;
      this.pos = pos;
      level
          .getChunkSource()
          .chunkMap
          .getDistanceManager()
          .addTicket(TICKET, pos, ChunkLevel.byStatus(ChunkStatus.FULL), key);
    }

    private ChunkAccess poll(int timeout) {
      var map = level.getChunkSource().chunkMap;
      if (future == null) {
        var holder = map.getVisibleChunkIfPresent(pos.toLong());
        if (holder != null && holder.getTicketLevel() <= ChunkLevel.byStatus(ChunkStatus.FULL))
          future = holder.scheduleChunkGenerationTask(ChunkStatus.FULL, map);
      }
      if (future == null || !future.isDone()) {
        if (System.nanoTime() - started > timeout * 1_000_000_000L)
          throw new IllegalStateException("Timed out waiting for gateway footprint FULL " + pos);
        return null;
      }
      var result = future.join();
      var chunk = result.orElse(null);
      if (!result.isSuccess()
          || chunk == null
          || chunk.getPersistedStatus() != ChunkStatus.FULL
          || !chunk.getPos().equals(pos))
        throw new IllegalStateException("Native gateway FULL failed " + pos);
      return chunk;
    }

    private void close() {
      level
          .getChunkSource()
          .chunkMap
          .getDistanceManager()
          .removeTicket(TICKET, pos, ChunkLevel.byStatus(ChunkStatus.FULL), key);
    }
  }

  private static final class Audit {
    private final ServerLevel level;
    private final JsonObject plan;
    private final Path output;
    private final Path journalPath;
    private final BufferedWriter journal;
    private final PlacedFeature feature;
    private final List<FeatureSorter.StepFeatureData> featureSteps;
    private final int step = GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal();
    private final int index;
    private final BlockPos exit;
    private final boolean exact;
    private final JsonElement definition;
    private final List<ChunkPos> centers = new ArrayList<>();
    private final Set<Long> seen = new HashSet<>();
    private final Map<Long, Request> requests = new LinkedHashMap<>();
    private final Map<Long, ChunkAccess> chunks = new LinkedHashMap<>();
    private final Map<Long, CompletableFuture<ChunkResult<ChunkAccess>>> origins = new TreeMap<>();
    private final JsonArray candidates = new JsonArray();
    private final JsonArray exceptions = new JsonArray();
    private final Consumer<ServerTickEvent.Post> tickListener = this::tick;
    private final Consumer<ServerStoppingEvent> stopListener = this::stopping;
    private final long started = System.nanoTime();
    private final int candidateBudget, rawBudget, fullBudget, radiusLimit, deadline, timeout;
    private final boolean requirePositive;
    private long nextProgress, maximumStep, journalRecords, referenceStarted;
    private int radius,
        perimeter,
        centerIndex,
        enumerated,
        rawColumns,
        fullCandidates,
        maximumTickets;
    private int phase;
    private WorldgenRandom random;
    private BlockPos squarePoint, target;
    private JsonObject candidate, positive;
    private String journalHash;
    private String termination = "running";
    private boolean done;

    private Audit(ServerLevel level, JsonObject plan, Path output) {
      this.level = level;
      this.plan = plan;
      this.output = output;
      if (number(plan, "seed").longValueExact() != level.getSeed())
        throw new IllegalArgumentException("Return gateway seed mismatch");
      if (integer(plan, "format", 1) != 1)
        throw new IllegalArgumentException("Unknown gateway plan format");
      candidateBudget = positive(plan, "candidate_chunk_budget", 100000);
      rawBudget = positive(plan, "raw_column_budget", 256);
      fullBudget = positive(plan, "full_candidate_budget", 8);
      radiusLimit = positive(plan, "radius_chunks", 256);
      deadline = positive(plan, "deadline_seconds", 600);
      timeout = positive(plan, "request_timeout_seconds", 120);
      requirePositive = plan.has("require_positive") && bool(plan, "require_positive");
      if (!plan.has("centers") || !plan.get("centers").isJsonArray())
        throw new IllegalArgumentException("Gateway centers must be an array");
      for (JsonElement value : plan.getAsJsonArray("centers")) {
        if (!value.isJsonObject())
          throw new IllegalArgumentException("Gateway center must be an object");
        JsonObject point = value.getAsJsonObject();
        centers.add(
            new ChunkPos(
                number(point, "x").intValueExact() >> 4, number(point, "z").intValueExact() >> 4));
      }
      if (centers.isEmpty())
        throw new IllegalArgumentException("Gateway search needs outer-End centers");
      feature =
          level
              .registryAccess()
              .registryOrThrow(Registries.PLACED_FEATURE)
              .get(ResourceLocation.parse(FEATURE));
      if (feature == null
          || !(feature.feature().value().feature() instanceof EndGatewayFeature)
          || !(feature.feature().value().config() instanceof EndGatewayConfiguration config))
        throw new IllegalStateException("Actual return route is not native EndGatewayFeature");
      exit =
          config
              .getExit()
              .orElseThrow(() -> new IllegalStateException("Return route has no configured exit"));
      exact = config.isExitExact();
      var modifiers = feature.placement();
      if (modifiers.size() != 5
          || !(modifiers.get(0) instanceof RarityFilter)
          || !(modifiers.get(1) instanceof InSquarePlacement)
          || !(modifiers.get(2) instanceof HeightmapPlacement)
          || !(modifiers.get(3) instanceof RandomOffsetPlacement)
          || !(modifiers.get(4) instanceof BiomeFilter))
        throw new IllegalStateException("Unexpected actual return gateway modifier order");
      var generator = level.getChunkSource().getGenerator();
      featureSteps = ((FeatureIndexAccess) generator).aeroscapesRegression$featuresPerStep().get();
      var stage = featureSteps.get(step);
      index = stage.indexMapping().applyAsInt(feature);
      if (index < 0
          || index >= stage.features().size()
          || !stage.features().get(index).equals(feature))
        throw new IllegalStateException("No actual native return gateway feature index");
      var encoded =
          PlacedFeature.DIRECT_CODEC.encodeStart(
              RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), feature);
      definition =
          encoded
              .result()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Cannot serialize actual gateway placement: "
                              + encoded.error().map(error -> error.message()).orElse("unknown")));
      journalPath = output.resolveSibling(output.getFileName() + ".rng.jsonl");
      try {
        Files.createDirectories(output.getParent());
        journal = Files.newBufferedWriter(journalPath);
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot open gateway RNG evidence", exception);
      }
    }

    private void tick(ServerTickEvent.Post event) {
      if (done || event.getServer() != level.getServer()) return;
      try {
        if (System.nanoTime() - started > deadline * 1_000_000_000L) {
          finish("deadline_exhausted", null);
          return;
        }
        long until = System.nanoTime() + 10_000_000L;
        while (!done && System.nanoTime() < until) {
          long before = System.nanoTime();
          boolean progress = advance();
          maximumStep = Math.max(maximumStep, System.nanoTime() - before);
          if (!progress) break;
        }
        if (!done && System.nanoTime() >= nextProgress) {
          nextProgress = System.nanoTime() + 10_000_000_000L;
          journal.flush();
          write("running");
          LOGGER.info(
              "End return gateway progress: RNG={} raw={} FULL={}",
              enumerated,
              rawColumns,
              fullCandidates);
        }
      } catch (IOException exception) {
        var failure = new IllegalStateException("Cannot flush gateway evidence", exception);
        try {
          if (!done) finish("exception", failure);
        } catch (RuntimeException finishFailure) {
          failure.addSuppressed(finishFailure);
        }
        throw failure;
      } catch (RuntimeException exception) {
        try {
          if (!done) finish("exception", exception);
        } catch (RuntimeException finishFailure) {
          exception.addSuppressed(finishFailure);
        }
        throw exception;
      }
    }

    private boolean advance() {
      var generator = level.getChunkSource().getGenerator();
      if (((FeatureIndexAccess) generator).aeroscapesRegression$featuresPerStep().get()
          != featureSteps) throw new IllegalStateException("Actual native feature index changed");
      if (phase == 0) return nextCandidate();
      if (phase == 1) {
        if (rawColumns >= rawBudget) {
          finish("raw_column_budget_exhausted", null);
          return false;
        }
        int height =
            generator.getBaseHeight(
                squarePoint.getX(),
                squarePoint.getZ(),
                Heightmap.Types.MOTION_BLOCKING,
                level,
                level.getChunkSource().randomState());
        rawColumns++;
        candidate.addProperty("raw_heightmap_first_free_y", height);
        if (height <= level.getMinBuildHeight()) {
          reject("raw_empty_island");
          return true;
        }
        PlacementContext context = new PlacementContext(level, generator, Optional.of(feature));
        var shifted =
            feature
                .placement()
                .get(3)
                .getPositions(
                    context, random, new BlockPos(squarePoint.getX(), height, squarePoint.getZ()))
                .toList();
        if (shifted.size() != 1)
          throw new IllegalStateException("Native random_offset emitted unexpected positions");
        target = shifted.getFirst().immutable();
        candidate.add("native_random_offset_target", block(target));
        candidate.addProperty("rng_count_after_random_offset", random.getCount());
        JsonObject suffix = new JsonObject();
        suffix.addProperty("record_type", "native_random_offset");
        suffix.addProperty("ordinal", enumerated);
        suffix.add("target", block(target));
        suffix.addProperty("rng_count", random.getCount());
        journal(suffix);
        if (target.getY() - 2 < level.getMinBuildHeight()
            || target.getY() + 2 >= level.getMaxBuildHeight()) {
          reject("raw_target_outside_build_height");
          return true;
        }
        Holder<Biome> biome =
            generator
                .getBiomeSource()
                .getNoiseBiome(
                    QuartPos.fromBlock(target.getX()), QuartPos.fromBlock(target.getY()),
                    QuartPos.fromBlock(target.getZ()),
                        level.getChunkSource().randomState().sampler());
        candidate.addProperty(
            "source_quart_biome", biome.unwrapKey().orElseThrow().location().toString());
        if (!biome.value().getGenerationSettings().hasFeature(feature)) {
          reject("source_quart_feature_membership_heuristic_rejected");
          return true;
        }
        if (fullCandidates >= fullBudget) {
          finish("FULL_candidate_budget_exhausted", null);
          return false;
        }
        fullCandidates++;
        for (int x = (target.getX() - 1) >> 4; x <= (target.getX() + 1) >> 4; x++)
          for (int z = (target.getZ() - 1) >> 4; z <= (target.getZ() + 1) >> 4; z++) {
            ChunkPos pos = new ChunkPos(x, z);
            requests.put(pos.toLong(), new Request(level, pos));
          }
        maximumTickets = Math.max(maximumTickets, requests.size());
        candidate.addProperty("status", "awaiting_natural_FULL");
        phase = 2;
        return true;
      }
      if (phase == 2) {
        boolean ready = true;
        for (var entry : requests.entrySet()) {
          if (chunks.containsKey(entry.getKey())) continue;
          var chunk = entry.getValue().poll(timeout);
          if (chunk == null) ready = false;
          else chunks.put(entry.getKey(), chunk);
        }
        if (!ready) return false;
        origins.clear();
        referenceStarted = System.nanoTime();
        for (var chunk : chunks.values())
          for (var references : chunk.getAllReferences().values())
            for (long origin : references) origins.putIfAbsent(origin, null);
        phase = 3;
        return true;
      }
      boolean ready = true;
      var map = level.getChunkSource().chunkMap;
      for (var entry : origins.entrySet()) {
        if (entry.getValue() == null) {
          var holder = map.getVisibleChunkIfPresent(entry.getKey());
          if (holder == null
              || holder.getTicketLevel() > ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS)) {
            ready = false;
            continue;
          }
          entry.setValue(holder.scheduleChunkGenerationTask(ChunkStatus.STRUCTURE_STARTS, map));
        }
        if (!entry.getValue().isDone()) ready = false;
        else if (!entry.getValue().join().isSuccess())
          throw new IllegalStateException("Referenced native origin failed");
      }
      if (!ready) {
        if (System.nanoTime() - referenceStarted > timeout * 1_000_000_000L)
          throw new IllegalStateException(
              "Timed out resolving gateway native structure references");
        return false;
      }
      if (structureOverlap()) {
        reject("native_structure_XZ_footprint_overlap");
        return true;
      }
      JsonObject proof = inspectGateway();
      if (proof == null) {
        reject("natural_FULL_has_no_predicted_gateway_signature");
        return true;
      }
      positive = proof;
      candidate.addProperty("status", "native_feature_block_positive");
      candidate.add("observed", proof);
      finish("natural_return_gateway_positive", null);
      return false;
    }

    private boolean nextCandidate() {
      if (enumerated >= candidateBudget || radius > radiusLimit) {
        finish("finite_candidate_budget_exhausted", null);
        return false;
      }
      ChunkPos center = centers.get(centerIndex);
      int dx = 0, dz = 0;
      if (radius > 0) {
        int width = radius * 2, side = perimeter / width, offset = perimeter % width;
        dx =
            switch (side) {
              case 0 -> -radius + offset;
              case 1 -> radius;
              case 2 -> radius - offset;
              default -> -radius;
            };
        dz =
            switch (side) {
              case 0 -> -radius;
              case 1 -> -radius + offset;
              case 2 -> radius;
              default -> radius - offset;
            };
      }
      ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
      if (++centerIndex >= centers.size()) {
        centerIndex = 0;
        if (++perimeter >= (radius == 0 ? 1 : radius * 8)) {
          perimeter = 0;
          radius++;
        }
      }
      if (!seen.add(pos.toLong())) return true;
      enumerated++;
      random = new WorldgenRandom(new XoroshiroRandomSource(0L));
      long decoration =
          random.setDecorationSeed(level.getSeed(), pos.getMinBlockX(), pos.getMinBlockZ());
      random.setFeatureSeed(decoration, index, step);
      int setupCount = random.getCount();
      var generator = level.getChunkSource().getGenerator();
      PlacementContext context = new PlacementContext(level, generator, Optional.of(feature));
      BlockPos origin =
          new BlockPos(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ());
      var rarity = feature.placement().getFirst().getPositions(context, random, origin).toList();
      JsonObject record = new JsonObject();
      record.addProperty("record_type", "native_RNG_prefix");
      record.addProperty("ordinal", enumerated);
      record.add("chunk", chunk(pos));
      record.addProperty("decoration_seed", decoration);
      record.addProperty("native_feature_index", index);
      record.addProperty("native_decoration_step", step);
      record.addProperty("feature_seed", decoration + index + 10000L * step);
      record.addProperty("rng_count_after_setup", setupCount);
      record.addProperty("rarity_pass", !rarity.isEmpty());
      record.addProperty("rng_count_after_rarity", random.getCount());
      if (rarity.isEmpty()) {
        journal(record);
        return true;
      }
      if (rarity.size() != 1)
        throw new IllegalStateException("Native gateway rarity emitted multiple positions");
      var square =
          feature.placement().get(1).getPositions(context, random, rarity.getFirst()).toList();
      if (square.size() != 1)
        throw new IllegalStateException("Native gateway square emitted multiple positions");
      squarePoint = square.getFirst().immutable();
      record.add("native_in_square", block(squarePoint));
      record.addProperty("rng_count_after_in_square", random.getCount());
      journal(record);
      candidate = record.deepCopy();
      candidate.addProperty("status", "raw_candidate_pending");
      candidates.add(candidate);
      phase = 1;
      return true;
    }

    private boolean structureOverlap() {
      var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      JsonArray overlap = new JsonArray();
      for (var future : origins.values()) {
        var origin = future.join().orElse(null);
        if (origin == null)
          throw new IllegalStateException("Missing gateway referenced native origin");
        for (var start : origin.getAllStarts().values()) {
          if (start == null || !start.isValid() || start.getPieces().isEmpty()) continue;
          for (var piece : start.getPieces())
            if (piece
                .getBoundingBox()
                .intersects(
                    target.getX() - 1, target.getZ() - 1, target.getX() + 1, target.getZ() + 1)) {
              JsonObject row = new JsonObject();
              row.addProperty("structure", registry.getKey(start.getStructure()).toString());
              row.add("origin", chunk(start.getChunkPos()));
              overlap.add(row);
              break;
            }
        }
      }
      if (!overlap.isEmpty()) candidate.add("structure_exclusions", overlap);
      return !overlap.isEmpty();
    }

    private JsonObject inspectGateway() {
      int bedrock = 0, air = 0;
      for (int x = -1; x <= 1; x++)
        for (int y = -2; y <= 2; y++)
          for (int z = -1; z <= 1; z++) {
            Block expected = Blocks.AIR;
            if (x == 0 && y == 0 && z == 0) expected = Blocks.END_GATEWAY;
            else if (y != 0
                && ((Math.abs(y) == 2 && x == 0 && z == 0)
                    || (Math.abs(y) != 2 && (x == 0 || z == 0)))) expected = Blocks.BEDROCK;
            BlockPos pos = target.offset(x, y, z);
            BlockState actual = state(pos);
            if (!actual.is(expected)) {
              JsonObject mismatch = new JsonObject();
              mismatch.add("position", block(pos));
              mismatch.addProperty("expected", BuiltInRegistries.BLOCK.getKey(expected).toString());
              mismatch.addProperty("actual", actual.toString());
              candidate.add("first_signature_mismatch", mismatch);
              return null;
            }
            if (expected == Blocks.BEDROCK) bedrock++;
            else if (expected == Blocks.AIR) air++;
          }
      var chunk = chunks.get(new ChunkPos(target).toLong());
      if (!(chunk.getBlockEntity(target) instanceof TheEndGatewayBlockEntity entity)) return null;
      var tag = entity.saveWithoutMetadata(level.registryAccess());
      var actualExit = NbtUtils.readBlockPos(tag, "exit_portal");
      if (actualExit.isEmpty()
          || !actualExit.get().equals(exit)
          || tag.getBoolean("ExactTeleport") != exact) return null;
      Holder<Biome> biome = level.getBiome(target);
      if (!biome.value().getGenerationSettings().hasFeature(feature)) return null;
      JsonObject proof = new JsonObject();
      proof.addProperty("route", FEATURE);
      proof.addProperty("native_feature_index", index);
      proof.addProperty("native_decoration_step", step);
      proof.addProperty(
          "native_decoration_stage", GenerationStep.Decoration.SURFACE_STRUCTURES.name());
      proof.add("gateway", block(target));
      proof.add("configured_exit", block(exit));
      proof.addProperty("exact_teleport", exact);
      proof.addProperty("observed_bedrock_blocks", bedrock);
      proof.addProperty("observed_air_blocks", air);
      proof.addProperty(
          "level_biome_at_target", biome.unwrapKey().orElseThrow().location().toString());
      JsonArray footprint = new JsonArray();
      chunks.values().forEach(value -> footprint.add(chunk(value.getPos())));
      proof.add("FULL_chunks", footprint);
      proof.addProperty(
          "proof_level",
          "native_RNG_and_offset_candidate_FULL_exact_gateway_frame_and_exit_NBT_outside_structure_XZ_footprints");
      proof.addProperty(
          "scope",
          "Ordinary chunk-decorated return gateway only. No dragon-fight, teleport, linked-exit or player lifecycle method invoked.");
      return proof;
    }

    private BlockState state(BlockPos pos) {
      var chunk = chunks.get(new ChunkPos(pos).toLong());
      if (chunk == null)
        throw new IllegalStateException("Gateway proof left its held FULL footprint");
      return chunk.getBlockState(pos);
    }

    private void reject(String reason) {
      candidate.addProperty("status", reason);
      release();
      phase = 0;
    }

    private void release() {
      requests.values().forEach(Request::close);
      requests.clear();
      chunks.clear();
      origins.clear();
    }

    private void journal(JsonObject record) {
      try {
        journal.write(JSON.toJson(record));
        journal.newLine();
        journalRecords++;
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot retain gateway RNG evidence", exception);
      }
    }

    private void stopping(ServerStoppingEvent event) {
      if (!done && event.getServer() == level.getServer())
        finish("server_stopping", new IllegalStateException("Gateway probe interrupted"));
    }

    private void finish(String reason, RuntimeException failure) {
      if (done) return;
      done = true;
      termination = reason;
      if (positive == null && candidate != null && failure == null) {
        String state = candidate.get("status").getAsString();
        if (state.contains("pending") || state.startsWith("awaiting")) {
          candidate.addProperty("status", "incomplete_at_finite_termination");
          candidate.addProperty("termination", reason);
        }
      }
      if (failure != null) {
        JsonObject error = new JsonObject();
        error.addProperty("class", failure.getClass().getName());
        error.addProperty("message", failure.getMessage());
        error.addProperty("phase", phase);
        exceptions.add(error);
      }
      release();
      NeoForge.EVENT_BUS.unregister(tickListener);
      NeoForge.EVENT_BUS.unregister(stopListener);
      try {
        journal.close();
        journalHash = hash(journalPath);
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot finalize gateway journal", exception);
      }
      write(failure != null ? "error" : positive == null ? "finite_missing" : "complete");
      if (failure != null) return;
      if (positive == null && requirePositive)
        throw new IllegalStateException("Required native return gateway finite-missing: " + output);
      LOGGER.info(
          "End gateway return probe completed: positive={} termination={} output={}",
          positive != null,
          reason,
          output);
    }

    private void write(String status) {
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("status", status);
      report.addProperty("termination", termination);
      report.addProperty("seed", level.getSeed());
      report.addProperty("dimension", level.dimension().location().toString());
      report.add("plan", plan.deepCopy());
      report.add("actual_placed_feature", definition);
      report.addProperty("native_feature_index", index);
      report.addProperty("native_decoration_step", step);
      report.addProperty(
          "native_decoration_stage", GenerationStep.Decoration.SURFACE_STRUCTURES.name());
      report.addProperty(
          "rng_counter_semantics",
          "Count includes four decoration-seed setup calls; rarity/in_square normally 5/7; native random_offset samples constant-zero X/Z without draws and uniform Y advances the same local RNG from 7 to 8.");
      report.addProperty("candidate_chunks_enumerated", enumerated);
      report.addProperty("raw_columns", rawColumns);
      report.addProperty("FULL_candidates", fullCandidates);
      report.addProperty("maximum_owned_FULL_tickets", maximumTickets);
      report.addProperty("active_owned_tickets", requests.size());
      report.addProperty("elapsed_ms", (System.nanoTime() - started) / 1_000_000L);
      report.addProperty("maximum_single_step_ms", maximumStep / 1_000_000.0);
      report.addProperty("cooperative_slice_ms", 10);
      report.addProperty("rng_evidence", journalPath.toString());
      report.addProperty("rng_evidence_records", journalRecords);
      if (journalHash != null) report.addProperty("rng_evidence_sha256", journalHash);
      report.addProperty(
          "scope",
          "Directed existence search; raw height/source are candidate heuristics. Native FULL blocks/NBT are final; no changed rates, placement calls, shared RNG/sampler mutation or boss lifecycle. Finite zero never proves global absence.");
      report.add("rarity_passed_candidates", candidates);
      if (positive != null) report.add("observation", positive);
      report.add("exceptions", exceptions);
      try {
        Files.createDirectories(output.getParent());
        Files.writeString(
            output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot write gateway probe evidence", exception);
      }
    }
  }

  private static JsonObject read(Path path) {
    try {
      JsonElement value = JsonParser.parseString(Files.readString(path));
      if (!value.isJsonObject())
        throw new IllegalArgumentException("Gateway plan must be an object");
      return value.getAsJsonObject();
    } catch (IOException exception) {
      throw new IllegalArgumentException("Cannot read gateway plan", exception);
    }
  }

  private static java.math.BigDecimal number(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException("Expected JSON number " + key);
    return value.getAsBigDecimal();
  }

  private static int integer(JsonObject object, String key, int fallback) {
    return object.has(key) ? number(object, key).intValueExact() : fallback;
  }

  private static int positive(JsonObject object, String key, int fallback) {
    int value = integer(object, key, fallback);
    if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
    return value;
  }

  private static boolean bool(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
      throw new IllegalArgumentException("Expected boolean " + key);
    return value.getAsBoolean();
  }

  private static JsonArray chunk(ChunkPos pos) {
    JsonArray value = new JsonArray();
    value.add(pos.x);
    value.add(pos.z);
    return value;
  }

  private static JsonArray block(BlockPos pos) {
    JsonArray value = new JsonArray();
    value.add(pos.getX());
    value.add(pos.getY());
    value.add(pos.getZ());
    return value;
  }

  private static String hash(Path path) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(path)) {
        byte[] bytes = new byte[65536];
        int count;
        while ((count = input.read(bytes)) >= 0) digest.update(bytes, 0, count);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Cannot hash gateway evidence", exception);
    }
  }
}
