package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.regression.noise.mixin.FeatureIndexAccess;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Observes natural target blocks in ordinary FULL chunks without placing features or crops. */
public final class PlantCoverageRegression {
  private static final TicketType<ChunkPos> PLANT_TICKET =
      TicketType.create("aeroscapes_plant_coverage", Comparator.comparingLong(ChunkPos::toLong));
  private static final Set<String> KINDS = Set.of("wild", "ingredient", "decorative", "cultivated");
  private static final int STRUCTURE_SPILL_MARGIN = 32;
  private static final int STRUCTURE_REFERENCE_RADIUS = (STRUCTURE_SPILL_MARGIN + 15) / 16;

  private PlantCoverageRegression() {}

  private record Target(String family, String kind, Set<Block> blocks, Set<String> ids) {}

  private record PlannedChunk(ChunkPos position) {}

  private record RngPlan(
      List<String> features, int radius, int candidateBudget, int rawBudget, int fullBudget) {}

  private record Plan(
      List<Target> targets,
      List<PlannedChunk> chunks,
      Set<String> expected,
      int columnsPerTick,
      int deadlineSeconds,
      boolean surfaceSurvey,
      RngPlan rng,
      Map<String, Set<Block>> featureTrace,
      JsonObject controllerManifest) {}

  public static void start(ServerLevel level, Path plan, Path output) {
    Path input = plan.toAbsolutePath().normalize();
    Path destination = output.toAbsolutePath().normalize();
    Probe probe = null;
    try {
      require(!input.equals(destination), "Plant output must differ from its plan");
      require(
          level.getServer().isDedicatedServer() && level.dimension() == Level.OVERWORLD,
          "Plant coverage needs a disposable dedicated Overworld");
      require(level.getServer().isSameThread(), "Start plant coverage on the server thread");
      require(
          level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
          "Plant coverage needs the supported normal noise generator");
      probe = new Probe(level, input, destination, readPlan(level, input));
      probe.checkSampler();
      probe.armTrace();
      probe.write("running", null);
      NeoForge.EVENT_BUS.addListener(probe::tick);
      NeoForge.EVENT_BUS.addListener(probe::stopping);
      LogUtils.getLogger()
          .info(
              "Plant FULL coverage started: seed={} chunks={} families={} output={}",
              level.getSeed(),
              probe.plan.chunks().size(),
              probe.plan.expected().size(),
              destination);
    } catch (IOException | ReflectiveOperationException | RuntimeException failure) {
      if (probe != null) probe.disarmTrace();
      JsonObject error = new JsonObject();
      error.addProperty("format", 1);
      error.addProperty("status", "error");
      error.addProperty("seed", level.getSeed());
      error.addProperty("plan", input.toString());
      error.addProperty("failure", failure.toString());
      if (!input.equals(destination)) write(destination, error);
      throw new IllegalStateException("Cannot start plant FULL coverage", failure);
    }
  }

  private static Plan readPlan(ServerLevel level, Path input) throws IOException {
    JsonObject root = object(JsonParser.parseString(Files.readString(input)), "plan");
    require(number(root.get("format"), "format") == 1, "Unsupported plant plan format");
    int columns = bounded(root, "columns_per_tick", 4, 1, 16);
    int deadline = bounded(root, "deadline_seconds", 600, 1, 1800);
    boolean surfaceSurvey =
        root.has("surface_survey") && bool(root.get("surface_survey"), "surface_survey");
    RngPlan rng = null;
    if (root.has("fd_rng_search")) {
      JsonObject search = object(root.get("fd_rng_search"), "fd_rng_search");
      List<String> features = new ArrayList<>();
      for (JsonElement value : array(search.get("features"), "features")) {
        String id = string(value, "FD placed feature");
        require(
            id.startsWith("farmersdelight:") && !features.contains(id),
            "Invalid FD RNG feature " + id);
        features.add(id);
      }
      require(!features.isEmpty(), "FD RNG search has no features");
      rng =
          new RngPlan(
              List.copyOf(features),
              bounded(search, "radius_chunks", 64, 0, 512),
              bounded(search, "candidate_budget", 16384, 1, 1000000),
              bounded(search, "raw_budget", 512, 1, 10000),
              bounded(search, "full_budget", 32, 1, 512));
    }
    List<Target> targets = new ArrayList<>();
    Set<String> families = new HashSet<>();
    Set<Block> allBlocks = new HashSet<>();
    for (JsonElement element : array(root.get("targets"), "targets")) {
      JsonObject row = object(element, "target");
      String family = string(row.get("family"), "family");
      require(!family.isBlank() && families.add(family), "Invalid/duplicate family " + family);
      String kind = string(row.get("kind"), "kind");
      require(KINDS.contains(kind), "Unknown plant route kind " + kind);
      Set<Block> blocks = new HashSet<>();
      Set<String> ids = new TreeSet<>();
      for (JsonElement value : array(row.get("blocks"), "blocks")) {
        String id = string(value, "block ID");
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        require(parsed != null && id.equals(parsed.toString()), "Noncanonical plant block " + id);
        require(BuiltInRegistries.BLOCK.containsKey(parsed), "Unregistered target block " + id);
        Block block = BuiltInRegistries.BLOCK.get(parsed);
        require(blocks.add(block) && allBlocks.add(block), "Duplicate target block " + id);
        ids.add(id);
      }
      require(!blocks.isEmpty(), "Plant family has no target blocks " + family);
      targets.add(new Target(family, kind, Set.copyOf(blocks), Set.copyOf(ids)));
    }
    require(!targets.isEmpty(), "Plant plan has no targets");
    Map<String, Set<Block>> featureTrace = new TreeMap<>();
    if (root.has("feature_trace")) {
      require(rng == null, "Feature trace uses explicit planned chunks without RNG expansion");
      JsonObject trace = object(root.get("feature_trace"), "feature_trace");
      require(!trace.isEmpty(), "Feature trace map must not be empty");
      var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
      for (var entry : trace.entrySet()) {
        String id = entry.getKey();
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        Set<String> allowed = FeatureCommitObserver.ALLOWED.get(id);
        require(
            parsed != null
                && id.equals(parsed.toString())
                && placed.containsKey(parsed)
                && allowed != null,
            "Unapproved or unregistered trace placed feature " + id);
        Set<Block> blocks = new HashSet<>();
        for (JsonElement value : array(entry.getValue(), "trace blocks")) {
          String blockId = string(value, "trace block");
          ResourceLocation blockKey = ResourceLocation.tryParse(blockId);
          require(
              blockKey != null
                  && blockId.equals(blockKey.toString())
                  && BuiltInRegistries.BLOCK.containsKey(blockKey)
                  && allowed.contains(blockId),
              "Unapproved or unregistered trace block " + blockId);
          Block block = BuiltInRegistries.BLOCK.get(blockKey);
          require(
              allBlocks.contains(block) && blocks.add(block),
              "Trace block must be a unique plant target " + blockId);
        }
        require(!blocks.isEmpty(), "Trace feature has no target blocks " + id);
        featureTrace.put(id, Set.copyOf(blocks));
      }
    }
    Set<Long> seeds = new HashSet<>();
    List<PlannedChunk> selectedChunks = null;
    Set<String> selectedExpected = null;
    for (JsonElement element : array(root.get("worlds"), "worlds")) {
      JsonObject world = object(element, "world");
      long seed = number(world.get("seed"), "seed");
      require(seeds.add(seed), "Duplicate plant host seed " + seed);
      Set<String> expected = new TreeSet<>();
      for (JsonElement value : array(world.get("expected_families"), "expected_families")) {
        String family = string(value, "expected family");
        require(
            families.contains(family) && expected.add(family), "Invalid expected family " + family);
      }
      require(!expected.isEmpty(), "Plant world has no expected families");
      List<PlannedChunk> chunks = new ArrayList<>();
      Set<ChunkPos> positions = new HashSet<>();
      for (JsonElement value : array(world.get("chunks"), "chunks")) {
        JsonObject chunk = object(value, "chunk");
        long x = number(chunk.get("x"), "chunk.x");
        long z = number(chunk.get("z"), "chunk.z");
        require(
            x >= -1_875_000 && x < 1_875_000 && z >= -1_875_000 && z < 1_875_000,
            "Plant chunk outside native world bounds");
        ChunkPos position = new ChunkPos((int) x, (int) z);
        require(positions.add(position), "Duplicate plant chunk " + position);
        chunks.add(new PlannedChunk(position));
      }
      require(!chunks.isEmpty() && chunks.size() <= 4096, "Invalid plant chunk budget");
      if (seed == level.getSeed()) {
        selectedChunks = List.copyOf(chunks);
        selectedExpected = Set.copyOf(expected);
      }
    }
    require(selectedChunks != null, "Plant plan has no actual host seed " + level.getSeed());
    JsonObject manifest =
        root.has("input_manifest")
            ? object(root.get("input_manifest"), "input_manifest")
            : new JsonObject();
    return new Plan(
        List.copyOf(targets),
        selectedChunks,
        selectedExpected,
        columns,
        deadline,
        surfaceSurvey,
        rng,
        Map.copyOf(featureTrace),
        manifest.deepCopy());
  }

