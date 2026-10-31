package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.StructureBiomeCompatibility;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionStructure;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Pure procedural search evidence; ordinary native STARTS/FULL remains the placement proof. */
public final class SurfaceStructurePrefilter {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Set<String> SUPPORTED =
      Set.of("minecraft:mansion", "minecraft:desert_pyramid");

  private SurfaceStructurePrefilter() {}

  public static void start(ServerLevel level, Path planFile, Path reportFile) {
    if (!level.getServer().isDedicatedServer() || level.dimension() != Level.OVERWORLD)
      throw new IllegalArgumentException("Surface prefilter requires a dedicated Overworld");
    if (!level.getServer().getWorldData().worldGenOptions().generateStructures())
      throw new IllegalStateException("Surface prefilter requires generateStructures");
    Audit audit = new Audit(level, read(planFile), reportFile.toAbsolutePath());
    NeoForge.EVENT_BUS.addListener(audit.tickListener);
    NeoForge.EVENT_BUS.addListener(audit.stopListener);
    LOGGER.info(
        "Surface structure prefilter started: seed={} output={}", level.getSeed(), reportFile);
  }

  private record Center(String target, int x, int z) {}

  private record Pending(Search search, ChunkPos origin, BlockPos anchor, JsonObject row) {}

  private static final class Search {
    private final String id;
    private final String setId;
    private final Structure root;
    private final Structure shadow;
    private final RandomSpreadStructurePlacement placement;
    private final List<Center> centers;
    private final JsonObject definition;
    private final JsonObject setDefinition;
    private final List<StructureSet.StructureSelectionEntry> members;
    private final NativeRootForensics forensics;
    private final int minimumY;
    private final int maximumY;
    private final Set<Long> seen = new HashSet<>();
    private final Map<String, Integer> counts = new TreeMap<>();
    private final JsonArray rows = new JsonArray();
    private final List<ChunkPos> survivors = new ArrayList<>();
    private int radius;
    private int perimeter;
    private int center;
    private int enumerated;
    private int prefits;
    private boolean exhausted;

    private Search(
        ServerLevel level,
        String id,
        List<Center> allCenters,
        boolean nativeRoots,
        boolean rootForensics,
        int forensicCandidates) {
      this.id = id;
      root =
          level
              .registryAccess()
              .registryOrThrow(Registries.STRUCTURE)
              .get(ResourceLocation.parse(id));
      if (root == null
          || (!nativeRoots
              && !root.getClass()
                  .getName()
                  .equals("dev.worldgen.lithostitched.worldgen.structure.DelegatingStructure")))
        throw new IllegalStateException("Unexpected surface root class " + id);
      var operations = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
      definition =
          Structure.DIRECT_CODEC.encodeStart(operations, root).getOrThrow().getAsJsonObject();
      if (nativeRoots) {
        validateNativeRoot(definition);
        shadow = null;
        minimumY = Integer.MIN_VALUE;
        maximumY = Integer.MAX_VALUE;
      } else {
        JsonObject delegate = object(definition, "delegate");
        String expectedType =
            id.equals("minecraft:mansion")
                ? "aeroscapes_reimagine:island_mansion"
                : "aeroscapes_reimagine:island_pyramid";
        if (!text(definition, "type").equals("lithostitched:delegating")
            || !text(delegate, "type").equals(expectedType))
          throw new IllegalStateException("Unverified delegate context path " + id);
        JsonObject condition = object(definition, "spawn_condition");
        if (!text(condition, "type").equals("lithostitched:height_filter")
            || !text(condition, "range_type").equals("absolute")
            || condition.has("heightmap"))
          throw new IllegalStateException("Unverified surface placement condition " + condition);
        JsonArray range = array(condition, "permitted_range");
        if (range.size() != 2)
          throw new IllegalStateException("Unexpected absolute height interval");
        minimumY = range.get(0).getAsBigDecimal().intValueExact();
        maximumY = range.get(1).getAsBigDecimal().intValueExact();
        if (minimumY > maximumY)
          throw new IllegalStateException("Reversed absolute height interval");
        shadow =
            id.equals("minecraft:mansion")
                ? new WoodlandMansionStructure(root.getModifiedStructureSettings())
                : new DesertPyramidStructure(root.getModifiedStructureSettings());
      }
      forensics =
          rootForensics
              ? new NativeRootForensics(level, id, root, definition, forensicCandidates)
              : null;
      var matches =
          level.getChunkSource().getGeneratorState().possibleStructureSets().stream()
              .filter(
                  holder ->
                      holder.value().structures().stream()
                          .anyMatch(member -> member.structure().value() == root))
              .toList();
      if (matches.size() != 1)
        throw new IllegalStateException("Expected exactly one actual set for " + id);
      Holder<StructureSet> set = matches.getFirst();
      members = List.copyOf(set.value().structures());
      setId = set.unwrapKey().orElseThrow().location().toString();
      if (!(set.value().placement() instanceof RandomSpreadStructurePlacement spread))
        throw new IllegalStateException("Unexpected surface placement " + setId);
      placement = spread;
      setDefinition =
          StructureSet.DIRECT_CODEC
              .encodeStart(operations, set.value())
              .getOrThrow()
              .getAsJsonObject();
      if (!nativeRoots && object(setDefinition, "placement").has("exclusion_zone"))
        throw new IllegalStateException("Unverified exclusion-zone dependency " + setId);
      centers =
          allCenters.stream()
              .filter(point -> point.target == null || point.target.equals(id))
              .toList();
      if (centers.isEmpty()) throw new IllegalArgumentException("No centers for " + id);
    }

