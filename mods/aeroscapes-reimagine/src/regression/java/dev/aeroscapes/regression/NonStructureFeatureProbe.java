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
import java.util.LinkedHashSet;
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
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.DesertWellFeature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Observes natural feature blocks after FULL generation; never invokes feature placement. */
public final class NonStructureFeatureProbe {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson JSON = new Gson();
  private static final TicketType<UUID> TICKET =
      TicketType.create("aeroscapes_non_structure_probe", UUID::compareTo);
  private static final String WELL = "minecraft:desert_well";
  private static final String WELL_LOOT = "minecraft:archaeology/desert_well";
  private static final String DUNGEON_LOOT = "minecraft:chests/simple_dungeon";

  private NonStructureFeatureProbe() {}

  public static void start(ServerLevel level, Path planFile, Path output) {
    if (!level.getServer().isDedicatedServer() || !level.getServer().isSameThread())
      throw new IllegalStateException("Feature probe requires the dedicated server thread");
    if (!level.getServer().getWorldData().worldGenOptions().generateStructures())
      throw new IllegalStateException("Feature probe preserves generateStructures=true");
    Audit audit = new Audit(level, Plan.read(planFile), output.toAbsolutePath().normalize());
    NeoForge.EVENT_BUS.addListener(audit.tickListener);
    NeoForge.EVENT_BUS.addListener(audit.stopListener);
    LOGGER.info(
        "Non-structure feature probe started: mode={} seed={} output={}",
        audit.plan.mode,
        level.getSeed(),
        audit.output);
  }

  private record Center(int x, int z) {}

  private record Plan(
      String mode,
      long seed,
      List<Center> centers,
      int radius,
      int candidateBudget,
      int rawBudget,
      int fullBudget,
      int deadline,
      int requestTimeout,
      boolean requirePositive,
      Path structureReport,
      Path savedResult,
      int savedChunkBudget,
      JsonObject original) {
    private static Plan read(Path path) {
      JsonObject json = readObject(path);
      if (integer(json, "format", 1) != 1)
        throw new IllegalArgumentException("Unknown probe format");
      String mode = string(json, "mode");
      if (!Set.of("desert_well", "saved_dungeon", "end_chunks").contains(mode))
        throw new IllegalArgumentException("Unknown feature probe mode " + mode);
      List<Center> centers = new ArrayList<>();
      if (json.has("centers"))
        for (JsonElement value : array(json, "centers")) {
          if (!value.isJsonObject()) throw new IllegalArgumentException("Center must be an object");
          JsonObject center = value.getAsJsonObject();
          centers.add(
              new Center(number(center, "x").intValueExact(), number(center, "z").intValueExact()));
        }
      if (centers.isEmpty()) centers.add(new Center(0, 0));
      int radius = integer(json, "radius_chunks", 256);
      if (radius < 0) throw new IllegalArgumentException("radius_chunks must be nonnegative");
      return new Plan(
          mode,
          number(json, "seed").longValueExact(),
          List.copyOf(centers),
          radius,
          positive(json, "candidate_chunk_budget", 100000),
          positive(json, "raw_column_budget", 512),
          positive(json, "full_candidate_budget", 8),
          positive(json, "deadline_seconds", 600),
          positive(json, "request_timeout_seconds", 120),
          bool(json, "require_positive", false),
          json.has("structure_report") ? Path.of(string(json, "structure_report")) : null,
          json.has("saved_world_result") ? Path.of(string(json, "saved_world_result")) : null,
          positive(json, "saved_chunk_budget", 256),
          json.deepCopy());
    }
  }

  private static final class Request {
    private final ServerLevel level;
    private final ChunkPos pos;
    private final UUID key = UUID.randomUUID();
    private final long started = System.nanoTime();
    private CompletableFuture<ChunkResult<ChunkAccess>> future;
    private boolean closed;

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
      var holder = map.getVisibleChunkIfPresent(pos.toLong());
      if (future == null) {
        if (holder != null && holder.getTicketLevel() <= ChunkLevel.byStatus(ChunkStatus.FULL))
          future = holder.scheduleChunkGenerationTask(ChunkStatus.FULL, map);
      }
      if (future == null || !future.isDone()) {
        if (System.nanoTime() - started > timeout * 1_000_000_000L)
          throw new IllegalStateException("Timed out waiting for natural FULL " + pos);
        return null;
      }
      ChunkResult<ChunkAccess> result = future.join();
      ChunkAccess chunk = result.orElse(null);
      if (!result.isSuccess()
          || chunk == null
          || !chunk.getPos().equals(pos)
          || chunk.getPersistedStatus() != ChunkStatus.FULL)
        throw new IllegalStateException(
            "Natural FULL request failed " + pos + " " + result.getError());
      return chunk;
    }