  private static final class Probe {
    private final ServerLevel level;
    private final NoiseBasedChunkGenerator generator;
    private final BiomeSource source;
    private final RandomState random;
    private final Path input;
    private final Path output;
    private final Plan plan;
    private final long started = System.nanoTime();
    private final Map<Block, Target> byBlock = new IdentityHashMap<>();
    private final TreeMap<String, JsonObject> witnesses = new TreeMap<>();
    private final TreeMap<String, Long> blockCounts = new TreeMap<>();
    private final JsonArray completed = new JsonArray();
    private final JsonArray rejected = new JsonArray();
    private final JsonObject manifest;
    private final JsonArray surfaces = new JsonArray();
    private final RngSearch rngSearch;
    private FeatureCommitObserver.Session traceSession;
    private CompletableFuture<ChunkResult<ChunkAccess>> pending;
    private ChunkPos ticket;
    private ChunkAccess current;
    private List<StructureStart> structures = List.of();
    private JsonArray structureEnvelopes = new JsonArray();
    private boolean unverifiedStructureNeighborhood;
    private List<Integer> activeSections = List.of();
    private int index;
    private int column;
    private long cells;
    private long mismatchCount;
    private long structureCount;
    private long nextProgress;
    private boolean finished;
    private String termination = "running";

    private Probe(ServerLevel level, Path input, Path output, Plan plan)
        throws IOException, ReflectiveOperationException {
      this.level = level;
      this.generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
      this.source = generator.getBiomeSource();
      this.random = level.getChunkSource().randomState();
      this.input = input;
      this.output = output;
      this.plan = plan;
      for (Target target : plan.targets())
        for (Block block : target.blocks()) byBlock.put(block, target);
      this.manifest = inputManifest(input, plan);
      this.rngSearch = plan.rng() == null ? null : new RngSearch(this, plan.rng());
    }

    private void armTrace() {
      if (plan.featureTrace().isEmpty()) return;
      Set<Long> chunks = new HashSet<>();
      for (PlannedChunk chunk : plan.chunks()) chunks.add(chunk.position().toLong());
      traceSession = FeatureCommitObserver.arm(level, plan.featureTrace(), chunks);
    }

    private void disarmTrace() {
      if (traceSession != null) FeatureCommitObserver.disarm(traceSession);
    }

    private void checkSampler() {
      IslandBiomeSampler sampler = (IslandBiomeSampler) (Object) random.sampler();
      require(
          !sampler.aeroscapesReimagine$deferTerrainMask()
              && sampler.aeroscapesReimagine$terrainMask() != null,
          "Plant probe needs the normal caller sampler with its terrain mask");
      require(
          level.getChunkSource().getGenerator() == generator
              && generator.getBiomeSource() == source,
          "Plant generator/source changed");
      if (rngSearch != null) rngSearch.checkIndex();
    }

    private void tick(ServerTickEvent.Post event) {
      if (finished || event.getServer() != level.getServer()) return;
      try {
        checkSampler();
        if (System.nanoTime() - started >= plan.deadlineSeconds() * 1_000_000_000L) {
          finish("finite_miss", "deadline_exhausted");
          return;
        }
        advance();
        checkSampler();
        if (!finished && System.nanoTime() >= nextProgress) {
          nextProgress = System.nanoTime() + 15_000_000_000L;
          LogUtils.getLogger()
              .info(
                  "Plant FULL coverage progress: seed={} chunks={}/{} target_blocks={}",
                  level.getSeed(),
                  index,
                  plan.chunks().size(),
                  witnesses.size());
        }
      } catch (RuntimeException failure) {
        if (!finished) {
          finished = true;
          disarmTrace();
          release();
          write("error", failure.toString());
        }
        throw failure;
      }
    }