    private void count(String stage) {
      counts.merge(stage, 1, Integer::sum);
    }

    private ChunkPos next(ServerLevel level, int budget, int maximumRadius, int survivorBudget) {
      if (enumerated >= budget || radius > maximumRadius || survivors.size() >= survivorBudget) {
        exhausted = true;
        return null;
      }
      Center point = centers.get(center);
      int dx = 0;
      int dz = 0;
      if (radius > 0) {
        int width = radius * 2;
        int offset = perimeter % width;
        switch (perimeter / width) {
          case 0 -> {
            dx = -radius + offset;
            dz = -radius;
          }
          case 1 -> {
            dx = radius;
            dz = -radius + offset;
          }
          case 2 -> {
            dx = radius - offset;
            dz = radius;
          }
          default -> {
            dx = -radius;
            dz = radius - offset;
          }
        }
      }
      int regionX = Math.floorDiv(point.x >> 4, placement.spacing()) + dx;
      int regionZ = Math.floorDiv(point.z >> 4, placement.spacing()) + dz;
      ChunkPos origin =
          placement.getPotentialStructureChunk(
              level.getChunkSource().getGeneratorState().getLevelSeed(),
              regionX * placement.spacing(),
              regionZ * placement.spacing());
      if (++center == centers.size()) {
        center = 0;
        if (++perimeter == (radius == 0 ? 1 : radius * 8)) {
          perimeter = 0;
          radius++;
        }
      }
      if (!seen.add(origin.toLong())) {
        count("duplicate_origin");
        return null;
      }
      enumerated++;
      return origin;
    }
  }

  private static final class Audit {
    private final ServerLevel level;
    private final JsonObject plan;
    private final Path output;
    private final int budget;
    private final int radius;
    private final int prefitBudget;
    private final int survivorBudget;
    private final int seconds;
    private final boolean nativeRoots;
    private final boolean firstWeightedChoiceOnly;
    private final boolean nativeWeightedPrefix;
    private final boolean rootForensics;
    private final List<Search> searches = new ArrayList<>();
    private final Climate.Sampler sampler;
    private final Object mask;
    private final long started = System.nanoTime();
    private final Consumer<ServerTickEvent.Post> tickListener = this::tick;
    private final Consumer<ServerStoppingEvent> stopListener = this::stopping;
    private final JsonArray errors = new JsonArray();
    private Pending pending;
    private int searchIndex;
    private int prefits;
    private long maximumStep;
    private boolean done;

