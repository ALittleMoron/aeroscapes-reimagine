package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.regression.noise.mixin.FeatureIndexAccess;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/** Opt-in evidence for real biome-loop block introductions, independent of structure geometry. */
public final class FeatureCommitObserver {
  static final Map<String, Set<String>> ALLOWED =
      Map.ofEntries(
          Map.entry("aeroscapes:patch_sugar_cane", Set.of("minecraft:sugar_cane")),
          Map.entry(
              "farmersdelight:patch_brown_mushroom_colony",
              Set.of("farmersdelight:brown_mushroom_colony")),
          Map.entry(
              "farmersdelight:patch_wild_beetroots",
              Set.of("farmersdelight:sandy_shrub", "farmersdelight:wild_beetroots")),
          Map.entry(
              "farmersdelight:patch_wild_cabbages",
              Set.of("farmersdelight:sandy_shrub", "farmersdelight:wild_cabbages")),
          Map.entry("farmersdelight:patch_wild_onions", Set.of("farmersdelight:wild_onions")),
          Map.entry("farmersdelight:patch_wild_rice", Set.of("farmersdelight:wild_rice")),
          Map.entry(
              "fungidelight:huge_inky_cap_mushroom",
              Set.of(
                  "fungidelight:inky_cap_mushroom_block",
                  "fungidelight:inky_cap_mushroom_edge",
                  "fungidelight:inky_cap_mushroom_stem",
                  "fungidelight:inky_goo_veil")),
          Map.entry(
              "fungidelight:huge_morel_mushroom",
              Set.of("fungidelight:morel_mushroom_block", "fungidelight:morel_mushroom_stem")),
          Map.entry(
              "fungidelight:patch_inky_cap_mushroom_colony",
              Set.of("fungidelight:inky_cap_mushroom_colony")),
          Map.entry(
              "fungidelight:patch_morel_mushroom_colony",
              Set.of("fungidelight:morel_mushroom_colony")),
          Map.entry("fungidelight:rooted_dirt_blob", Set.of("minecraft:rooted_dirt")),
          Map.entry("fungidelight:truffle_dirt_ore", Set.of("fungidelight:truffle_dirt")),
          Map.entry(
              "galosphere:lichen_cordyceps_column",
              Set.of("galosphere:lichen_cordyceps", "galosphere:lichen_cordyceps_plant")),
          Map.entry("minecraft:bamboo", Set.of("minecraft:bamboo")),
          Map.entry("minecraft:bamboo_light", Set.of("minecraft:bamboo")),
          Map.entry("minecraft:patch_berry_common", Set.of("minecraft:sweet_berry_bush")),
          Map.entry("minecraft:patch_berry_rare", Set.of("minecraft:sweet_berry_bush")),
          Map.entry("minecraft:patch_cactus_decorated", Set.of("minecraft:cactus")),
          Map.entry("minecraft:patch_cactus_desert", Set.of("minecraft:cactus")),
          Map.entry("minecraft:patch_pumpkin", Set.of("minecraft:pumpkin")),
          Map.entry("minecraft:patch_sugar_cane", Set.of("minecraft:sugar_cane")),
          Map.entry("minecraft:patch_sugar_cane_badlands", Set.of("minecraft:sugar_cane")),
          Map.entry("minecraft:patch_sugar_cane_desert", Set.of("minecraft:sugar_cane")),
          Map.entry("minecraft:patch_sugar_cane_swamp", Set.of("minecraft:sugar_cane")),
          Map.entry("minecraft:trees_birch", Set.of("minecraft:bee_nest")),
          Map.entry("minecraft:trees_birch_and_oak", Set.of("minecraft:bee_nest")),
          Map.entry("minecraft:trees_cherry", Set.of("minecraft:bee_nest")),
          Map.entry("minecraft:trees_flower_forest", Set.of("minecraft:bee_nest")),
          Map.entry("minecraft:trees_jungle", Set.of("minecraft:cocoa")),
          Map.entry("minecraft:trees_jungle_extra", Set.of("minecraft:cocoa")),
          Map.entry("minecraft:trees_mangrove", Set.of("minecraft:bee_nest")),
          Map.entry("minecraft:trees_meadow", Set.of("minecraft:bee_nest")),
          Map.entry("minecraft:trees_plains", Set.of("minecraft:bee_nest")),
          Map.entry("minecraft:trees_sparse_jungle", Set.of("minecraft:cocoa")),
          Map.entry("natures_spirit:aspen_placed", Set.of("minecraft:bee_nest")),
          Map.entry("natures_spirit:bamboo", Set.of("minecraft:bamboo")),
          Map.entry("natures_spirit:bamboo_light", Set.of("minecraft:bamboo")),
          Map.entry("natures_spirit:birch_tall", Set.of("minecraft:bee_nest")),
          Map.entry(
              "natures_spirit:coconut_placed",
              Set.of(
                  "natures_spirit:coconut",
                  "natures_spirit:coconut_leaves",
                  "natures_spirit:coconut_log")),
          Map.entry(
              "natures_spirit:flower_golden2_placed",
              Set.of("natures_spirit:flaxen_fern", "natures_spirit:large_flaxen_fern")),
          Map.entry(
              "natures_spirit:flower_golden_placed",
              Set.of("natures_spirit:flaxen_fern", "natures_spirit:large_flaxen_fern")),
          Map.entry(
              "natures_spirit:maple2_placed",
              Set.of(
                  "natures_spirit:maple_log",
                  "natures_spirit:orange_maple_leaves",
                  "natures_spirit:red_maple_leaves",
                  "natures_spirit:shiitake_mushroom",
                  "natures_spirit:yellow_maple_leaves")),
          Map.entry(
              "natures_spirit:maple_placed",
              Set.of(
                  "natures_spirit:maple_log",
                  "natures_spirit:orange_maple_leaves",
                  "natures_spirit:red_maple_leaves",
                  "natures_spirit:shiitake_mushroom",
                  "natures_spirit:yellow_maple_leaves")),
          Map.entry(
              "natures_spirit:olive_placed",
              Set.of(
                  "natures_spirit:olive_branch",
                  "natures_spirit:olive_leaves",
                  "natures_spirit:olive_log")),
          Map.entry("natures_spirit:patch_cactus_dunes", Set.of("minecraft:cactus")),
          Map.entry("natures_spirit:patch_cactus_scorched", Set.of("minecraft:cactus")),
          Map.entry("natures_spirit:patch_cactus_stratified", Set.of("minecraft:cactus")),
          Map.entry(
              "natures_spirit:patch_flaxen_fern",
              Set.of("natures_spirit:flaxen_fern", "natures_spirit:large_flaxen_fern")),
          Map.entry(
              "natures_spirit:patch_tundra_red_vegetation",
              Set.of("natures_spirit:green_bearberries", "natures_spirit:purple_bearberries")),
          Map.entry("natures_spirit:pumpkin_feature_placed", Set.of("minecraft:pumpkin")),
          Map.entry(
              "natures_spirit:sparse_olive_placed",
              Set.of(
                  "natures_spirit:olive_branch",
                  "natures_spirit:olive_leaves",
                  "natures_spirit:olive_log")),
          Map.entry("natures_spirit:trees_bamboo_wetlands", Set.of("minecraft:cocoa")),
          Map.entry("natures_spirit:trees_cherry_forest", Set.of("minecraft:bee_nest")),
          Map.entry("natures_spirit:trees_oak_savanna", Set.of("minecraft:bee_nest")),
          Map.entry("natures_spirit:trees_oak_savanna_2", Set.of("minecraft:bee_nest")),
          Map.entry("natures_spirit:yellow_aspen_placed", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:coastal/trees_hawaii", Set.of("minecraft:cocoa")),
          Map.entry("terralith:desert/lush/cacti", Set.of("minecraft:cactus")),
          Map.entry(
              "terralith:flower/blue/flowers",
              Set.of("minecraft:nether_sprouts", "minecraft:warped_roots")),
          Map.entry("terralith:flower/lavender/trees", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:forest/flower/cloud_trees", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:forest/flower/mid/trees_mid", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:forest/flower/small/trees_small", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:forest/flower/tall/trees_tall", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:forest/flower/tiny/trees_tiny", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:forest/vanilla/trees", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:jungle/amethyst/trees", Set.of("minecraft:cocoa")),
          Map.entry("terralith:sakura/bamboo", Set.of("minecraft:bamboo")),
          Map.entry("terralith:sakura/birch", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:sakura/birch_sparse", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:sakura/cherry_trees", Set.of("minecraft:bee_nest")),
          Map.entry("terralith:sakura/sugarcane", Set.of("minecraft:sugar_cane")));
  private static final int LIMIT = 8192;
  private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
  private static volatile Session active;

  private FeatureCommitObserver() {}

  public record Frame(
      Session session, WorldGenLevel region, String feature, Set<Block> blocks, ChunkPos origin) {}

  private record Write(
      String feature, ChunkPos origin, BlockPos position, BlockState before, BlockState after) {}

  public record Attempt(
      Session session,
      String topFeature,
      String configuredFeature,
      String featureType,
      BlockPos position,
      String before,
      String support,
      String biome) {}

  private record AttemptResult(Attempt attempt, Boolean success) {}

  private static final Set<String> MODIFIER_ROUTES =
      Set.of(
          "natures_spirit:coconut_placed",
          "natures_spirit:olive_placed",
          "natures_spirit:sparse_olive_placed",
          "farmersdelight:patch_brown_mushroom_colony",
          "natures_spirit:patch_tundra_red_vegetation");

  public record ModifierStage(Session session, WorldGenLevel region, String key, BlockPos input) {}

  public static ModifierStage beginModifier(
      PlacementModifier modifier, PlacementContext context, BlockPos input) {
    Frame frame = CURRENT.get();
    if (frame == null
        || frame.session != active
        || frame.region != context.getLevel()
        || !MODIFIER_ROUTES.contains(frame.feature)) return null;
    String top =
        context
            .topFeature()
            .map(
                feature -> {
                  var key =
                      frame
                          .session
                          .level
                          .registryAccess()
                          .registryOrThrow(Registries.PLACED_FEATURE)
                          .getKey(feature);
                  return key == null ? "unregistered" : key.toString();
                })
            .orElse("nested_without_biome_check");
    String key =
        frame.feature
            + " / "
            + top
            + " / "
            + BuiltInRegistries.PLACEMENT_MODIFIER_TYPE.getKey(modifier.type());
    frame
        .session
        .modifierInputs
        .computeIfAbsent(key, ignored -> new AtomicLong())
        .incrementAndGet();
    return new ModifierStage(frame.session, frame.region, key, input.immutable());
  }

  public static void modifierOutput(ModifierStage stage, BlockPos output) {
    long number =
        stage
            .session
            .modifierOutputs
            .computeIfAbsent(stage.key, ignored -> new AtomicLong())
            .incrementAndGet();
    if (number > 64) return;
    JsonObject row = new JsonObject();
    row.addProperty("stage", stage.key);
    row.addProperty("input_x", stage.input.getX());
    row.addProperty("input_y", stage.input.getY());
    row.addProperty("input_z", stage.input.getZ());
    row.addProperty("output_x", output.getX());
    row.addProperty("output_y", output.getY());
    row.addProperty("output_z", output.getZ());
    row.addProperty("output_state", sampledBlock(stage.session, stage.region, output));
    row.addProperty("support_at_output", sampledBlock(stage.session, stage.region, output.below()));
    row.addProperty("level_biome_at_output", sampledBiome(stage.session, stage.region, output));
    stage.session.modifierRows.add(row);
  }

  public static Attempt beginAttempt(
      ConfiguredFeature<?, ?> feature, WorldGenLevel region, BlockPos origin) {
    Frame frame = CURRENT.get();
    if (frame == null || frame.session != active || frame.region != region) return null;
    long number =
        frame
            .session
            .attemptCounts
            .computeIfAbsent(frame.feature, ignored -> new AtomicLong())
            .incrementAndGet();
    if (number > 64) return null;
    var key =
        region.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getKey(feature);
    return new Attempt(
        frame.session,
        frame.feature,
        key == null ? "inline" : key.toString(),
        BuiltInRegistries.FEATURE.getKey(feature.feature()).toString(),
        origin.immutable(),
        sampledBlock(frame.session, region, origin),
        sampledBlock(frame.session, region, origin.below()),
        sampledBiome(frame.session, region, origin));
  }

  public static void endAttempt(Attempt attempt, Boolean success) {
    if (attempt != null) attempt.session.attempts.add(new AttemptResult(attempt, success));
  }

  public static void oreTargetTest(
      WorldGenLevel region,
      BlockState actual,
      BlockState desired,
      BlockPos position,
      boolean accepted) {
    Frame frame = CURRENT.get();
    if (frame == null
        || frame.session != active
        || frame.region != region
        || !(frame.feature.equals("fungidelight:rooted_dirt_blob")
            || frame.feature.equals("fungidelight:truffle_dirt_ore"))) return;
    String key =
        frame.feature
            + " / "
            + BuiltInRegistries.BLOCK.getKey(actual.getBlock())
            + " -> "
            + BuiltInRegistries.BLOCK.getKey(desired.getBlock())
            + " / "
            + accepted;
    long number =
        frame.session.oreTests.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    if (number > 64) return;
    JsonObject row = new JsonObject();
    row.addProperty("test", key);
    row.addProperty("x", position.getX());
    row.addProperty("y", position.getY());
    row.addProperty("z", position.getZ());
    row.addProperty("actual_state", actual.toString());
    row.addProperty("desired_state", desired.toString());
    row.addProperty("native_accepted", accepted);
    frame.session.oreTestRows.add(row);
  }

  private static String sampledBlock(Session session, WorldGenLevel region, BlockPos position) {
    if (region.isOutsideBuildHeight(position)) return "outside_build_height_not_sampled";
    if (!region.hasChunk(position.getX() >> 4, position.getZ() >> 4))
      return "outside_region_not_sampled";
    try {
      return region.getBlockState(position).toString();
    } catch (ReportedException failure) {
      session.hintReadFailures.incrementAndGet();
      return "unavailable_region_sample: " + failure.getMessage();
    }
  }

  private static String sampledBiome(Session session, WorldGenLevel region, BlockPos position) {
    if (region.isOutsideBuildHeight(position)) return "outside_build_height_not_sampled";
    // BiomeManager fuzzy zoom may select a neighboring quart cell.
    for (int dx : new int[] {-4, 4})
      for (int dz : new int[] {-4, 4})
        if (!region.hasChunk((position.getX() + dx) >> 4, (position.getZ() + dz) >> 4))
          return "region_edge_biome_not_sampled";
    try {
      return region
          .getBiome(position)
          .unwrapKey()
          .map(key -> key.location().toString())
          .orElse("unregistered_biome_holder");
    } catch (ReportedException failure) {
      session.hintReadFailures.incrementAndGet();
      return "unavailable_region_sample: " + failure.getMessage();
    }
  }

  static synchronized Session arm(
      ServerLevel level, Map<String, Set<Block>> routes, Set<Long> chunks) {
    if (active != null) throw new IllegalStateException("Feature commit observer already armed");
    Session session = new Session(level, routes, chunks);
    active = session;
    return session;
  }

  static synchronized void disarm(Session session) {
    if (active == session) active = null;
  }

  public static boolean armed() {
    return active != null;
  }

  public static Frame enter(PlacedFeature feature, WorldGenLevel region, BlockPos origin) {
    Frame previous = CURRENT.get();
    CURRENT.remove();
    Session session = active;
    if (session != null && region.getLevel() == session.level) {
      var key =
          session.level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).getKey(feature);
      Set<Block> blocks = key == null ? null : session.routes.get(key.toString());
      if (blocks != null) {
        session.calls.incrementAndGet();
        CURRENT.set(new Frame(session, region, key.toString(), blocks, new ChunkPos(origin)));
      }
    }
    return previous;
  }

  public static void restore(Frame previous) {
    if (previous == null) CURRENT.remove();
    else CURRENT.set(previous);
  }

  public static void committed(
      ServerLevel level,
      WorldGenLevel region,
      BlockPos position,
      BlockState before,
      BlockState after) {
    Session session = active;
    if (session == null
        || session.level != level
        || before == null
        || before.getBlock() == after.getBlock()
        || position.getY() < level.getMinBuildHeight()
        || position.getY() >= level.getMaxBuildHeight()
        || !session.chunks.contains(new ChunkPos(position).toLong())) return;
    session.changes.incrementAndGet();
    session.writes.remove(position.asLong());
    if (!session.targets.contains(before.getBlock()) && !session.targets.contains(after.getBlock()))
      return;
    if (session.touches.incrementAndGet() > LIMIT) {
      session.overflow = true;
      session.writes.clear();
      return;
    }
    long writesAtPosition = session.targetChanges.merge(position.asLong(), 1L, Long::sum);
    Frame frame = CURRENT.get();
    if (writesAtPosition != 1
        || frame == null
        || frame.session != session
        || frame.region != region
        || !frame.blocks.contains(after.getBlock())
        || session.overflow) return;
    session.introductions.incrementAndGet();
    session.writes.put(
        position.asLong(),
        new Write(frame.feature, frame.origin, position.immutable(), before, after));
  }

  public static final class Session {
    private final ServerLevel level;
    private final Map<String, Set<Block>> routes;
    private final Set<Long> chunks;
    private final Set<Block> targets = new HashSet<>();
    private final ConcurrentHashMap<Long, Write> writes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> targetChanges = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong changes = new AtomicLong();
    private final AtomicLong introductions = new AtomicLong();
    private final AtomicLong touches = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> attemptCounts = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<AttemptResult> attempts = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, AtomicLong> modifierInputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> modifierOutputs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<JsonObject> modifierRows = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, AtomicLong> oreTests = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<JsonObject> oreTestRows = new ConcurrentLinkedQueue<>();
    private final JsonArray nativeIndices = new JsonArray();
    private final AtomicLong hintReadFailures = new AtomicLong();
    private volatile boolean overflow;

    private Session(ServerLevel level, Map<String, Set<Block>> routes, Set<Long> chunks) {
      this.level = level;
      this.routes = Map.copyOf(routes);
      this.chunks = Set.copyOf(chunks);
      routes.values().forEach(targets::addAll);
      var steps =
          ((FeatureIndexAccess) level.getChunkSource().getGenerator())
              .aeroscapesRegression$featuresPerStep()
              .get();
      var registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
      for (String id : new java.util.TreeSet<>(ALLOWED.keySet())) {
        PlacedFeature feature = registry.get(ResourceLocation.parse(id));
        if (feature == null) continue;
        for (int step = 0; step < steps.size(); step++) {
          int index = steps.get(step).indexMapping().applyAsInt(feature);
          if (index < 0
              || index >= steps.get(step).features().size()
              || !steps.get(step).features().get(index).equals(feature)) continue;
          JsonObject row = new JsonObject();
          row.addProperty("id", id);
          row.addProperty("step", step);
          row.addProperty("index", index);
          nativeIndices.add(row);
        }
      }
    }

    JsonObject evidence(BlockPos position, BlockState actual) {
      Write write = overflow ? null : writes.get(position.asLong());
      if (write == null || !valid(position, actual.getBlock())) return null;
      JsonObject result = new JsonObject();
      result.addProperty("proof_kind", "source_feature_commit");
      result.addProperty("successful_block_ID_change", true);
      result.addProperty("FULL_block_ID_matches_commit", true);
      result.addProperty("target_ID_changes_at_position", 1);
      result.addProperty("counter_period", "since_observer_arm");
      result.addProperty("top_placed_feature", write.feature);
      result.addProperty("origin_chunk_x", write.origin.x);
      result.addProperty("origin_chunk_z", write.origin.z);
      result.addProperty("x", write.position.getX());
      result.addProperty("y", write.position.getY());
      result.addProperty("z", write.position.getZ());
      result.addProperty("old_state", write.before.toString());
      result.addProperty("new_state", write.after.toString());
      result.addProperty("FULL_state", actual.toString());
      return result;
    }

    boolean valid(BlockPos position, Block block) {
      Write write = overflow ? null : writes.get(position.asLong());
      return write != null
          && write.after.getBlock() == block
          && Long.valueOf(1).equals(targetChanges.get(position.asLong()));
    }

    JsonObject report() {
      JsonObject result = new JsonObject();
      result.addProperty("version", 1);
      result.addProperty("enabled", true);
      result.addProperty("counter_period", "since_observer_arm");
      result.addProperty("armed", active == this);
      result.addProperty("allowlisted_feature_calls", calls.get());
      result.addProperty("observed_block_ID_changes", changes.get());
      result.addProperty("qualifying_introductions", introductions.get());
      result.addProperty("target_touching_ID_changes", touches.get());
      result.addProperty("retained_positions", writes.size());
      result.addProperty("record_limit", LIMIT);
      result.addProperty("planned_chunk_count", chunks.size());
      result.add("actual_native_feature_indices_at_arm", nativeIndices.deepCopy());
      JsonObject attemptTotals = new JsonObject();
      attemptCounts.forEach((feature, count) -> attemptTotals.addProperty(feature, count.get()));
      result.add("configured_attempt_counts_including_nested_calls", attemptTotals);
      JsonArray attemptRows = new JsonArray();
      for (AttemptResult observation : attempts) {
        Attempt attempt = observation.attempt;
        JsonObject row = new JsonObject();
        row.addProperty("top_placed_feature", attempt.topFeature);
        row.addProperty("configured_feature", attempt.configuredFeature);
        row.addProperty("feature_type", attempt.featureType);
        row.addProperty("x", attempt.position.getX());
        row.addProperty("y", attempt.position.getY());
        row.addProperty("z", attempt.position.getZ());
        row.addProperty("state_before", attempt.before);
        row.addProperty("support_before", attempt.support);
        row.addProperty("level_biome_before", attempt.biome);
        row.addProperty("native_return", observation.success);
        row.addProperty("native_threw", observation.success == null);
        attemptRows.add(row);
      }
      result.add("first_64_configured_attempts_per_top_feature", attemptRows);
      result.addProperty("hint_region_read_failures", hintReadFailures.get());
      JsonObject stages = new JsonObject();
      modifierInputs.forEach(
          (key, inputs) -> {
            JsonObject stage = new JsonObject();
            stage.addProperty("inputs", inputs.get());
            stage.addProperty("outputs", modifierOutputs.getOrDefault(key, new AtomicLong()).get());
            stages.add(key, stage);
          });
      result.add("lazy_modifier_stage_counts", stages);
      JsonArray modifierSamples = new JsonArray();
      modifierRows.forEach(modifierSamples::add);
      result.add("first_64_modifier_outputs_per_stage", modifierSamples);
      JsonObject oreCounts = new JsonObject();
      oreTests.forEach((key, count) -> oreCounts.addProperty(key, count.get()));
      result.add("native_ore_target_test_counts", oreCounts);
      JsonArray oreSamples = new JsonArray();
      oreTestRows.forEach(oreSamples::add);
      result.add("first_64_native_ore_tests_per_kind", oreSamples);
      result.addProperty(
          "modifier_trace_limits",
          "Original lazy streams are observed with peek without collecting, reordering, extra RNG draws or repeated enumeration. Counts group native modifier types within their placement context; repeated same-type modifiers are combined.");
      result.addProperty(
          "configured_attempt_limits",
          "Nested configured calls are separate observations. These samples describe original modifier survivors and native returns, not block introduction proof; null return denotes a native exception. Sampling reads the existing WorldGenRegion and consumes no RNG.");
      result.addProperty("overflow_disables_all_trace_evidence", overflow);
      result.addProperty(
          "scope",
          "Opt-in planned chunks only. Native biome-loop introductions; no-op writes excluded."
              + " Every observed WorldGenRegion or native OreFeature section ID-changing commit invalidates prior evidence."
              + " Multiple target-touching ID changes at one position permanently reject its trace,"
              + " irrespective of callback order. FULL block ID/source are revalidated. Direct"
              + " writes through other APIs and later identical-ID overwrites are not provenance"
              + " claims.");
      return result;
    }
  }
}