    private void advance() {
      if ((rngSearch == null && index == plan.chunks().size())
          || (rngSearch != null && rngSearch.exhausted)) {
        String status = missingVariants().isEmpty() ? "complete" : "finite_miss";
        finish(status, rngSearch == null ? "planned_chunks_completed" : rngSearch.termination);
        return;
      }
      if (current != null) {
        for (int offset = 0; offset < plan.columnsPerTick() && column < 256; offset++) {
          scanColumn(column++);
        }
        if (column == 256) {
          JsonObject result = new JsonObject();
          result.addProperty("x", current.getPos().x);
          result.addProperty("z", current.getPos().z);
          result.addProperty("target_palette_sections", activeSections.size());
          result.addProperty("columns_scanned", column);
          result.add("structure_exclusion_envelopes", structureEnvelopes.deepCopy());
          result.addProperty("unverified_structure_neighborhood", unverifiedStructureNeighborhood);
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
                .orElseThrow(() -> new IllegalStateException("Plant FULL chunk unavailable"));
        pending = null;
        require(
            current.getPos().equals(ticket) && current.getPersistedStatus() == ChunkStatus.FULL,
            "Plant probe did not receive the requested actual FULL chunk");
        prepareStructureFilter();
        List<Integer> sections = new ArrayList<>();
        for (int section = 0; section < current.getSectionsCount(); section++) {
          if (current.getSection(section).maybeHas(state -> byBlock.containsKey(state.getBlock())))
            sections.add(section);
        }
        activeSections = List.copyOf(sections);
        if (rngSearch != null) rngSearch.noteFull(current);
        column = 0;
        return;
      }
      if (rngSearch == null) ticket = plan.chunks().get(index).position();
      else {
        ticket = rngSearch.next();
        if (ticket == null) return;
      }
      level.getChunkSource().addRegionTicket(PLANT_TICKET, ticket, 0, ticket);
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

    private void prepareStructureFilter() {
      TreeMap<String, StructureStart> starts = new TreeMap<>();
      for (int dx = -STRUCTURE_REFERENCE_RADIUS; dx <= STRUCTURE_REFERENCE_RADIUS; dx++) {
        for (int dz = -STRUCTURE_REFERENCE_RADIUS; dz <= STRUCTURE_REFERENCE_RADIUS; dz++) {
          ChunkPos nearby = new ChunkPos(current.getPos().x + dx, current.getPos().z + dz);
          for (StructureStart start :
              level.structureManager().startsForStructure(nearby, structure -> true)) {
            String id =
                level
                    .registryAccess()
                    .registryOrThrow(Registries.STRUCTURE)
                    .getKey(start.getStructure())
                    .toString();
            starts.put(id + "@" + start.getChunkPos(), start);
          }
        }
      }
      structures = List.copyOf(starts.values());
      structureEnvelopes = new JsonArray();
      unverifiedStructureNeighborhood = false;
      for (var entry : starts.entrySet()) {
        StructureStart start = entry.getValue();
        if (start.getPieces().isEmpty()) continue;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int featurePools = 0;
        boolean unverified = false;
        Set<String> poolTypes = new TreeSet<>();
        for (var piece : start.getPieces()) {
          var box = piece.getBoundingBox();
          minX = Math.min(minX, box.minX());
          minZ = Math.min(minZ, box.minZ());
          maxX = Math.max(maxX, box.maxX());
          maxZ = Math.max(maxZ, box.maxZ());
          if (piece instanceof PoolElementStructurePiece pool) {
            var element = pool.getElement();
            poolTypes.add(element.getClass().getName());
            if (element instanceof FeaturePoolElement) featurePools++;
            if (element instanceof ListPoolElement
                || !(element instanceof FeaturePoolElement
                    || element instanceof SinglePoolElement
                    || element instanceof EmptyPoolElement)) unverified = true;
          }
        }
        JsonObject envelope = new JsonObject();
        envelope.addProperty("start", entry.getKey());
        envelope.addProperty("min_x", minX - STRUCTURE_SPILL_MARGIN);
        envelope.addProperty("max_x", maxX + STRUCTURE_SPILL_MARGIN);
        envelope.addProperty("min_z", minZ - STRUCTURE_SPILL_MARGIN);
        envelope.addProperty("max_z", maxZ + STRUCTURE_SPILL_MARGIN);
        envelope.addProperty("vertical_scope", "entire_build_height");
        envelope.addProperty("feature_pool_pieces", featurePools);
        envelope.add("pool_element_classes", strings(poolTypes));
        envelope.addProperty("unknown_or_nested_pool_element", unverified);
        structureEnvelopes.add(envelope);
        unverifiedStructureNeighborhood |= unverified;
      }
    }

    private void scanColumn(int selected) {
      int x = current.getPos().getMinBlockX() + selected % 16;
      int z = current.getPos().getMinBlockZ() + selected / 16;
      if (plan.surfaceSurvey()) surveyColumn(x, z);
      BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
      for (int section : activeSections) {
        int minimum = current.getSectionYFromSectionIndex(section) << 4;
        for (int y = minimum; y < minimum + 16; y++) {
          cells++;
          position.set(x, y, z);
          BlockState state = current.getBlockState(position);
          Target target = byBlock.get(state.getBlock());
          if (target == null) continue;
          String id = blockId(state);
          blockCounts.merge(id, 1L, Long::sum);
          if (witnesses.containsKey(id)) continue;
          observe(target, state, position.immutable());
        }
      }
    }

    private void surveyColumn(int x, int z) {
      JsonObject columnData = new JsonObject();
      columnData.addProperty("x", x);
      columnData.addProperty("z", z);
      JsonObject heights = new JsonObject();
      JsonArray origins = new JsonArray();
      Set<Integer> seen = new HashSet<>();
      for (Heightmap.Types type :
          List.of(
              Heightmap.Types.MOTION_BLOCKING,
              Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
              Heightmap.Types.OCEAN_FLOOR,
              Heightmap.Types.WORLD_SURFACE)) {
        int y = current.getHeight(type, x, z) + 1;
        heights.addProperty(type.name(), y);
        if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight() || !seen.add(y))
          continue;
        BlockPos origin = new BlockPos(x, y, z);
        BlockPos floor = origin.below();
        JsonObject row = new JsonObject();
        row.addProperty("y", y);
        row.addProperty("origin_state", current.getBlockState(origin).toString());
        row.addProperty("support_state", current.getBlockState(floor).toString());
        row.addProperty(
            "level_biome_at_origin",
            level.getBiome(origin).unwrapKey().orElseThrow().location().toString());
        row.addProperty(
            "stored_at_origin",
            current
                .getNoiseBiome(x >> 2, y >> 2, z >> 2)
                .unwrapKey()
                .orElseThrow()
                .location()
                .toString());
        row.addProperty(
            "source_at_origin",
            source
                .getNoiseBiome(x >> 2, y >> 2, z >> 2, random.sampler())
                .unwrapKey()
                .orElseThrow()
                .location()
                .toString());
        row.addProperty("sky_visible", level.canSeeSky(origin));
        row.addProperty("air_at_origin", current.getBlockState(origin).isAir());
        row.addProperty("origin_fluid_empty", current.getFluidState(origin).isEmpty());
        if (BuiltInRegistries.BLOCK.containsKey(
                ResourceLocation.parse("natures_spirit:red_moss_block"))
            && current
                .getBlockState(floor)
                .is(
                    BuiltInRegistries.BLOCK.get(
                        ResourceLocation.parse("natures_spirit:red_moss_block")))) {
          var vegetation =
              level
                  .registryAccess()
                  .registryOrThrow(Registries.CONFIGURED_FEATURE)
                  .get(ResourceLocation.parse("natures_spirit:patch_tundra_red_vegetation"));
          require(
              vegetation != null && vegetation.config() instanceof RandomPatchConfiguration,
              "Unexpected native tundra patch configuration");
          var patch = (RandomPatchConfiguration) vegetation.config();
          var child = patch.feature().value().feature().value().config();
          require(
              child instanceof SimpleBlockConfiguration
                  && ((SimpleBlockConfiguration) child).toPlace() instanceof NoiseProvider,
              "Unexpected native tundra spatial state provider");
          var provider = (NoiseProvider) ((SimpleBlockConfiguration) child).toPlace();
          row.addProperty(
              "native_spatial_provider_state_at_origin",
              provider.getState(RandomSource.create(0L), origin).toString());
          row.addProperty("provider_sample_is_search_hint_not_placement", true);
        }
        JsonObject fluids = new JsonObject();
        fluid(fluids, "origin", origin);
        fluid(fluids, "support", floor);
        for (Direction direction : Direction.Plane.HORIZONTAL)
          fluid(fluids, direction.getName() + "_at_support_y", floor.relative(direction));
        row.add("fluids", fluids);
        Block rice =
            BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farmersdelight:wild_rice"));
        row.addProperty(
            "native_rice_top_water_habitat",
            current.getBlockState(floor).is(Blocks.WATER)
                && current.getBlockState(origin).isAir()
                && rice.defaultBlockState().canSurvive(level, floor));
        origins.add(row);
      }
      if (!origins.isEmpty()) {
        columnData.add("heightmaps", heights);
        columnData.add("origins", origins);
        surfaces.add(columnData);
      }
    }

    private void observe(Target target, BlockState state, BlockPos position) {
      String stored =
          current
              .getNoiseBiome(position.getX() >> 2, position.getY() >> 2, position.getZ() >> 2)
              .unwrapKey()
              .orElseThrow()
              .location()
              .toString();
      String selected =
          source
              .getNoiseBiome(
                  position.getX() >> 2,
                  position.getY() >> 2,
                  position.getZ() >> 2,
                  random.sampler())
              .unwrapKey()
              .orElseThrow()
              .location()
              .toString();
      Set<String> containing = new TreeSet<>();
      for (StructureStart start : structures) {
        if (start.getPieces().stream()
            .anyMatch(piece -> piece.getBoundingBox().isInside(position))) {
          containing.add(
              level
                  .registryAccess()
                  .registryOrThrow(Registries.STRUCTURE)
                  .getKey(start.getStructure())
                  .toString());
        }
      }
      Set<String> exclusions = new TreeSet<>();
      for (JsonElement element : structureEnvelopes) {
        JsonObject envelope = element.getAsJsonObject();
        if (position.getX() >= envelope.get("min_x").getAsInt()
            && position.getX() <= envelope.get("max_x").getAsInt()
            && position.getZ() >= envelope.get("min_z").getAsInt()
            && position.getZ() <= envelope.get("max_z").getAsInt())
          exclusions.add(envelope.get("start").getAsString());
      }
      if (unverifiedStructureNeighborhood)
        exclusions.add("unverified_unknown_or_nested_pool_element_in_reference_neighborhood");
      JsonObject witness = new JsonObject();
      witness.addProperty("family", target.family());
      witness.addProperty("kind", target.kind());
      witness.addProperty("block", blockId(state));
      witness.addProperty("state", state.toString());
      witness.addProperty("x", position.getX());
      witness.addProperty("y", position.getY());
      witness.addProperty("z", position.getZ());
      witness.addProperty("stored_biome", stored);
      witness.addProperty("normal_source_biome", selected);
      witness.add("containing_structure_pieces", strings(containing));
      witness.add("structure_spill_exclusions", strings(exclusions));
      witness.addProperty("below", level.getBlockState(position.below()).toString());
      witness.addProperty("above", level.getBlockState(position.above()).toString());
      witness.addProperty("raw_brightness_observed", level.getRawBrightness(position, 0));
      JsonObject fluids = new JsonObject();
      fluid(fluids, "self", position);
      for (Direction direction : Direction.values())
        fluid(fluids, direction.getName(), position.relative(direction));
      witness.add("neighbor_fluids", fluids);
      JsonObject trace = traceSession == null ? null : traceSession.evidence(position, state);
      if (trace != null) witness.add("source_feature_commit", trace);
      witness.addProperty("structure_geometry_excluded", !exclusions.isEmpty());
      if (!stored.equals(selected)
          || (!exclusions.isEmpty() && !target.kind().equals("cultivated") && trace == null)) {
        if (!stored.equals(selected)) mismatchCount++;
        if (!exclusions.isEmpty()) structureCount++;
        if (rejected.size() < 32) rejected.add(witness);
        return;
      }
      witness.addProperty(
          "proof_kind",
          trace != null
              ? "source_feature_commit"
              : target.kind().equals("cultivated")
                  ? "cultivated_block_observation"
                  : "outside_structure_spill_envelopes");
      witnesses.put(blockId(state), witness);
    }

    private void fluid(JsonObject fluids, String name, BlockPos position) {
      JsonObject entry = new JsonObject();
      ChunkAccess neighbor =
          current.getPos().equals(new ChunkPos(position))
              ? current
              : level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
      if (neighbor == null) {
        entry.addProperty("status", "neighbor_not_loaded_no_chunk_request");
        fluids.add(name, entry);
        return;
      }
      var state = neighbor.getFluidState(position);
      entry.addProperty("type", BuiltInRegistries.FLUID.getKey(state.getType()).toString());
      entry.addProperty("amount", state.getAmount());
      entry.addProperty("source", state.isSource());
      fluids.add(name, entry);
    }

    private Set<String> missingFamilies() {
      Set<String> missing = new TreeSet<>(plan.expected());
      for (JsonObject witness : witnesses.values())
        missing.remove(witness.get("family").getAsString());
      return missing;
    }

    private Set<String> missingVariants() {
      Set<String> missing = new TreeSet<>();
      for (Target target : plan.targets()) {
        if (plan.expected().contains(target.family())) missing.addAll(target.ids());
      }
      missing.removeAll(witnesses.keySet());
      return missing;
    }

    private void release() {
      if (ticket != null) {
        level.getChunkSource().removeRegionTicket(PLANT_TICKET, ticket, 0, ticket);
        ticket = null;
      }
      if (pending != null) {
        pending.cancel(false);
        pending = null;
      }
      current = null;
      activeSections = List.of();
      structures = List.of();
    }

    private void finish(String status, String reason) {
      finished = true;
      termination = reason;
      disarmTrace();
      if (traceSession != null) {
        var iterator = witnesses.entrySet().iterator();
        while (iterator.hasNext()) {
          var entry = iterator.next();
          JsonObject witness = entry.getValue();
          if (!witness.has("source_feature_commit")) continue;
          BlockPos position =
              new BlockPos(
                  witness.get("x").getAsInt(),
                  witness.get("y").getAsInt(),
                  witness.get("z").getAsInt());
          Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(entry.getKey()));
          if (traceSession.valid(position, block)) continue;
          witness.remove("source_feature_commit");
          witness.addProperty("trace_invalidated", true);
          if (witness.get("structure_geometry_excluded").getAsBoolean()
              && !witness.get("kind").getAsString().equals("cultivated")) {
            witness.addProperty("proof_kind", "invalidated_source_feature_commit");
            structureCount++;
            if (rejected.size() < 32) rejected.add(witness);
            iterator.remove();
          } else
            witness.addProperty(
                "proof_kind",
                witness.get("kind").getAsString().equals("cultivated")
                    ? "cultivated_block_observation"
                    : "outside_structure_spill_envelopes");
        }
        if (!missingVariants().isEmpty()) status = "finite_miss";
      }
      release();
      write(status, null);
      LogUtils.getLogger()
          .info(
              "Plant FULL coverage regression completed: seed={} status={} chunks={} witnesses={}"
                  + " output={}",
              level.getSeed(),
              status,
              index,
              witnesses.size(),
              output);
    }