    private Audit(ServerLevel level, JsonObject plan, Path output) {
      this.level = level;
      this.plan = plan;
      this.output = output;
      if (number(plan, "seed").longValueExact() != level.getSeed())
        throw new IllegalArgumentException("Surface prefilter seed does not match loaded world");
      nativeRoots = plan.has("native_roots") && flag(plan, "native_roots");
      firstWeightedChoiceOnly =
          plan.has("first_weighted_choice_only") && flag(plan, "first_weighted_choice_only");
      if (firstWeightedChoiceOnly && !nativeRoots)
        throw new IllegalArgumentException("Weighted hints require native_roots");
      nativeWeightedPrefix =
          plan.has("native_weighted_prefix") && flag(plan, "native_weighted_prefix");
      if (nativeWeightedPrefix && (!nativeRoots || firstWeightedChoiceOnly))
        throw new IllegalArgumentException(
            "Native weighted fallback requires ordinary native roots");
      rootForensics = plan.has("root_forensics") && flag(plan, "root_forensics");
      if (rootForensics && (!nativeRoots || firstWeightedChoiceOnly))
        throw new IllegalArgumentException("Root forensics requires ordinary native roots");
      int forensicCandidates =
          rootForensics ? bounded(plan, "forensic_candidates_per_target", 512, 1, 8192) : 0;
      budget = bounded(plan, "candidates_per_target", 8192, 1, nativeRoots ? 65536 : 8192);
      radius = bounded(plan, "radius_regions", 128, 0, 128);
      prefitBudget = bounded(plan, "prefit_budget_total", 32, 0, nativeRoots ? 65536 : 32);
      survivorBudget = bounded(plan, "survivors_per_target", 4, 1, 16);
      seconds = bounded(plan, "deadline_seconds", 180, 1, nativeRoots ? 1800 : 180);
      List<Center> centers = new ArrayList<>();
      for (JsonElement value : array(plan, "centers")) {
        if (!value.isJsonObject()) throw new IllegalArgumentException("Center must be an object");
        JsonObject point = value.getAsJsonObject();
        String target = point.has("target") ? text(point, "target") : null;
        if (target != null && !nativeRoots && !SUPPORTED.contains(target))
          throw new IllegalArgumentException("Unsupported center target " + target);
        int x = number(point, "x").intValueExact();
        int z = number(point, "z").intValueExact();
        if (Math.abs((long) x) > 10_000_000 || Math.abs((long) z) > 10_000_000)
          throw new IllegalArgumentException("Center exceeds bounded coordinate range");
        centers.add(new Center(target, x, z));
      }
      if (centers.isEmpty() || centers.size() > (nativeRoots ? 65536 : 64))
        throw new IllegalArgumentException("Invalid bounded center count");
      Set<String> targets = new HashSet<>();
      for (JsonElement value : array(plan, "targets")) {
        if (!value.isJsonPrimitive()
            || !value.getAsJsonPrimitive().isString()
            || (!nativeRoots && !SUPPORTED.contains(value.getAsString()))
            || !targets.add(value.getAsString()))
          throw new IllegalArgumentException("Expected distinct supported target IDs");
      }
      if (targets.isEmpty()) throw new IllegalArgumentException("No surface targets");
      targets.stream()
          .sorted()
          .forEach(
              id ->
                  searches.add(
                      new Search(
                          level, id, centers, nativeRoots, rootForensics, forensicCandidates)));
      sampler = level.getChunkSource().randomState().sampler();
      var context = (IslandBiomeSampler) (Object) sampler;
      mask = context.aeroscapesReimagine$terrainMask();
      if (mask == null || context.aeroscapesReimagine$deferTerrainMask())
        throw new IllegalStateException("Surface prefilter needs the normal bound sampler");
    }

    private Structure.GenerationContext context(
        ChunkPos origin, Predicate<Holder<Biome>> predicate) {
      var context =
          new Structure.GenerationContext(
              level.registryAccess(),
              level.getChunkSource().getGenerator(),
              level.getChunkSource().getGenerator().getBiomeSource(),
              level.getChunkSource().randomState(),
              level.getStructureManager(),
              level.getChunkSource().getGeneratorState().getLevelSeed(),
              origin,
              level,
              predicate);
      if (StructureBiomeCompatibility.prefitSampler(context, sampler) != sampler)
        throw new IllegalStateException("Unexpected scoped adaptation of a surface context");
      return context;
    }