    private void close() {
      if (!closed) {
        closed = true;
        level
            .getChunkSource()
            .chunkMap
            .getDistanceManager()
            .removeTicket(TICKET, pos, ChunkLevel.byStatus(ChunkStatus.FULL), key);
      }
    }
  }

  private static final class Audit {
    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final Plan plan;
    private final Path output;
    private final Consumer<ServerTickEvent.Post> tickListener = this::tick;
    private final Consumer<ServerStoppingEvent> stopListener = this::stopping;
    private final long started = System.nanoTime();
    private final Map<Long, Request> requests = new LinkedHashMap<>();
    private final Map<Long, ChunkAccess> chunks = new LinkedHashMap<>();
    private final Map<Long, CompletableFuture<ChunkResult<ChunkAccess>>> references =
        new TreeMap<>();
    private final Set<Long> seen = new HashSet<>();
    private final JsonArray candidates = new JsonArray();
    private final JsonArray positives = new JsonArray();
    private final JsonArray exceptions = new JsonArray();
    private final List<ChunkPos> savedChunks = new ArrayList<>();
    private final List<BlockPos> savedSpawners = new ArrayList<>();
    private final List<SpikeFeature.EndSpike> spikes = new ArrayList<>();
    private final Set<Long> everRequested = new HashSet<>();
    private final PlacedFeature well;
    private final List<FeatureSorter.StepFeatureData> featureSteps;
    private final int featureIndex;
    private final int featureStep = GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal();
    private final JsonElement wellDefinition;
    private final Path journalPath;
    private BufferedWriter journal;
    private String journalHash;
    private long journalRecords;
    private long nextProgress;
    private long maxStep;
    private int maximumTickets;
    private int radius;
    private int perimeter;
    private int center;
    private long enumerated;
    private int rawColumns;
    private int fullCandidates;
    private int phase;
    private BlockPos candidatePoint;
    private JsonObject candidate;
    private NoiseColumn rawCenter;
    private int rawBaseY;
    private int supportIndex;
    private int archaeologyOne;
    private int archaeologyTwo;
    private CompletableFuture<Optional<CompoundTag>> savedRead;
    private long savedReadStarted;
    private long referencesStarted;
    private int savedIndex;
    private int spawnerIndex;
    private int spikeIndex;
    private int spikeColumn;
    private long spikeVerified;
    private long endReadyAt;
    private boolean endPlatformVerified;
    private boolean done;
    private String termination = "running";

    private Audit(ServerLevel level, Plan plan, Path output) {
      this.level = level;
      this.generator = level.getChunkSource().getGenerator();
      this.plan = plan;
      this.output = output;
      if (level.getSeed() != plan.seed)
        throw new IllegalArgumentException("Feature plan seed mismatch");
      boolean end = plan.mode.equals("end_chunks");
      if (!level.dimension().equals(end ? Level.END : Level.OVERWORLD))
        throw new IllegalArgumentException("Probe mode uses the wrong actual dimension");
      if (plan.mode.equals("desert_well")) {
        well =
            level
                .registryAccess()
                .registryOrThrow(Registries.PLACED_FEATURE)
                .get(ResourceLocation.parse(WELL));
        if (well == null)
          throw new IllegalStateException("Missing actual desert_well placed feature");
        if (!(well.feature().value().feature() instanceof DesertWellFeature))
          throw new IllegalStateException(
              "Actual configured well is not the native DesertWellFeature");
        featureSteps =
            ((FeatureIndexAccess) generator).aeroscapesRegression$featuresPerStep().get();
        var step = featureSteps.get(featureStep);
        featureIndex = step.indexMapping().applyAsInt(well);
        if (featureIndex < 0
            || featureIndex >= step.features().size()
            || !step.features().get(featureIndex).equals(well))
          throw new IllegalStateException("Desert well has no actual native feature index");
        var modifiers = well.placement();
        if (modifiers.size() != 4
            || !(modifiers.get(0) instanceof RarityFilter)
            || !(modifiers.get(1) instanceof InSquarePlacement)
            || !(modifiers.get(2) instanceof HeightmapPlacement)
            || !(modifiers.get(3) instanceof BiomeFilter))
          throw new IllegalStateException("Unexpected actual desert well modifier order");
        var encoded =
            PlacedFeature.DIRECT_CODEC.encodeStart(
                RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), well);
        wellDefinition =
            encoded
                .result()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Cannot serialize actual well: "
                                + encoded.error().map(error -> error.message()).orElse("unknown")));
        journalPath = output.resolveSibling(output.getFileName() + ".rng.jsonl");
        try {
          Files.createDirectories(output.getParent());
          journal = Files.newBufferedWriter(journalPath);
        } catch (IOException exception) {
          throw new IllegalStateException("Cannot open native RNG evidence journal", exception);
        }
      } else {
        well = null;
        featureSteps = List.of();
        featureIndex = -1;
        wellDefinition = null;
        journalPath = null;
      }
      if (plan.mode.equals("saved_dungeon")) prepareSavedChunks();
      if (end) {
        spikes.addAll(SpikeFeature.getSpikesForLevel(level));
        if (spikes.size() != SpikeFeature.NUMBER_OF_SPIKES)
          throw new IllegalStateException("Unexpected native End spike list");
        Set<ChunkPos> footprint = new LinkedHashSet<>();
        for (var spike : spikes) {
          if (spike.getHeight() + 10 >= level.getMaxBuildHeight())
            throw new IllegalStateException("Native spike extends above actual build height");
          footprint.addAll(rectangle(spike.getCenterX(), spike.getCenterZ(), spike.getRadius()));
        }
        footprint.addAll(rectangle(100, 0, 2));
        beginFull(footprint);
      }
    }

    private void prepareSavedChunks() {
      if (plan.structureReport == null || plan.savedResult == null)
        throw new IllegalArgumentException(
            "saved_dungeon requires structure_report and saved_world_result");
      JsonObject save = readObject(plan.savedResult);
      if (!bool(save, "saved_all_dimensions", false)
          || integer(save, "jvm_exit", -1) != 0
          || !array(save, "errors").isEmpty())
        throw new IllegalArgumentException("Producer world lacks completed clean-save evidence");
      JsonObject report = readObject(plan.structureReport);
      if (!string(report, "status").equals("complete")
          || number(report, "seed").longValueExact() != plan.seed
          || !array(report, "exceptions").isEmpty())
        throw new IllegalArgumentException("Prior structure collection is incomplete/wrong seed");
      Set<Long> positions = new LinkedHashSet<>();
      for (JsonElement value : array(report, "structures")) {
        if (!value.isJsonObject())
          throw new IllegalArgumentException("Malformed prior structure row");
        JsonObject row = value.getAsJsonObject();
        if (!row.has("natural_FULL")) continue;
        if (!row.get("natural_FULL").isJsonObject())
          throw new IllegalArgumentException("Prior natural_FULL must be an object");
        JsonObject proof = row.getAsJsonObject("natural_FULL");
        if (!string(proof, "dimension").equals(level.dimension().location().toString())) continue;
        for (String field : List.of("origin_chunk", "root_piece_chunk")) {
          JsonArray pair = array(proof, field);
          if (pair.size() != 2)
            throw new IllegalArgumentException("Malformed saved FULL coordinate");
          positions.add(new ChunkPos(exactInt(pair.get(0)), exactInt(pair.get(1))).toLong());
        }
      }
      positions.stream().limit(plan.savedChunkBudget).map(ChunkPos::new).forEach(savedChunks::add);
      if (savedChunks.isEmpty())
        throw new IllegalArgumentException("No prior FULL chunks for dungeon scan");
    }

    private void tick(ServerTickEvent.Post event) {
      if (done || event.getServer() != level.getServer()) return;
      try {
        if (System.nanoTime() - started >= plan.deadline * 1_000_000_000L) {
          finish("deadline_exhausted", null);
          return;
        }
        long until = System.nanoTime() + 10_000_000L;
        while (!done && System.nanoTime() < until) {
          long before = System.nanoTime();
          boolean progressed =
              switch (plan.mode) {
                case "desert_well" -> advanceWell();
                case "saved_dungeon" -> advanceDungeon();
                default -> advanceEnd();
              };
          maxStep = Math.max(maxStep, System.nanoTime() - before);
          if (!progressed) break;
        }
        if (!done && System.nanoTime() >= nextProgress) {
          nextProgress = System.nanoTime() + 10_000_000_000L;
          flushJournal();
          write("running");
          LOGGER.info(
              "Non-structure probe progress: mode={} RNG={} raw={} FULL-candidates={} positives={}",
              plan.mode,
              enumerated,
              rawColumns,
              fullCandidates,
              positives.size());
        }
      } catch (RuntimeException exception) {
        if (!done) finish("exception", exception);
        throw exception;
      }
    }

    private boolean advanceWell() {
      if (((FeatureIndexAccess) generator).aeroscapesRegression$featuresPerStep().get()
          != featureSteps)
        throw new IllegalStateException("Actual native feature index changed during the probe");
      if (phase == 0) return nextWellCandidate();
      if (phase == 1) {
        if (!rawAvailable()) return false;
        rawCenter =
            generator.getBaseColumn(
                candidatePoint.getX(),
                candidatePoint.getZ(),
                level,
                level.getChunkSource().randomState());
        rawColumns++;
        rawBaseY = level.getMaxBuildHeight() - 1;
        while (rawBaseY >= level.getMinBuildHeight()
            && !Heightmap.Types.MOTION_BLOCKING.isOpaque().test(rawCenter.getBlock(rawBaseY)))
          rawBaseY--;
        candidate.addProperty("raw_surface_y", rawBaseY);
        if (rawBaseY < level.getMinBuildHeight() + 2
            || rawBaseY + 4 >= level.getMaxBuildHeight()
            || !rawCenter.getBlock(rawBaseY).getFluidState().isEmpty()) {
          reject("raw_height_or_water_heuristic_rejected");
          return true;
        }
        Holder<Biome> biome =
            generator
                .getBiomeSource()
                .getNoiseBiome(
                    QuartPos.fromBlock(candidatePoint.getX()), QuartPos.fromBlock(rawBaseY + 1),
                    QuartPos.fromBlock(candidatePoint.getZ()),
                        level.getChunkSource().randomState().sampler());
        candidate.addProperty(
            "source_quart_biome", biome.unwrapKey().orElseThrow().location().toString());
        if (!biome.value().getGenerationSettings().hasFeature(well)) {
          reject("source_quart_feature_membership_heuristic_rejected");
          return true;
        }
        supportIndex = 0;
        phase = 2;
        return true;
      }
      if (phase == 2) {
        if (supportIndex < 25) {
          int dx = supportIndex % 5 - 2;
          int dz = supportIndex / 5 - 2;
          if ((dx != 0 || dz != 0) && !rawAvailable()) return false;
          NoiseColumn column = rawCenter;
          if (dx != 0 || dz != 0) {
            column =
                generator.getBaseColumn(
                    candidatePoint.getX() + dx,
                    candidatePoint.getZ() + dz,
                    level,
                    level.getChunkSource().randomState());
            rawColumns++;
          }
          supportIndex++;
          if (column.getBlock(rawBaseY - 1).isAir() && column.getBlock(rawBaseY - 2).isAir())
            reject("raw_support_heuristic_rejected");
          return true;
        }
        if (fullCandidates >= plan.fullBudget) {
          finish("FULL_candidate_budget_exhausted", null);
          return false;
        }
        fullCandidates++;
        candidate.addProperty("status", "awaiting_natural_FULL");
        beginFull(rectangle(candidatePoint.getX(), candidatePoint.getZ(), 2));
        phase = 3;
        return true;
      }
      if (phase == 3) {
        if (!pollFull()) return false;
        prepareReferences();
        phase = 4;
        return true;
      }
      if (!pollReferences()) return false;
      JsonArray overlap =
          overlaps(
              candidatePoint.getX() - 2,
              candidatePoint.getZ() - 2,
              candidatePoint.getX() + 2,
              candidatePoint.getZ() + 2);
      if (!overlap.isEmpty()) {
        candidate.add("structure_exclusions", overlap);
        reject("native_structure_XZ_footprint_overlap");
        return true;
      }
      for (int y = level.getMaxBuildHeight() - 5; y >= level.getMinBuildHeight() + 2; y--) {
        BlockPos base = new BlockPos(candidatePoint.getX(), y, candidatePoint.getZ());
        JsonObject proof = wellProof(base);
        if (proof == null) continue;
        candidate.addProperty("status", "native_feature_block_positive");
        candidate.add("observed", proof);
        positives.add(proof);
        finish("desert_well_positive", null);
        return false;
      }
      reject("natural_FULL_has_no_complete_predicted_well_signature");
      return true;
    }

    private boolean nextWellCandidate() {
      if (enumerated >= plan.candidateBudget || radius > plan.radius) {
        finish("finite_candidate_budget_exhausted", null);
        return false;
      }
      Center origin = plan.centers.get(center);
      int dx = 0, dz = 0;
      if (radius > 0) {
        int width = 2 * radius;
        int side = perimeter / width, offset = perimeter % width;
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
      ChunkPos pos = new ChunkPos((origin.x >> 4) + dx, (origin.z >> 4) + dz);
      if (++center >= plan.centers.size()) {
        center = 0;
        if (++perimeter >= (radius == 0 ? 1 : radius * 8)) {
          perimeter = 0;
          radius++;
        }
      }
      if (!seen.add(pos.toLong())) return true;
      enumerated++;
      WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
      long decoration =
          random.setDecorationSeed(level.getSeed(), pos.getMinBlockX(), pos.getMinBlockZ());
      random.setFeatureSeed(decoration, featureIndex, featureStep);
      int setupCount = random.getCount();
      PlacementContext context = new PlacementContext(level, generator, Optional.of(well));
      BlockPos originPos =
          new BlockPos(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ());
      List<BlockPos> rarity =
          well.placement().getFirst().getPositions(context, random, originPos).toList();
      JsonObject evidence = new JsonObject();
      evidence.addProperty("ordinal", enumerated);
      evidence.add("chunk", chunk(pos));
      evidence.addProperty("decoration_seed", decoration);
      evidence.addProperty("native_feature_index", featureIndex);
      evidence.addProperty("native_decoration_step", featureStep);
      evidence.addProperty("feature_seed", decoration + featureIndex + 10000L * featureStep);
      evidence.addProperty("rng_count_after_setup", setupCount);
      evidence.addProperty("rarity_pass", !rarity.isEmpty());
      evidence.addProperty("rng_count_after_rarity", random.getCount());
      if (rarity.isEmpty()) {
        journal(evidence);
        return true;
      }
      if (rarity.size() != 1)
        throw new IllegalStateException("Native rarity emitted multiple positions");
      List<BlockPos> square =
          well.placement().get(1).getPositions(context, random, rarity.getFirst()).toList();
      if (square.size() != 1)
        throw new IllegalStateException("Native in_square emitted unexpected positions");
      candidatePoint = square.getFirst().immutable();
      evidence.add("native_in_square", block(candidatePoint));
      evidence.addProperty("rng_count_after_in_square", random.getCount());
      archaeologyOne = random.nextInt(5);
      archaeologyTwo = random.nextInt(5);
      evidence.addProperty("archaeology_first_index_if_well_places", archaeologyOne);
      evidence.addProperty("archaeology_second_index_if_well_places", archaeologyTwo);
      evidence.addProperty("rng_count_after_hypothetical_archaeology", random.getCount());
      journal(evidence);
      candidate = evidence.deepCopy();
      candidate.addProperty("status", "raw_candidate_filter_pending");
      candidates.add(candidate);
      phase = 1;
      return true;
    }

    private boolean rawAvailable() {
      if (rawColumns < plan.rawBudget) return true;
      finish("raw_column_budget_exhausted", null);
      return false;
    }

    private void reject(String reason) {
      candidate.addProperty("status", reason);
      releaseFull();
      phase = 0;
      rawCenter = null;
    }

    private JsonObject wellProof(BlockPos base) {
      List<BlockPos> cross = List.of(base, base.east(), base.south(), base.west(), base.north());
      if (cross.stream().anyMatch(pos -> !state(pos).is(Blocks.WATER))) return null;
      BlockPos first = cross.get(archaeologyOne).below();
      BlockPos second = cross.get(archaeologyTwo).below(2);
      for (int dy = -2; dy <= 0; dy++)
        for (int dx = -2; dx <= 2; dx++)
          for (int dz = -2; dz <= 2; dz++) {
            BlockPos pos = base.offset(dx, dy, dz);
            Block expected = Blocks.SANDSTONE;
            if (dy == 0 && cross.contains(pos)) expected = Blocks.WATER;
            if (dy == -1 && cross.contains(pos.above())) expected = Blocks.SAND;
            if (pos.equals(first) || pos.equals(second)) expected = Blocks.SUSPICIOUS_SAND;
            if (!state(pos).is(expected)) return null;
          }
      for (int dx = -2; dx <= 2; dx++)
        for (int dz = -2; dz <= 2; dz++)
          if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
            boolean slab = (Math.abs(dx) == 2 && dz == 0) || (dx == 0 && Math.abs(dz) == 2);
            if (!state(base.offset(dx, 1, dz)).is(slab ? Blocks.SANDSTONE_SLAB : Blocks.SANDSTONE))
              return null;
          }
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++)
          if (!state(base.offset(dx, 4, dz))
              .is(dx == 0 && dz == 0 ? Blocks.SANDSTONE : Blocks.SANDSTONE_SLAB)) return null;
      for (int dy = 1; dy <= 3; dy++)
        for (int dx : new int[] {-1, 1})
          for (int dz : new int[] {-1, 1})
            if (!state(base.offset(dx, dy, dz)).is(Blocks.SANDSTONE)) return null;
      JsonArray archaeology = new JsonArray();
      for (BlockPos pos : List.of(first, second)) {
        BlockEntity entity = blockEntity(pos);
        if (!(entity instanceof BrushableBlockEntity)) return null;
        CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
        if (!tag.getString("LootTable").equals(WELL_LOOT)
            || tag.getLong("LootTableSeed") != pos.asLong()) return null;
        JsonObject entry = new JsonObject();
        entry.add("position", block(pos));
        entry.addProperty("loot_table", tag.getString("LootTable"));
        entry.addProperty("loot_seed", tag.getLong("LootTableSeed"));
        archaeology.add(entry);
      }
      Holder<Biome> biome = level.getBiome(base.above());
      if (!biome.value().getGenerationSettings().hasFeature(well)) return null;
      JsonObject proof = new JsonObject();
      proof.addProperty("route", WELL);
      proof.add("center_water", block(base));
      proof.addProperty(
          "proof_level",
          "native_RNG_candidate_FULL_exact_well_blocks_and_archaeology_outside_structure_XZ_footprints");
      proof.addProperty(
          "actual_biome_at_original_heightmap_position",
          biome.unwrapKey().orElseThrow().location().toString());
      proof.add("archaeology", archaeology);
      proof.add("FULL_chunks", fullPositions());
      return proof;
    }

    private void beginFull(Set<ChunkPos> positions) {
      if (!requests.isEmpty())
        throw new IllegalStateException("Previous FULL footprint still held");
      for (ChunkPos pos : positions) {
        requests.put(pos.toLong(), new Request(level, pos));
        everRequested.add(pos.toLong());
      }
      maximumTickets = Math.max(maximumTickets, requests.size());
    }

    private boolean pollFull() {
      boolean ready = true;
      for (var entry : requests.entrySet()) {
        if (chunks.containsKey(entry.getKey())) continue;
        ChunkAccess chunk = entry.getValue().poll(plan.requestTimeout);
        if (chunk == null) ready = false;
        else chunks.put(entry.getKey(), chunk);
      }
      return ready;
    }

    private void prepareReferences() {
      references.clear();
      referencesStarted = System.nanoTime();
      for (ChunkAccess chunk : chunks.values())
        for (var set : chunk.getAllReferences().values())
          for (long origin : set) references.putIfAbsent(origin, null);
    }

    private boolean pollReferences() {
      boolean ready = true;
      var map = level.getChunkSource().chunkMap;
      for (var entry : references.entrySet()) {
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
          throw new IllegalStateException("Native referenced origin request failed");
      }
      if (!ready && System.nanoTime() - referencesStarted > plan.requestTimeout * 1_000_000_000L)
        throw new IllegalStateException("Timed out loading native referenced origins");
      return ready;
    }

    private JsonArray overlaps(int minX, int minZ, int maxX, int maxZ) {
      JsonArray found = new JsonArray();
      Registry<net.minecraft.world.level.levelgen.structure.Structure> registry =
          level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      Set<String> duplicate = new HashSet<>();
      for (var future : references.values()) {
        ChunkAccess origin = future.join().orElse(null);
        if (origin == null) throw new IllegalStateException("Missing referenced native origin");
        for (StructureStart start : origin.getAllStarts().values()) {
          if (start == null || !start.isValid() || start.getPieces().isEmpty()) continue;
          for (var piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (!box.intersects(minX, minZ, maxX, maxZ)) continue;
            String key = registry.getKey(start.getStructure()) + ":" + start.getChunkPos();
            if (duplicate.add(key)) {
              JsonObject row = new JsonObject();
              row.addProperty("structure", registry.getKey(start.getStructure()).toString());
              row.add("origin", chunk(start.getChunkPos()));
              row.add("piece_bounds", bounds(box));
              found.add(row);
            }
            break;
          }
        }
      }
      return found;
    }

    private void releaseFull() {
      requests.values().forEach(Request::close);
      requests.clear();
      chunks.clear();
      references.clear();
    }

    private BlockState state(BlockPos pos) {
      ChunkAccess chunk = chunks.get(new ChunkPos(pos).toLong());
      if (chunk == null)
        throw new IllegalStateException("Block proof left its held FULL footprint " + pos);
      return chunk.getBlockState(pos);
    }

    private BlockEntity blockEntity(BlockPos pos) {
      ChunkAccess chunk = chunks.get(new ChunkPos(pos).toLong());
      if (chunk == null) throw new IllegalStateException("Block entity proof left FULL footprint");
      return chunk.getBlockEntity(pos);
    }

    private boolean advanceDungeon() {
      if (phase == 0) {
        if (savedIndex >= savedChunks.size()) {
          finish("saved_FULL_scan_exhausted", null);
          return false;
        }
        if (savedRead == null) {
          savedReadStarted = System.nanoTime();
          savedRead = level.getChunkSource().chunkMap.read(savedChunks.get(savedIndex));
          return false;
        }
        if (!savedRead.isDone()) {
          if (System.nanoTime() - savedReadStarted > plan.requestTimeout * 1_000_000_000L)
            throw new IllegalStateException("Timed out reading saved FULL chunk");
          return false;
        }
        Optional<CompoundTag> raw = savedRead.join();
        candidate = new JsonObject();
        candidate.add("source_saved_chunk", chunk(savedChunks.get(savedIndex)));
        candidates.add(candidate);
        savedIndex++;
        savedRead = null;
        if (raw.isEmpty() || !raw.get().getString("Status").equals(ChunkStatus.FULL.getName())) {
          candidate.addProperty("status", "not_saved_FULL_skipped_without_generation");
          return true;
        }
        savedSpawners.clear();
        spawnerIndex = 0;
        for (Tag value : raw.get().getList("block_entities", Tag.TAG_COMPOUND)) {
          CompoundTag entity = (CompoundTag) value;
          if (entity.getString("id").equals("minecraft:mob_spawner"))
            savedSpawners.add(
                new BlockPos(entity.getInt("x"), entity.getInt("y"), entity.getInt("z")));
        }
        candidate.addProperty("saved_native_spawners", savedSpawners.size());
        phase = 1;
        return true;
      }
      if (phase == 1) {
        if (spawnerIndex >= savedSpawners.size()) {
          candidate.addProperty("status", "saved_FULL_spawners_scanned");
          phase = 0;
          return true;
        }
        if (fullCandidates >= plan.fullBudget) {
          finish("FULL_candidate_budget_exhausted", null);
          return false;
        }
        candidatePoint = savedSpawners.get(spawnerIndex++);
        fullCandidates++;
        beginFull(rectangle(candidatePoint.getX(), candidatePoint.getZ(), 4));
        phase = 2;
        return true;
      }
      if (phase == 2) {
        if (!pollFull()) return false;
        prepareReferences();
        phase = 3;
        return true;
      }
      if (!pollReferences()) return false;
      JsonObject observation = dungeonProof(candidatePoint);
      if (observation != null) {
        positives.add(observation);
        candidate.addProperty("status", "natural_dungeon_block_signature_positive");
        finish("dungeon_block_positive", null);
        return false;
      }
      releaseFull();
      phase = 1;
      return true;
    }

    private JsonObject dungeonProof(BlockPos pos) {
      if (!state(pos).is(Blocks.SPAWNER) || !(blockEntity(pos) instanceof SpawnerBlockEntity))
        return null;
      if (!overlaps(pos.getX() - 4, pos.getZ() - 4, pos.getX() + 4, pos.getZ() + 4).isEmpty())
        return null;
      CompoundTag spawner = blockEntity(pos).saveWithoutMetadata(level.registryAccess());
      String mob = spawner.getCompound("SpawnData").getCompound("entity").getString("id");
      if (!Set.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider").contains(mob))
        return null;
      int moss = 0, cobble = 0;
      JsonArray chests = new JsonArray();
      for (int dx = -4; dx <= 4; dx++)
        for (int dz = -4; dz <= 4; dz++) {
          BlockState floor = state(pos.offset(dx, -1, dz));
          if (floor.is(Blocks.MOSSY_COBBLESTONE)) moss++;
          if (floor.is(Blocks.COBBLESTONE)) cobble++;
          BlockPos chest = pos.offset(dx, 0, dz);
          if (state(chest).is(Blocks.CHEST) && blockEntity(chest) != null) {
            CompoundTag tag = blockEntity(chest).saveWithoutMetadata(level.registryAccess());
            if (tag.getString("LootTable").equals(DUNGEON_LOOT)) chests.add(block(chest));
          }
        }
      if (moss < 8 || cobble < 1 || chests.isEmpty()) return null;
      JsonObject proof = new JsonObject();
      proof.addProperty("route", "minecraft:monster_room");
      proof.addProperty(
          "native_decoration_step", GenerationStep.Decoration.UNDERGROUND_STRUCTURES.ordinal());
      proof.addProperty(
          "native_decoration_stage", GenerationStep.Decoration.UNDERGROUND_STRUCTURES.name());
      proof.addProperty(
          "proof_level",
          "saved_FULL_native_dungeon_block_signature_outside_structure_XZ_footprints");
      proof.add("spawner", block(pos));
      proof.addProperty("spawned_mob", mob);
      proof.addProperty("mossy_floor_blocks", moss);
      proof.addProperty("cobblestone_floor_blocks", cobble);
      proof.add("simple_dungeon_loot_chests", chests);
      proof.add("FULL_chunks", fullPositions());
      proof.addProperty(
          "scope",
          "Stored native spawner/floor/chest signature; no feature-return observer. Neighbor footprint chunks naturally completed to FULL.");
      return proof;
    }

    private boolean advanceEnd() {
      if (phase == 0) {
        if (!pollFull()) return false;
        endReadyAt = System.nanoTime();
        phase = 1;
        return true;
      }
      if (spikeIndex < spikes.size()) {
        var spike = spikes.get(spikeIndex);
        int diameter = spike.getRadius() * 2 + 1;
        if (spikeColumn < diameter * diameter) {
          int dx = spikeColumn % diameter - spike.getRadius();
          int dz = spikeColumn / diameter - spike.getRadius();
          for (int y = level.getMinBuildHeight(); y <= spike.getHeight() + 10; y++) {
            Block expected = null;
            if (dx * dx + dz * dz <= spike.getRadius() * spike.getRadius() + 1
                && y < spike.getHeight()) expected = Blocks.OBSIDIAN;
            else if (y > 65) expected = Blocks.AIR;
            int relativeY = y - spike.getHeight();
            if (spike.isGuarded()
                && Math.abs(dx) <= 2
                && Math.abs(dz) <= 2
                && relativeY >= 0
                && relativeY <= 3
                && (Math.abs(dx) == 2 || Math.abs(dz) == 2 || relativeY == 3))
              expected = Blocks.IRON_BARS;
            if (dx == 0 && dz == 0 && relativeY == 0) expected = Blocks.BEDROCK;
            if (dx == 0 && dz == 0 && relativeY == 1) expected = Blocks.FIRE;
            if (expected != null) {
              requireBlock(
                  new BlockPos(spike.getCenterX() + dx, y, spike.getCenterZ() + dz), expected);
              spikeVerified++;
            }
          }
          spikeColumn++;
          return true;
        }
        AABB box =
            new AABB(
                spike.getCenterX() - 1,
                spike.getHeight(),
                spike.getCenterZ() - 1,
                spike.getCenterX() + 2,
                spike.getHeight() + 3,
                spike.getCenterZ() + 2);
        List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, box);
        if (crystals.isEmpty()
            && System.nanoTime() - endReadyAt < plan.requestTimeout * 1_000_000_000L) return false;
        if (crystals.size() != 1)
          throw new IllegalStateException("Expected one natural crystal at spike " + spikeIndex);
        EndCrystal crystal = crystals.getFirst();
        if (crystal.isInvulnerable()
            || crystal.getBeamTarget() != null
            || Math.abs(crystal.getX() - spike.getCenterX() - 0.5) > 0.001
            || Math.abs(crystal.getY() - spike.getHeight() - 1) > 0.001
            || Math.abs(crystal.getZ() - spike.getCenterZ() - 0.5) > 0.001)
          throw new IllegalStateException("Natural End crystal differs from native configuration");
        JsonObject proof = new JsonObject();
        proof.addProperty("route", "minecraft:end_spike");
        proof.addProperty(
            "native_decoration_step", GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal());
        proof.addProperty(
            "native_decoration_stage", GenerationStep.Decoration.SURFACE_STRUCTURES.name());
        proof.addProperty("center_x", spike.getCenterX());
        proof.addProperty("center_z", spike.getCenterZ());
        proof.addProperty("height", spike.getHeight());
        proof.addProperty("radius", spike.getRadius());
        proof.addProperty("guarded", spike.isGuarded());
        proof.addProperty("observed_crystal_uuid", crystal.getUUID().toString());
        proof.addProperty(
            "proof_level", "native_seed_spike_FULL_exact_blocks_cage_and_live_crystal");
        positives.add(proof);
        spikeIndex++;
        spikeColumn = 0;
        return true;
      }
      for (int dx = -2; dx <= 2; dx++)
        for (int dz = -2; dz <= 2; dz++)
          for (int y = 48; y <= 51; y++)
            requireBlock(new BlockPos(100 + dx, y, dz), y == 48 ? Blocks.OBSIDIAN : Blocks.AIR);
      JsonObject platform = new JsonObject();
      platform.addProperty("route", "minecraft:end_platform");
      platform.addProperty(
          "native_decoration_step", GenerationStep.Decoration.TOP_LAYER_MODIFICATION.ordinal());
      platform.addProperty(
          "native_decoration_stage", GenerationStep.Decoration.TOP_LAYER_MODIFICATION.name());
      platform.addProperty("obsidian_floor_blocks", 25);
      platform.addProperty("cleared_air_blocks", 75);
      platform.addProperty("proof_level", "native_fixed_position_FULL_exact_platform_blocks");
      positives.add(platform);
      endPlatformVerified = true;
      finish("End_chunk_features_positive", null);
      return false;
    }

    private void requireBlock(BlockPos pos, Block expected) {
      if (!state(pos).is(expected))
        throw new IllegalStateException(
            "Native feature block differs at "
                + pos
                + ": expected "
                + BuiltInRegistries.BLOCK.getKey(expected)
                + ", got "
                + state(pos));
    }

    private JsonArray fullPositions() {
      JsonArray positions = new JsonArray();
      chunks.values().forEach(value -> positions.add(chunk(value.getPos())));
      return positions;
    }

    private void journal(JsonObject record) {
      try {
        journal.write(JSON.toJson(record));
        journal.newLine();
        journalRecords++;
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot retain native RNG evidence", exception);
      }
    }

    private void flushJournal() {
      try {
        if (journal != null) journal.flush();
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot flush RNG evidence", exception);
      }
    }

    private void stopping(ServerStoppingEvent event) {
      if (!done && event.getServer() == level.getServer())
        finish(
            "server_stopping",
            new IllegalStateException("Server stopped before non-structure probe finished"));
    }

    private void finish(String reason, RuntimeException failure) {
      if (done) return;
      done = true;
      termination = reason;
      if (failure != null) {
        JsonObject error = new JsonObject();
        error.addProperty("class", failure.getClass().getName());
        error.addProperty("message", failure.getMessage());
        error.addProperty("phase", phase);
        exceptions.add(error);
      }
      releaseFull();
      NeoForge.EVENT_BUS.unregister(tickListener);
      NeoForge.EVENT_BUS.unregister(stopListener);
      try {
        if (journal != null) {
          journal.close();
          journal = null;
          journalHash = sha256(journalPath);
        }
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot finalize RNG evidence", exception);
      }
      boolean missing =
          plan.mode.equals("end_chunks")
              ? spikeIndex != spikes.size() || !endPlatformVerified
              : positives.isEmpty();
      if (missing && candidate != null && failure == null) {
        JsonElement state = candidate.get("status");
        if (state == null
            || state.getAsString().contains("pending")
            || state.getAsString().startsWith("awaiting")) {
          candidate.addProperty("status", "incomplete_at_finite_termination");
          candidate.addProperty("termination", reason);
        }
      }
      write(failure != null ? "error" : missing ? "finite_missing" : "complete");
      if (failure != null) return;
      if (missing && plan.requirePositive)
        throw new IllegalStateException(
            "Required natural feature remains finite-missing: " + output);
      LOGGER.info(
          "Non-structure feature probe completed: mode={} observations={} termination={} output={}",
          plan.mode,
          positives.size(),
          reason,
          output);
    }

    private void write(String status) {
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("status", status);
      report.addProperty("termination", termination);
      report.addProperty("mode", plan.mode);
      report.addProperty("seed", level.getSeed());
      report.addProperty("dimension", level.dimension().location().toString());
      report.add("plan", plan.original.deepCopy());
      report.addProperty(
          "scope",
          "Directed existence/block evidence only; finite misses never prove global absence. Native chunk generation performs all placement. No /place, feature.place, changed weights/chances, shared sampler flags or lifecycle invocation.");
      report.addProperty("elapsed_ms", (System.nanoTime() - started) / 1_000_000L);
      report.addProperty("cooperative_slice_ms", 10);
      report.addProperty("maximum_single_step_ms", maxStep / 1_000_000.0);
      report.addProperty("candidate_chunks_enumerated", enumerated);
      report.addProperty("raw_columns", rawColumns);
      report.addProperty("FULL_candidates", fullCandidates);
      report.addProperty("distinct_requested_FULL_chunks", everRequested.size());
      report.addProperty("maximum_owned_FULL_tickets", maximumTickets);
      report.addProperty("active_owned_tickets", requests.size());
      if (well != null) {
        report.addProperty("native_feature_index", featureIndex);
        report.addProperty("native_decoration_step", featureStep);
      }
      if (wellDefinition != null) report.add("actual_placed_feature", wellDefinition);
      if (journalPath != null) {
        report.addProperty("rng_evidence", journalPath.toString());
        report.addProperty("rng_evidence_records", journalRecords);
        if (journalHash != null) report.addProperty("rng_evidence_sha256", journalHash);
      }
      report.addProperty("source_quart_and_raw_checks_are_heuristics", true);
      report.addProperty(
          "rng_counter_semantics",
          "WorldgenRandom count includes four decoration-seed setup calls; rarity/in_square are normally 5/7, hypothetical well archaeology normally 9. Rejection sampling may consume additional calls.");
      report.addProperty("saved_FULL_chunks_examined", savedIndex);
      report.addProperty("end_spike_blocks_verified", spikeVerified);
      report.addProperty("end_spikes_completed", spikeIndex);
      report.addProperty("end_platform_verified", endPlatformVerified);
      report.addProperty(
          "unique_observed_routes",
          positives.asList().stream()
              .map(value -> value.getAsJsonObject().get("route").getAsString())
              .distinct()
              .count());
      report.add("rarity_passed_filter_candidates", candidates);
      report.add("observations", positives);
      report.add("exceptions", exceptions);
      try {
        Files.createDirectories(output.getParent());
        Files.writeString(
            output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot write feature probe evidence " + output, exception);
      }
    }
  }

  private static Set<ChunkPos> rectangle(int x, int z, int radius) {
    Set<ChunkPos> positions = new LinkedHashSet<>();
    for (int chunkX = (x - radius) >> 4; chunkX <= (x + radius) >> 4; chunkX++)
      for (int chunkZ = (z - radius) >> 4; chunkZ <= (z + radius) >> 4; chunkZ++)
        positions.add(new ChunkPos(chunkX, chunkZ));
    return positions;
  }

  private static JsonObject readObject(Path path) {
    try {
      JsonElement value = JsonParser.parseString(Files.readString(path));
      if (!value.isJsonObject()) throw new IllegalArgumentException("Expected object " + path);
      return value.getAsJsonObject();
    } catch (IOException exception) {
      throw new IllegalArgumentException("Cannot read probe input " + path, exception);
    }
  }

  private static java.math.BigDecimal number(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException("Expected JSON number " + key);
    return value.getAsBigDecimal();
  }

  private static int exactInt(JsonElement value) {
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException("Expected exact integer");
    return value.getAsBigDecimal().intValueExact();
  }

  private static int integer(JsonObject object, String key, int fallback) {
    return object.has(key) ? number(object, key).intValueExact() : fallback;
  }

  private static int positive(JsonObject object, String key, int fallback) {
    int value = integer(object, key, fallback);
    if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
    return value;
  }

  private static String string(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException("Expected string " + key);
    return value.getAsString();
  }

  private static boolean bool(JsonObject object, String key, boolean fallback) {
    if (!object.has(key)) return fallback;
    JsonElement value = object.get(key);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
      throw new IllegalArgumentException("Expected boolean " + key);
    return value.getAsBoolean();
  }

  private static JsonArray array(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonArray())
      throw new IllegalArgumentException("Expected array " + key);
    return value.getAsJsonArray();
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

  private static JsonArray bounds(BoundingBox box) {
    JsonArray value = new JsonArray();
    for (int coordinate :
        new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()})
      value.add(coordinate);
    return value;
  }

  private static String sha256(Path path) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(path)) {
        byte[] buffer = new byte[65536];
        int size;
        while ((size = input.read(buffer)) >= 0) digest.update(buffer, 0, size);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Cannot hash evidence " + path, exception);
    }
  }
}