    private void stopping(ServerStoppingEvent event) {
      if (finished || event.getServer() != level.getServer()) return;
      finished = true;
      disarmTrace();
      release();
      write("aborted", "Server stopped before plant probe completion");
    }

    private void write(String status, String failure) {
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("status", status);
      report.addProperty("termination", termination);
      JsonObject filter = new JsonObject();
      filter.addProperty("version", 2);
      filter.addProperty("overall_piece_envelope_margin_blocks", STRUCTURE_SPILL_MARGIN);
      filter.addProperty("reference_neighborhood_radius_chunks", STRUCTURE_REFERENCE_RADIUS);
      filter.addProperty("vertical_scope", "entire_build_height");
      filter.addProperty(
          "unknown_nested_policy",
          "nearby unknown or ListPoolElement makes wild witnesses unverified");
      filter.addProperty(
          "bound",
          "Pinned FEATURES WorldGenRegion blockStateWriteRadius=1: at most31blocks from an"
              + " intersecting source chunk anchor;32block envelope is conservative. Full vertical"
              + " exclusion includes tree output. All starts from the corresponding5x5 REFERENCES"
              + " neighborhood are deduplicated.");
      report.add("structure_filter", filter);
      if (traceSession != null) report.add("feature_commit_trace", traceSession.report());
      report.addProperty("seed", level.getSeed());
      report.addProperty("plan", input.toString());
      report.addProperty("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
      report.addProperty("deadline_seconds", plan.deadlineSeconds());
      report.addProperty("columns_per_tick", plan.columnsPerTick());
      report.addProperty("generator", generator.getClass().getName());
      report.addProperty("biome_source", source.getClass().getName());
      report.addProperty("min_y", level.getMinBuildHeight());
      report.addProperty("max_y_exclusive", level.getMaxBuildHeight());
      report.add("input_manifest", manifest.deepCopy());
      report.add("expected_families", strings(plan.expected()));
      report.add("finite_missing_families", strings(missingFamilies()));
      report.add("finite_missing_block_variants", strings(missingVariants()));
      JsonArray variants = new JsonArray();
      for (Target target : plan.targets()) {
        JsonObject row = new JsonObject();
        row.addProperty("family", target.family());
        row.addProperty("kind", target.kind());
        row.add("target_blocks", strings(target.ids()));
        Set<String> missing = new TreeSet<>(target.ids());
        missing.removeAll(witnesses.keySet());
        row.add("finite_missing_block_variants", strings(missing));
        variants.add(row);
      }
      report.add("target_variants", variants);
      JsonArray observed = new JsonArray();
      witnesses.values().forEach(observed::add);
      report.add("natural_witnesses", observed);
      JsonObject counts = new JsonObject();
      blockCounts.forEach(counts::addProperty);
      report.add("all_observed_target_block_counts", counts);
      report.add("completed_FULL_chunks", completed);
      report.addProperty("planned_chunk_count", plan.chunks().size());
      report.addProperty("surface_survey_enabled", plan.surfaceSurvey());
      report.add("actual_FULL_heightmap_surfaces", surfaces);
      if (rngSearch != null) report.add("fd_native_rng_search", rngSearch.report());
      report.addProperty("target_palette_cells_examined", cells);
      report.addProperty("source_label_mismatch_candidates", mismatchCount);
      report.addProperty("structure_contained_candidates", structureCount);
      report.add("rejected_witness_examples", rejected);
      report.addProperty("ticket_active", ticket != null);
      IslandBiomeSampler sampler = (IslandBiomeSampler) (Object) random.sampler();
      report.addProperty("caller_sampler_defer", sampler.aeroscapesReimagine$deferTerrainMask());
      report.addProperty(
          "caller_sampler_mask_present", sampler.aeroscapesReimagine$terrainMask() != null);
      if (failure != null) report.addProperty("failure", failure);
      report.addProperty(
          "limits",
          "Finite ordinary FULL chunk search, no feature/crop placement or rarity changes. All"
              + " matching block counts include structure/biome-ambiguous candidates; only the"
              + " separate natural witnesses qualify. Family availability and every block variant"
              + " are distinct. A finite miss is not global absence. Source/stored mismatches and"
              + " structure-contained wild candidates without a revalidated source_feature_commit"
              + " do not qualify. Plant-local feature eligibility is not required: native patch"
              + " spillover across biome boundaries is allowed. Native generation may exceed one"
              + " tick; scan work is sliced by complete XZ columns.");
      PlantCoverageRegression.write(output, report);
    }
  }