    private void tick(ServerTickEvent.Post event) {
      if (done || event.getServer() != level.getServer()) return;
      long slice = System.nanoTime();
      try {
        while (!done && System.nanoTime() - slice < 20_000_000L) {
          if (System.nanoTime() - started >= seconds * 1_000_000_000L) {
            finish("deadline", null);
            break;
          }
          long step = System.nanoTime();
          if (pending != null) checkRoot();
          else if (searches.stream().allMatch(search -> search.exhausted))
            finish("finite_search_exhausted", null);
          else {
            Search search = searches.get(searchIndex++ % searches.size());
            if (!search.exhausted) prefix(search);
          }
          maximumStep = Math.max(maximumStep, System.nanoTime() - step);
        }
      } catch (RuntimeException | LinkageError exception) {
        LOGGER.error("Surface prefilter stopped after a failure", exception);
        try {
          if (!done) finish("exception", exception);
        } catch (RuntimeException finishFailure) {
          exception.addSuppressed(finishFailure);
        }
        throw exception;
      }
    }

    private void prefix(Search search) {
      ChunkPos origin = search.next(level, budget, radius, survivorBudget);
      if (origin == null) return;
      JsonObject row = new JsonObject();
      row.addProperty("chunk_x", origin.x);
      row.addProperty("chunk_z", origin.z);
      if (!search.placement.isStructureChunk(
          level.getChunkSource().getGeneratorState(), origin.x, origin.z)) {
        reject(search, row, "native_placement_rejected");
        return;
      }
      if (nativeRoots) {
        String first = initialWeightedChoice(search, origin);
        row.addProperty("initial_weighted_choice", first);
        if (firstWeightedChoiceOnly && !first.equals(search.id)) {
          reject(search, row, "initial_weighted_choice_is_another_structure");
          return;
        }
        checkNativeRoot(search, origin, row);
        return;
      }
      var nativeContext = context(origin, search.root.biomes()::contains);
      var nativeStub =
          search.shadow instanceof DesertPyramidStructure pyramid
              ? pyramid.findGenerationPoint(nativeContext)
              : ((WoodlandMansionStructure) search.shadow).findGenerationPoint(nativeContext);
      if (nativeStub.isEmpty()) {
        reject(search, row, "native_stub_absent");
        return;
      }
      BlockPos anchor = nativeStub.orElseThrow().position();
      row.add("native_stub", position(anchor));
      if (anchor.getY() < search.minimumY || anchor.getY() > search.maximumY) {
        reject(search, row, "configured_absolute_height_rejected");
        return;
      }
      BlockPos query =
          new BlockPos(
              QuartPos.toBlock(QuartPos.fromBlock(anchor.getX())),
              QuartPos.toBlock(QuartPos.fromBlock(anchor.getY())),
              QuartPos.toBlock(QuartPos.fromBlock(anchor.getZ())));
      row.add("biome_query_block_origin", position(query));
      var source = level.getChunkSource().getGenerator().getBiomeSource();
      Holder<Biome> biome =
          source.getNoiseBiome(
              QuartPos.fromBlock(anchor.getX()),
              QuartPos.fromBlock(anchor.getY()),
              QuartPos.fromBlock(anchor.getZ()),
              sampler);
      row.addProperty("outer_biome", biomeId(biome));
      geometry(query, row);
      if (!search.root.biomes().contains(biome)) {
        Holder<Biome> deferred =
            source.getNoiseBiome(
                QuartPos.fromBlock(anchor.getX()),
                QuartPos.fromBlock(anchor.getY()),
                QuartPos.fromBlock(anchor.getZ()),
                StructureBiomeCompatibility.deferredSampler(sampler));
        row.addProperty("deferred_mask_outer_biome", biomeId(deferred));
        row.addProperty("deferred_biome_eligible", search.root.biomes().contains(deferred));
        reject(search, row, "outer_biome_rejected");
        return;
      }
      search.count("cheap_prefix_passed");
      int perTarget = Math.max(1, prefitBudget / searches.size());
      if (prefits >= prefitBudget || search.prefits >= perTarget) {
        reject(search, row, "prefix_passed_prefit_budget_exhausted");
        return;
      }
      row.addProperty("status", "awaiting_template_only_root_prefit");
      pending = new Pending(search, origin, anchor, row);
    }

    private String initialWeightedChoice(Search search, ChunkPos origin) {
      var random = new WorldgenRandom(new LegacyRandomSource(0L));
      random.setLargeFeatureSeed(
          level.getChunkSource().getGeneratorState().getLevelSeed(), origin.x, origin.z);
      int weight =
          search.members.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
      int draw = search.members.size() == 1 ? 0 : random.nextInt(weight);
      for (var member : search.members) {
        draw -= member.weight();
        if (draw < 0) return member.structure().unwrapKey().orElseThrow().location().toString();
      }
      throw new IllegalStateException("Native weighted choice exhausted its weights");
    }

    private void checkNativeRoot(Search search, ChunkPos origin, JsonObject row) {
      int perTarget = Math.max(1, prefitBudget / searches.size());
      if (prefits >= prefitBudget || search.prefits >= perTarget) {
        reject(search, row, "native_root_prefit_budget_exhausted");
        search.exhausted = true;
        return;
      }
      prefits++;
      search.prefits++;
      JsonArray predicates = new JsonArray();
      Predicate<Holder<Biome>> predicate =
          biome -> {
            boolean accepted = search.root.biomes().contains(biome);
            JsonObject value = new JsonObject();
            value.addProperty("biome", biomeId(biome));
            value.addProperty("accepted", accepted);
            predicates.add(value);
            return accepted;
          };
      row.add("actual_root_biome_predicate_calls", predicates);
      Optional<Structure.GenerationStub> result =
          search.root.findValidGenerationPoint(context(origin, predicate));
      if (search.forensics != null)
        search.forensics.observe(
            origin, result.map(Structure.GenerationStub::position), predicates);
      if (result.isPresent()) {
        row.add("actual_root_stub", position(result.orElseThrow().position()));
        if (nativeWeightedPrefix) {
          var outcome =
              StructureWeightedPrefit.evaluate(
                  level, search.members, search.root, origin, result.orElseThrow());
          row.add("native_weighted_template_prefit", outcome.evidence());
          if (!outcome.selected()) {
            reject(search, row, "native_weighted_template_prefix_selected_another_member");
            return;
          }
        }
        search.survivors.add(origin);
        reject(search, row, "natural_prefit_survivor_not_placement_proof");
      } else {
        reject(search, row, "actual_root_rejected");
      }
    }

    private void checkRoot() {
      Pending check = pending;
      pending = null;
      Search search = check.search;
      prefits++;
      search.prefits++;
      JsonArray predicates = new JsonArray();
      Predicate<Holder<Biome>> predicate =
          biome -> {
            boolean accepted = search.root.biomes().contains(biome);
            JsonObject value = new JsonObject();
            value.addProperty("biome", biomeId(biome));
            value.addProperty("accepted", accepted);
            predicates.add(value);
            return accepted;
          };
      check.row.add("actual_root_biome_predicate_calls", predicates);
      check.row.addProperty("status", "template_only_root_prefit_attempted");
      Optional<Structure.GenerationStub> result;
      try {
        result = search.root.findValidGenerationPoint(context(check.origin, predicate));
      } catch (RuntimeException | LinkageError exception) {
        reject(search, check.row, "root_exception");
        throw exception;
      }
      if (result.isPresent()) {
        if (!result.orElseThrow().position().equals(check.anchor)) {
          reject(search, check.row, "root_contract_violation");
          throw new IllegalStateException("Native shadow/root anchor mismatch " + check.origin);
        }
        search.survivors.add(check.origin);
        reject(search, check.row, "natural_prefit_survivor_not_placement_proof");
      } else if (predicates.isEmpty()) {
        reject(search, check.row, "root_contract_violation");
        throw new IllegalStateException("Native shadow/root stub mismatch " + check.origin);
      } else if (predicates.asList().stream()
          .anyMatch(value -> !value.getAsJsonObject().get("accepted").getAsBoolean())) {
        reject(search, check.row, "root_biome_recheck_rejected");
      } else if (predicates.size() == 1) {
        reject(search, check.row, "owned_terrain_or_piece_bounds_rejected");
      } else {
        reject(search, check.row, "root_rejected_after_successful_biome_checks");
      }
    }