  private record RngFeature(String id, PlacedFeature feature, int index, JsonElement definition) {}

  private static final class RngSearch {
    private final Probe owner;
    private final RngPlan plan;
    private final int step = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
    private final List<FeatureSorter.StepFeatureData> steps;
    private final List<RngFeature> features = new ArrayList<>();
    private final Set<Long> seen = new HashSet<>();
    private final Set<Long> selected = new HashSet<>();
    private final JsonArray candidates = new JsonArray();
    private final TreeMap<String, Long> attempts = new TreeMap<>();
    private int radius;
    private int perimeter;
    private int center;
    private int feature;
    private int enumerated;
    private int rawColumns;
    private int fullChunks;
    private ChunkPos chunk;
    private BlockPos point;
    private RngFeature pendingFeature;
    private JsonObject pendingEvidence;
    private JsonObject activeFullEvidence;
    private RngFeature activeFullFeature;
    private BlockPos activeFullPoint;
    private boolean exhausted;
    private String termination = "running";

    private RngSearch(Probe owner, RngPlan plan) {
      this.owner = owner;
      this.plan = plan;
      this.steps =
          ((FeatureIndexAccess) owner.generator).aeroscapesRegression$featuresPerStep().get();
      var registry = owner.level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
      for (String id : plan.features()) {
        PlacedFeature placed = registry.get(ResourceLocation.parse(id));
        require(placed != null, "Missing actual FD placed feature " + id);
        int index = steps.get(step).indexMapping().applyAsInt(placed);
        require(
            index >= 0
                && index < steps.get(step).features().size()
                && steps.get(step).features().get(index).equals(placed),
            "FD feature has no native index " + id);
        var modifiers = placed.placement();
        require(
            modifiers.size() == 5
                && modifiers.get(0) instanceof RarityFilter
                && modifiers.get(1) instanceof InSquarePlacement
                && modifiers.get(2) instanceof HeightmapPlacement
                && modifiers.get(3) instanceof BiomeFilter,
            "Unexpected FD placement prefix/order " + id);
        JsonElement definition =
            PlacedFeature.DIRECT_CODEC
                .encodeStart(
                    RegistryOps.create(JsonOps.INSTANCE, owner.level.registryAccess()), placed)
                .result()
                .orElseThrow(
                    () -> new IllegalStateException("Cannot encode actual FD feature " + id));
        require(
            definition
                .getAsJsonObject()
                .getAsJsonArray("placement")
                .get(2)
                .getAsJsonObject()
                .get("heightmap")
                .getAsString()
                .equals("MOTION_BLOCKING"),
            "Unexpected FD heightmap " + id);
        features.add(new RngFeature(id, placed, index, definition));
      }
    }

    private void checkIndex() {
      require(
          ((FeatureIndexAccess) owner.generator).aeroscapesRegression$featuresPerStep().get()
              == steps,
          "Native FD feature-index table changed");
    }

    private ChunkPos next() {
      if (owner.missingVariants().isEmpty()) return finish("expected_variants_confirmed");
      if (fullChunks >= plan.fullBudget()) return finish("FULL_budget_exhausted");
      if (pendingEvidence != null) return filterRaw();
      for (int iteration = 0; iteration < 64; iteration++) {
        if (chunk == null) {
          if (enumerated >= plan.candidateBudget() || radius > plan.radius())
            return finish("finite_candidate_budget_exhausted");
          ChunkPos origin = owner.plan.chunks().get(center).position();
          int dx = 0;
          int dz = 0;
          if (radius > 0) {
            int width = 2 * radius;
            int side = perimeter / width;
            int offset = perimeter % width;
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
          ChunkPos proposed = new ChunkPos(origin.x + dx, origin.z + dz);
          if (++center == owner.plan.chunks().size()) {
            center = 0;
            if (++perimeter == (radius == 0 ? 1 : 8 * radius)) {
              perimeter = 0;
              radius++;
            }
          }
          if (proposed.x < -1_875_000
              || proposed.x >= 1_875_000
              || proposed.z < -1_875_000
              || proposed.z >= 1_875_000) continue;
          if (!seen.add(proposed.toLong())) continue;
          enumerated++;
          chunk = proposed;
          feature = 0;
        }
        RngFeature target = features.get(feature++);
        ChunkPos candidateChunk = chunk;
        if (feature == features.size()) chunk = null;
        attempts.merge(target.id(), 1L, Long::sum);
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        long decoration =
            random.setDecorationSeed(
                owner.level.getSeed(),
                candidateChunk.getMinBlockX(),
                candidateChunk.getMinBlockZ());
        random.setFeatureSeed(decoration, target.index(), step);
        PlacementContext context =
            new PlacementContext(owner.level, owner.generator, Optional.of(target.feature()));
        BlockPos origin =
            new BlockPos(
                candidateChunk.getMinBlockX(),
                owner.level.getMinBuildHeight(),
                candidateChunk.getMinBlockZ());
        List<BlockPos> rarity =
            target.feature().placement().getFirst().getPositions(context, random, origin).toList();
        if (rarity.isEmpty()) continue;
        require(rarity.size() == 1, "FD rarity emitted multiple positions");
        List<BlockPos> square =
            target
                .feature()
                .placement()
                .get(1)
                .getPositions(context, random, rarity.getFirst())
                .toList();
        require(square.size() == 1, "FD in_square emitted multiple positions");
        point = square.getFirst().immutable();
        pendingFeature = target;
        pendingEvidence = new JsonObject();
        pendingEvidence.addProperty("feature", target.id());
        pendingEvidence.addProperty("feature_index", target.index());
        pendingEvidence.addProperty("decoration_step", step);
        pendingEvidence.addProperty("decoration_seed", decoration);
        pendingEvidence.addProperty("feature_seed", decoration + target.index() + 10000L * step);
        pendingEvidence.addProperty("chunk_x", candidateChunk.x);
        pendingEvidence.addProperty("chunk_z", candidateChunk.z);
        pendingEvidence.addProperty("native_in_square_x", point.getX());
        pendingEvidence.addProperty("native_in_square_z", point.getZ());
        pendingEvidence.addProperty("native_rng_count_after_prefix", random.getCount());
        candidates.add(pendingEvidence);
        return null;
      }
      return null;
    }

    private ChunkPos filterRaw() {
      if (rawColumns >= plan.rawBudget()) return finish("raw_column_budget_exhausted");
      var column =
          owner.generator.getBaseColumn(point.getX(), point.getZ(), owner.level, owner.random);
      rawColumns++;
      int height = owner.level.getMinBuildHeight();
      for (int y = owner.level.getMaxBuildHeight() - 1; y >= owner.level.getMinBuildHeight(); y--) {
        if (Heightmap.Types.MOTION_BLOCKING.isOpaque().test(column.getBlock(y))) {
          height = y + 1;
          break;
        }
      }
      JsonObject evidence = pendingEvidence;
      pendingEvidence = null;
      evidence.addProperty("raw_motion_blocking_origin_y", height);
      evidence.addProperty("raw_support", column.getBlock(height - 1).toString());
      boolean eligible = false;
      JsonArray sourceSamples = new JsonArray();
      for (int offset : new int[] {-4, 0, 4}) {
        int y = height + offset;
        var biome =
            owner.source.getNoiseBiome(
                point.getX() >> 2, y >> 2, point.getZ() >> 2, owner.random.sampler());
        JsonObject sample = new JsonObject();
        sample.addProperty("y", y);
        sample.addProperty("biome", biome.unwrapKey().orElseThrow().location().toString());
        boolean attached =
            biome.value().getGenerationSettings().hasFeature(pendingFeature.feature());
        sample.addProperty("feature_attached", attached);
        eligible |= attached;
        sourceSamples.add(sample);
      }
      evidence.add("preliminary_normal_source_samples", sourceSamples);
      if (height <= owner.level.getMinBuildHeight() || !eligible) {
        evidence.addProperty("status", "preliminary_raw_or_source_filter_miss_not_absence");
        return null;
      }
      ChunkPos position = new ChunkPos(point);
      if (!selected.add(position.toLong())) {
        evidence.addProperty("status", "FULL_chunk_already_selected");
        return null;
      }
      fullChunks++;
      evidence.addProperty("status", "native_prefix_candidate_selected_for_normal_FULL");
      activeFullEvidence = evidence;
      activeFullFeature = pendingFeature;
      activeFullPoint = point;
      return position;
    }

    private void noteFull(ChunkAccess current) {
      require(
          activeFullEvidence != null && current.getPos().equals(new ChunkPos(activeFullPoint)),
          "Wrong native FD FULL candidate");
      int y =
          current.getHeight(
                  Heightmap.Types.MOTION_BLOCKING, activeFullPoint.getX(), activeFullPoint.getZ())
              + 1;
      BlockPos origin = new BlockPos(activeFullPoint.getX(), y, activeFullPoint.getZ());
      var biome = owner.level.getBiome(origin);
      JsonObject actual = new JsonObject();
      actual.addProperty("y", y);
      actual.addProperty("support_state", current.getBlockState(origin.below()).toString());
      actual.addProperty("origin_state", current.getBlockState(origin).toString());
      actual.addProperty("level_biome", biome.unwrapKey().orElseThrow().location().toString());
      actual.addProperty(
          "feature_attached",
          biome.value().getGenerationSettings().hasFeature(activeFullFeature.feature()));
      activeFullEvidence.add("actual_FULL_motion_blocking_origin", actual);
    }

    private ChunkPos finish(String reason) {
      exhausted = true;
      termination = reason;
      if (pendingEvidence != null) pendingEvidence.addProperty("status", reason);
      return null;
    }

    private JsonObject report() {
      JsonObject result = new JsonObject();
      result.addProperty("termination", termination);
      result.addProperty("enumerated_unique_chunks", enumerated);
      result.addProperty("raw_columns", rawColumns);
      result.addProperty("selected_FULL_chunks", fullChunks);
      result.addProperty("candidate_budget", plan.candidateBudget());
      result.addProperty("raw_budget", plan.rawBudget());
      result.addProperty("FULL_budget", plan.fullBudget());
      JsonObject counts = new JsonObject();
      attempts.forEach(counts::addProperty);
      result.add("native_rarity_attempts", counts);
      JsonArray definitions = new JsonArray();
      for (RngFeature feature : features) {
        JsonObject row = new JsonObject();
        row.addProperty("id", feature.id());
        row.addProperty("actual_feature_index", feature.index());
        row.addProperty("actual_decoration_step", step);
        row.add("actual_definition", feature.definition());
        definitions.add(row);
      }
      result.add("actual_features", definitions);
      result.add("prefix_pass_candidates", candidates);
      result.addProperty(
          "limits",
          "Only actual native rarity/in_square modifiers run on an independent native decoration"
              + " RNG copy. No configured feature, heightmap modifier or placement is invoked."
              + " Source/raw filters are preliminary, including ±4 source samples for recall, not"
              + " actual FULL habitat proof. Separate natural block witnesses qualify. Candidate"
              + " selection changes the finite sample, never native chances, source or generation"
              + " RNG.");
      return result;
    }
  }