    private void geometry(BlockPos query, JsonObject row) {
      IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
      if (terrain == null) throw new IllegalStateException("Missing retained island terrain");
      var layout = terrain.layout();
      var island = layout.largeIsland(query.getX(), query.getZ());
      if (island != null) {
        JsonObject shape = new JsonObject();
        double surface = island.surface(query.getX(), query.getZ());
        double peak = island.peak(query.getX(), query.getZ());
        double radial = island.radius(query.getX(), query.getZ());
        shape.addProperty("radius", radial);
        shape.addProperty("peak_height", island.peakHeight());
        shape.addProperty("local_peak", peak);
        shape.addProperty("surface", surface);
        shape.addProperty(
            "mountain_override_conditions",
            radial < 1
                && peak > 20
                && query.getY() >= surface - 24
                && query.getY() <= surface + 16);
        row.add("owned_inner_large_island_conditions", shape);
      }
      var basin =
          IslandTerrain.basin(layout, sampler.continentalness(), query.getX(), query.getZ());
      if (basin != null) {
        JsonObject shape = new JsonObject();
        double floor = basin.floor(query.getX(), query.getZ());
        shape.addProperty("floor", floor);
        shape.addProperty("waterline", basin.waterline());
        shape.addProperty(
            "ocean_or_shore_override_conditions",
            basin.radius(query.getX(), query.getZ()) < 1.1
                && query.getY() >= floor - 8
                && query.getY() <= basin.waterline() + 16);
        row.add("owned_inner_basin_conditions", shape);
      }
    }

    private boolean isolated() {
      var context = (IslandBiomeSampler) (Object) sampler;
      return sampler == level.getChunkSource().randomState().sampler()
          && context.aeroscapesReimagine$terrainMask() == mask
          && !context.aeroscapesReimagine$deferTerrainMask();
    }

    private void stopping(ServerStoppingEvent event) {
      if (!done && event.getServer() == level.getServer())
        finish("server_stopping", new IllegalStateException("Surface prefilter interrupted"));
    }

    private void finish(String termination, Throwable failure) {
      if (done) return;
      done = true;
      NeoForge.EVENT_BUS.unregister(tickListener);
      NeoForge.EVENT_BUS.unregister(stopListener);
      if (failure == null && !isolated())
        failure = new IllegalStateException("Surface probe changed shared sampler binding/flag");
      if (failure != null) errors.add(failure.getClass().getName() + ": " + failure.getMessage());
      if (pending != null)
        reject(pending.search, pending.row, "prefix_passed_unchecked_at_termination");
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("status", failure == null ? "complete" : "error");
      report.addProperty("termination", termination);
      report.addProperty("seed", level.getSeed());
      report.addProperty(
          "native_structure_level_seed", level.getChunkSource().getGeneratorState().getLevelSeed());
      report.add("plan", plan.deepCopy());
      report.addProperty(
          "scope",
          "Finite procedural search, not spawn proof. No chunks/tickets/placement/world writes. Registered-root prefit may assemble templates internally. Only subsequent ordinary native weighted STARTS/FULL can prove placement; zero survivors never means global absence.");
      report.addProperty(
          "context_contract",
          nativeRoots
              ? "Actual registered roots receive fresh ordinary native-seeded contexts and their unchanged biome predicate. Native height, template, terrain and scoped underground adaptations run through the original root. No stub-position or predicate-count equivalence is assumed. Root eligibility is not native weighted selection."
              : "Pinned Lithostitched DelegatingStructure forwards the same context to the owned adapter; both owned surface adapters call native super with that context. Neither invokes underground scoped adaptation. Native shadows are lazy findGenerationPoint only, using fresh native-seeded contexts; root prefits use separate fresh contexts and the unchanged actual biome predicate.");
      report.addProperty(
          "stage_order",
          nativeRoots
              ? "Native placement then actual registered root prefit. Rejection stage inside the root is not inferred from missing biome predicate calls."
              : "Cheap configured height prefilter is evaluated before biome/fit although native outer height filter runs last. Stage counts describe this prefilter order, not an instrumented native event trace.");
      report.addProperty("cooperative_tick_slice_ms", 20);
      report.addProperty("maximum_single_step_ms", maximumStep / 1_000_000.0);
      report.addProperty("elapsed_ms", (System.nanoTime() - started) / 1_000_000L);
      report.addProperty("deadline_is_cooperative", true);
      report.addProperty("template_only_root_prefits", prefits);
      report.addProperty("first_weighted_choice_only", firstWeightedChoiceOnly);
      report.addProperty("native_weighted_prefix", nativeWeightedPrefix);
      if (rootForensics) report.addProperty("root_forensics", true);
      report.addProperty(
          "weighted_hint_limits",
          "Initial choice reproduces the native set's ordered first weight draw with a separate LegacyRandomSource and level seed. It does not predict fallback after sibling failure or prove nonempty assembled pieces. Normal selected STARTS/FULL remains required.");
      report.addProperty("retained_rejected_candidate_limit_per_target", 64);
      report.addProperty("sampler_mask_binding_and_defer_flag_preserved", isolated());
      report.add("exceptions", errors);
      JsonArray rows = new JsonArray();
      for (Search search : searches) {
        JsonObject row = new JsonObject();
        row.addProperty("id", search.id);
        row.addProperty("set", search.setId);
        row.add("actual_definition", search.definition);
        row.add("actual_set_definition", search.setDefinition);
        JsonArray biomes = new JsonArray();
        search.root.biomes().stream()
            .map(SurfaceStructurePrefilter::biomeId)
            .sorted()
            .forEach(biomes::add);
        row.add("resolved_actual_target_biomes", biomes);
        row.addProperty("enumerated_unique_native_positions", search.enumerated);
        row.addProperty("template_only_root_prefits", search.prefits);
        row.addProperty("search_exhausted", search.exhausted);
        JsonObject counts = new JsonObject();
        search.counts.forEach(counts::addProperty);
        row.add("stage_counts", counts);
        if (search.forensics != null) row.add("root_forensics", search.forensics.report());
        row.add("candidates", search.rows);
        JsonArray survivors = new JsonArray();
        for (ChunkPos origin : search.survivors) {
          JsonObject point = new JsonObject();
          point.addProperty("chunk_x", origin.x);
          point.addProperty("chunk_z", origin.z);
          survivors.add(point);
        }
        row.add("survivor_chunk_origins", survivors);
        rows.add(row);
      }
      report.add("targets", rows);
      Path coverage = null;
      if (failure == null && searches.stream().anyMatch(search -> !search.survivors.isEmpty())) {
        coverage = output.resolveSibling(output.getFileName() + ".coverage-plan.json");
        report.addProperty("native_coverage_plan", coverage.toString());
      }
      write(output, report);
      if (coverage != null) write(coverage, coveragePlan());
      if (failure == null)
        LOGGER.info(
            "Surface structure prefilter completed: prefits={} termination={} output={}",
            prefits,
            termination,
            output);
      else LOGGER.error("Surface structure prefilter failed: output={}", output, failure);
    }

    private JsonObject coveragePlan() {
      JsonObject result = new JsonObject();
      result.addProperty("format", 1);
      result.addProperty("seed", level.getSeed());
      JsonArray targets = new JsonArray();
      JsonArray centers = new JsonArray();
      JsonObject budgets = new JsonObject();
      Map<String, Set<ChunkPos>> origins = new TreeMap<>();
      int total = 0;
      for (Search search : searches) {
        if (search.survivors.isEmpty()) continue;
        targets.add(search.id);
        Set<ChunkPos> setOrigins =
            origins.computeIfAbsent(search.setId, ignored -> new HashSet<>());
        for (ChunkPos origin : search.survivors) {
          if (!setOrigins.add(origin)) continue;
          JsonObject center = new JsonObject();
          center.addProperty("dimension", "minecraft:overworld");
          center.addProperty("set", search.setId);
          center.addProperty("x", origin.getMinBlockX());
          center.addProperty("z", origin.getMinBlockZ());
          centers.add(center);
          total++;
        }
      }
      origins.forEach((set, positions) -> budgets.addProperty(set, positions.size()));
      result.add("targets", targets);
      result.add("centers", centers);
      result.add("set_attempt_budgets", budgets);
      result.addProperty("attempts_per_set", total);
      result.addProperty("radius_regions", 0);
      result.addProperty("full_origin_budget", total);
      result.addProperty("root_full_budget", total);
      result.addProperty("deadline_seconds", 600);
      result.addProperty("request_timeout_seconds", 120);
      result.addProperty("require_all_target_positives", false);
      result.addProperty("trace_structure_commits", true);
      result.addProperty("prefilter_report", output.toString());
      result.addProperty(
          "scope",
          "Only exact natural-prefit survivor regions. Native weighted selection/biome predicates remain unchanged; a prefit survivor is not a positive.");
      return result;
    }
  }