  private static JsonObject inputManifest(Path input, Plan plan)
      throws IOException, ReflectiveOperationException {
    JsonObject manifest = new JsonObject();
    manifest.addProperty("plan_sha256", sha256(input));
    manifest.add("controller", plan.controllerManifest().deepCopy());
    JsonObject artifacts = new JsonObject();
    for (String id :
        List.of(
            "aeroscapes_reimagine",
            "aeroscapes_regression",
            "farmersdelight",
            "fungidelight",
            "natures_spirit",
            "quark",
            "galosphere",
            "supplementaries",
            "natures_delight")) {
      var info = ModList.get().getModFileById(id);
      require(info != null, "Missing installed plant audit dependency " + id);
      Path path = info.getFile().getFilePath().toAbsolutePath().normalize();
      JsonObject artifact = new JsonObject();
      artifact.addProperty("path", path.toString());
      artifact.addProperty("sha256", sha256(path));
      artifacts.add(id, artifact);
    }
    manifest.add("actual_loaded_artifacts", artifacts);
    JsonObject values = new JsonObject();
    Class<?> configuration = Class.forName("net.mehdinoui.fungidelight.ModConfiguration");
    for (String key :
        List.of(
            "huge_inky_cap",
            "huge_morel",
            "inky_cap_normal",
            "morel_normal",
            "inky_cap_common",
            "morel_common",
            "inky_cap_rare",
            "morel_rare",
            "inky_cap_colony",
            "morel_colony",
            "rooted_dirt_blob",
            "truffle_dirt_ore")) {
      Object value = configuration.getField("CHANCE_" + key.toUpperCase(Locale.ROOT)).get(null);
      require(value instanceof ModConfigSpec.IntValue, "Unexpected Fungi rarity value " + key);
      ModConfigSpec.IntValue configured = (ModConfigSpec.IntValue) value;
      JsonObject entry = new JsonObject();
      entry.addProperty("config_path", String.join(".", configured.getPath()));
      entry.addProperty("actual_value", configured.get());
      entry.addProperty("default_value", configured.getDefault());
      values.add(key, entry);
    }
    manifest.add("actual_fungi_configurable_rarity", values);
    manifest.add(
        "actual_farmer_config",
        configFields(
            "vectorwing.farmersdelight.common.Configuration",
            List.of(
                "CHANCE_WILD_CABBAGES",
                "CHANCE_WILD_BEETROOTS",
                "CHANCE_WILD_CARROTS",
                "CHANCE_WILD_ONIONS",
                "CHANCE_WILD_POTATOES",
                "CHANCE_WILD_TOMATOES",
                "CHANCE_WILD_RICE",
                "CHANCE_BROWN_MUSHROOM_COLONIES",
                "CHANCE_RED_MUSHROOM_COLONIES",
                "GENERATE_BROWN_MUSHROOM_COLONIES",
                "GENERATE_RED_MUSHROOM_COLONIES",
                "GENERATE_VILLAGE_FARM_FD_CROPS",
                "GENERATE_FD_CHEST_LOOT")));
    manifest.add(
        "actual_supplementaries_config",
        configFields(
            "net.mehvahdjukaar.supplementaries.configs.CommonConfigs$Functional",
            List.of("FLAX_ENABLED", "WILD_FLAX_ENABLED")));
    manifest.add("common_and_server_config_files", configFiles());
    return manifest;
  }

  private static JsonObject configFields(String owner, List<String> names)
      throws ReflectiveOperationException {
    Class<?> type = Class.forName(owner);
    JsonObject values = new JsonObject();
    for (String name : names) {
      Object field = type.getField(name).get(null);
      require(field instanceof Supplier<?>, "Unexpected plant config field " + owner + "." + name);
      Object actual = ((Supplier<?>) field).get();
      JsonObject entry = new JsonObject();
      if (actual instanceof Boolean flag) entry.addProperty("actual_value", flag);
      else if (actual instanceof Number number) entry.addProperty("actual_value", number);
      else throw new IllegalStateException("Unexpected plant config value " + owner + "." + name);
      if (field instanceof ModConfigSpec.ConfigValue<?> configured) {
        entry.addProperty("config_path", String.join(".", configured.getPath()));
      }
      values.add(name, entry);
    }
    return values;
  }

  private static JsonArray configFiles() throws IOException {
    List<String> providers =
        List.of(
            "farmersdelight",
            "fungidelight",
            "natures_spirit",
            "natures_delight",
            "supplementaries",
            "quark",
            "zeta",
            "galosphere");
    JsonArray files = new JsonArray();
    Set<Path> captured = new HashSet<>();
    for (String id : providers) {
      for (ModConfig config : ModConfigs.getModConfigs(id)) {
        if (config.getType() == ModConfig.Type.CLIENT) continue;
        JsonObject row = new JsonObject();
        row.addProperty("mod", id);
        row.addProperty("type", config.getType().toString());
        row.addProperty("loaded", config.getLoadedConfig() != null);
        Path path;
        try {
          path = config.getFullPath().toAbsolutePath().normalize();
        } catch (IllegalStateException unavailable) {
          row.addProperty("file_status", "no_loaded_filesystem_path");
          row.addProperty("reason", unavailable.toString());
          files.add(row);
          continue;
        }
        row.addProperty("path", path.toString());
        if (Files.isRegularFile(path)) {
          row.addProperty("sha256", sha256(path));
          captured.add(path);
        } else row.addProperty("file_status", "not_present");
        files.add(row);
      }
    }
    Path directory = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
    if (Files.isDirectory(directory)) {
      try (var stream = Files.walk(directory, 2)) {
        for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
          String relative = directory.relativize(path).toString().toLowerCase(Locale.ROOT);
          if (relative.contains("client") || captured.contains(path)) continue;
          String normalized = relative.replace("_", "").replace("-", "");
          if (providers.stream().noneMatch(id -> normalized.startsWith(id.replace("_", ""))))
            continue;
          JsonObject row = new JsonObject();
          row.addProperty("type", "provider_custom_config");
          row.addProperty("path", path.toString());
          row.addProperty("sha256", sha256(path));
          files.add(row);
        }
      }
    }
    return files;
  }

  private static String sha256(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var stream = Files.newInputStream(path)) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 unavailable", failure);
    }
  }

  private static int bounded(JsonObject root, String key, int fallback, int minimum, int maximum) {
    long value = root.has(key) ? number(root.get(key), key) : fallback;
    require(value >= minimum && value <= maximum, "Plant plan value out of bounds " + key);
    return (int) value;
  }

  private static JsonObject object(JsonElement element, String name) {
    require(element != null && element.isJsonObject(), "Expected object " + name);
    return element.getAsJsonObject();
  }

  private static JsonArray array(JsonElement element, String name) {
    require(element != null && element.isJsonArray(), "Expected array " + name);
    return element.getAsJsonArray();
  }

  private static String string(JsonElement element, String name) {
    require(
        element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString(),
        "Expected string " + name);
    return element.getAsString();
  }

  private static boolean bool(JsonElement element, String name) {
    require(
        element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean(),
        "Expected boolean " + name);
    return element.getAsBoolean();
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

  private static JsonArray strings(Set<String> values) {
    JsonArray result = new JsonArray();
    new TreeSet<>(values).forEach(result::add);
    return result;
  }

  private static String blockId(BlockState state) {
    return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
  }

  private static void write(Path output, JsonObject report) {
    try {
      Files.createDirectories(output.getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write plant FULL coverage " + output, failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