  private static void reject(Search search, JsonObject row, String stage) {
    search.count(stage);
    row.addProperty("status", stage);
    if (search.rows.size() < 64 || stage.equals("natural_prefit_survivor_not_placement_proof"))
      search.rows.add(row);
  }

  private static void validateNativeRoot(JsonObject definition) {
    String type = text(definition, "type");
    if (!Set.of(
            "lithostitched:delegating",
            "lithostitched:jigsaw",
            "minecraft:jigsaw",
            "aeroscapes_reimagine:supported",
            "aeroscapes_reimagine:root_supported",
            "minecraft:buried_treasure",
            "supplementaries:road_sign")
        .contains(type)) throw new IllegalArgumentException("Unaudited native prefit type " + type);
    if (definition.has("delegate")) validateNativeRoot(object(definition, "delegate"));
    if (definition.has("spawn_condition")
        && !text(object(definition, "spawn_condition"), "type")
            .equals("lithostitched:height_filter"))
      throw new IllegalArgumentException("Unaudited native prefit spawn condition");
  }

  private static JsonObject position(BlockPos point) {
    JsonObject result = new JsonObject();
    result.addProperty("x", point.getX());
    result.addProperty("y", point.getY());
    result.addProperty("z", point.getZ());
    return result;
  }

  private static String biomeId(Holder<Biome> biome) {
    return biome.unwrapKey().orElseThrow().location().toString();
  }

  private static JsonObject read(Path path) {
    try {
      JsonElement value = JsonParser.parseString(Files.readString(path));
      if (!value.isJsonObject())
        throw new IllegalArgumentException("Surface plan must be an object");
      JsonObject result = value.getAsJsonObject();
      if (bounded(result, "format", 1, 1, 1) != 1)
        throw new IllegalArgumentException("Unknown surface plan format");
      return result;
    } catch (IOException exception) {
      throw new IllegalArgumentException("Cannot read surface plan " + path, exception);
    }
  }

  private static boolean flag(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
      throw new IllegalArgumentException("Expected boolean " + key);
    return value.getAsBoolean();
  }

  private static JsonObject object(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonObject())
      throw new IllegalArgumentException("Expected object " + key);
    return value.getAsJsonObject();
  }

  private static JsonArray array(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonArray())
      throw new IllegalArgumentException("Expected array " + key);
    return value.getAsJsonArray();
  }

  private static String text(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException("Expected string " + key);
    return value.getAsString();
  }

  private static java.math.BigDecimal number(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException("Expected number " + key);
    return value.getAsBigDecimal();
  }

  private static int bounded(
      JsonObject object, String key, int fallback, int minimum, int maximum) {
    int value = object.has(key) ? number(object, key).intValueExact() : fallback;
    if (value < minimum || value > maximum)
      throw new IllegalArgumentException("Out-of-range " + key);
    return value;
  }

  private static void write(Path path, JsonObject value) {
    try {
      Path parent = path.toAbsolutePath().getParent();
      Files.createDirectories(parent);
      Path temporary = Files.createTempFile(parent, path.getFileName() + ".", ".tmp");
      try {
        Files.writeString(
            temporary, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
        Files.move(
            temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException exception) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException cleanup) {
          exception.addSuppressed(cleanup);
        }
        throw exception;
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot write surface prefilter " + path, exception);
    }
  }
}
