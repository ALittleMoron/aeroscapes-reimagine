package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandBiomeSource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.PalettedContainer;
import terrablender.worldgen.IExtendedParameterList;

/** Read-only diagnosis of the seed-42 FULL/source disagreement; never regenerates its chunk. */
public final class BiomeMismatchRegression {
  private static final int X = 5638;
  private static final int Z = 262;
  private static final String DESERT = "minecraft:desert";
  private static final String SAVANNA = "minecraft:windswept_savanna";
  private static final String NON_MINIMUM_CONTROL = "minecraft:plains";
  private static final String DEFERRED = "terrablender:deferred_placeholder";
  private static final String INJECTOR =
      "dev.worldgen.lithostitched.impl.worldgen.biomeinjector.internal.InjectorBiomeSource";
  private static final String BLUEPRINT =
      "com.teamabnormals.blueprint.common.world.modification.ModdedBiomeSource";
  private static final int[] OFFSETS = {0, -1, 1, -2, 2, -4, 4, -8, 8, -16, 16, -32, 32, -64, 64};

  private BiomeMismatchRegression() {}

  private record Leaf(Object node, String biome) {}

  private record Score(Leaf leaf, long fitness) {}

  private record NativeTree(
      Class<?> leafClass, Field root, Field children, Field value, Method distance) {
    private static NativeTree open() throws ReflectiveOperationException {
      Class<?> tree = Class.forName("net.minecraft.world.level.biome.Climate$RTree");
      Class<?> node = Class.forName("net.minecraft.world.level.biome.Climate$RTree$Node");
      Class<?> leaf = Class.forName("net.minecraft.world.level.biome.Climate$RTree$Leaf");
      Class<?> branch = Class.forName("net.minecraft.world.level.biome.Climate$RTree$SubTree");
      Method distance = node.getDeclaredMethod("distance", long[].class);
      distance.setAccessible(true);
      return new NativeTree(
          leaf, field(tree, "root"), field(branch, "children"), field(leaf, "value"), distance);
    }

    private List<Leaf> leaves(Object tree) throws ReflectiveOperationException {
      List<Leaf> result = new ArrayList<>();
      ArrayDeque<Object> pending = new ArrayDeque<>();
      pending.add(root.get(tree));
      while (!pending.isEmpty()) {
        Object node = pending.removeLast();
        if (leafClass.isInstance(node)) {
          Object biome = value.get(node);
          require(biome instanceof Holder<?>, "Native leaf is not a biome holder");
          result.add(new Leaf(node, id((Holder<?>) biome)));
        } else {
          Object descendants = children.get(node);
          require(descendants instanceof Object[], "Native branch has no children array");
          for (Object child : (Object[]) descendants) pending.add(child);
        }
      }
      return result;
    }
  }

  static JsonObject nativeTie(
      ServerLevel level, BiomeSource outer, BlockPos position, String first, String second) {
    require(level.getServer().isSameThread(), "Classify biome ties on the server thread");
    if (first.equals(second) || first.equals(DEFERRED) || second.equals(DEFERRED)) return null;
    try {
      IslandBiomeSource owned = ownedSource(outer);
      TreeSet<String> habitats = habitatIds(owned);
      if (habitats.contains(first) || habitats.contains(second)) return null;
      IExtendedParameterList<?> parameters = effective(delegate(owned));
      require(parameters.isInitialized(), "Live TerraBlender trees are uninitialized");
      IExtendedParameterList<?> generation = effective(delegate(owned.forGeneration()));
      require(generation.isInitialized(), "Generation TerraBlender trees are uninitialized");
      int qx = position.getX() >> 2;
      int qy = position.getY() >> 2;
      int qz = position.getZ() >> 2;
      int region = parameters.getUniqueness(qx, qy, qz);
      int generationRegion = generation.getUniqueness(qx, qy, qz);
      if (region != generationRegion
          || !parameters
              .getRegion(region)
              .getName()
              .equals(generation.getRegion(generationRegion).getName())) return null;
      Climate.TargetPoint target =
          level.getChunkSource().randomState().sampler().sample(qx, qy, qz);
      NativeTree nativeTree = NativeTree.open();
      JsonObject selected = fitness(nativeTree, parameters.getTree(region), target, first, second);
      boolean fallback = region != 0 && onlyDeferred(selected);
      int treeIndex = fallback ? 0 : region;
      JsonObject evaluated =
          fallback ? fitness(nativeTree, parameters.getTree(0), target, first, second) : selected;
      if (!evaluated.get("both_recorded_IDs_are_global_minima").getAsBoolean()) return null;
      Object tree = parameters.getTree(treeIndex);
      Object generationTree = generation.getTree(treeIndex);
      if (fallback
          && parameters.getTree(region) != generation.getTree(region)
          && !onlyDeferred(fitness(nativeTree, generation.getTree(region), target, first, second)))
        return null;
      JsonObject generationFitness =
          tree == generationTree
              ? evaluated
              : fitness(nativeTree, generationTree, target, first, second);
      if (!generationFitness.get("both_recorded_IDs_are_global_minima").getAsBoolean()
          || !generationFitness
              .get("native_minimum_fitness")
              .equals(evaluated.get("native_minimum_fitness"))) return null;
      JsonObject result = new JsonObject();
      result.addProperty("classification", "native_equal_fitness");
      result.addProperty("seed", level.getSeed());
      result.addProperty("dimension", level.dimension().location().toString());
      result.addProperty("x", position.getX());
      result.addProperty("y", position.getY());
      result.addProperty("z", position.getZ());
      result.addProperty("quart_x", qx);
      result.addProperty("quart_y", qy);
      result.addProperty("quart_z", qz);
      result.addProperty("first_biome", first);
      result.addProperty("second_biome", second);
      result.add("target_quantized", target(target));
      result.addProperty("region_index", region);
      result.addProperty("generation_region_index", generationRegion);
      result.addProperty("region", parameters.getRegion(region).getName().toString());
      result.addProperty(
          "generation_region", generation.getRegion(generationRegion).getName().toString());
      result.addProperty("live_and_generation_region_choice_equal", true);
      result.addProperty("evaluated_tree_index", treeIndex);
      result.addProperty("used_deferred_fallback", fallback);
      result.addProperty("selected_tree_shared_with_generation", tree == generationTree);
      result.add("selected_region_fitness", selected);
      result.add("evaluated_tree_fitness", evaluated);
      result.add("generation_tree_fitness", generationFitness);
      JsonArray excluded = new JsonArray();
      habitats.forEach(excluded::add);
      result.add("excluded_habitat_and_void_biomes", excluded);
      result.addProperty("native_biome_queries", 0);
      result.addProperty("native_nearest_leaf_cache_reset", false);
      result.addProperty(
          "proof_boundary",
          "Both supplied IDs are global native leaf minima at this live climate; live/clone region"
              + " choices agree and both IDs exclude every owned habitat and void biome. Fallback"
              + " requires every selected-region minimum to be deferred_placeholder. This does not"
              + " prove general outer-wrapper postprocessing or generation-time cached-sampler"
              + " equivalence, recover original worker query order, or establish a FULL witness.");
      return result;
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(
          "Cannot inspect exact native biome tie at " + position, failure);
    }
  }

  private static boolean onlyDeferred(JsonObject fitness) {
    JsonArray winners = fitness.getAsJsonArray("minimum_biomes");
    return winners.size() == 1 && winners.get(0).getAsString().equals(DEFERRED);
  }

  public static void verify(ServerLevel level, Path output) {
    require(level.getServer().isDedicatedServer(), "Disposable dedicated server only");
    require(level.getServer().isSameThread(), "Run mismatch diagnostic on the server thread");
    require(output.isAbsolute() && output.toString().contains("/.runtime/"), "Ignored output only");
    JsonObject report = new JsonObject();
    report.addProperty("format", 1);
    report.addProperty("seed", level.getSeed());
    report.addProperty("status", "running");
    JsonArray points = new JsonArray();
    report.add("points", points);
    try {
      write(output, report);
      require(level.getSeed() == 42, "This diagnosis requires the verified seed-42 world");
      Map<Integer, String> stored = savedBiomes(level);
      BiomeSource outer = level.getChunkSource().getGenerator().getBiomeSource();
      IslandBiomeSource owned = ownedSource(outer);
      IslandBiomeSource generation = owned.forGeneration();
      MultiNoiseBiomeSource delegate = delegate(owned);
      MultiNoiseBiomeSource generationDelegate = delegate(generation);
      IExtendedParameterList<?> parameters = effective(delegate);
      IExtendedParameterList<?> generationParameters = effective(generationDelegate);
      require(parameters.isInitialized(), "Live TerraBlender trees are uninitialized");
      require(generationParameters.isInitialized(), "Generation trees are uninitialized");
      Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
      NativeTree nativeTree = NativeTree.open();
      report.addProperty("outer_source", outer.getClass().getName());
      report.addProperty("owned_source", owned.getClass().getName());
      report.addProperty("dimension", level.dimension().location().toString());
      TreeSet<String> habitats = habitatIds(owned);
      JsonArray habitatBiomes = new JsonArray();
      habitats.forEach(habitatBiomes::add);
      report.add("owned_habitat_and_void_biomes", habitatBiomes);
      report.addProperty(
          "recorded_IDs_disjoint_from_habitat_and_void",
          !habitats.contains(DESERT) && !habitats.contains(SAVANNA));
      report.addProperty("effective_parameter_lists_shared", parameters == generationParameters);
      report.addProperty("primer_radius_blocks", 256);
      report.addProperty("primer_y_offsets_quarts", "-16,0,16");
      report.addProperty("primer_candidates_per_point", OFFSETS.length * OFFSETS.length * 3);
      report.addProperty("deadline_seconds", 60);
      report.addProperty(
          "limits",
          "Only the seven recorded quart cells of saved seed 42 are examined. Native Node.distance"
              + " is invoked on actual initialized tree leaves; no reconstructed climate table."
              + " Adjacent outer-source primer/target calls share the live source and sampler."
              + " forGeneration clones are new diagnostic objects, not the original worker objects;"
              + " their native trees may remain shared. Native tree lastResult is ThreadLocal;"
              + " original generation-worker cache state is unavailable. No cache is reset or installed.");
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
      boolean classifierPassed = true;
      for (int y = -34; y <= -10; y += 4) {
        JsonObject point =
            point(
                outer,
                owned,
                generation,
                delegate,
                generationDelegate,
                parameters,
                generationParameters,
                sampler,
                nativeTree,
                stored.get(y),
                y);
        points.add(point);
        write(output, report);
        primers(outer, sampler, point, y, deadline);
        write(output, report);
        JsonObject classifier = classifierChecks(level, outer, owned, point, y);
        point.add("classifier_contract", classifier);
        classifierPassed &= classifier.get("passed").getAsBoolean();
        write(output, report);
      }
      report.addProperty("classifier_contract_passed", classifierPassed);
      report.addProperty("status", "complete");
      write(output, report);
      require(
          stored.values().stream().allMatch(DESERT::equals),
          "Saved FULL no longer matches the recorded desert witness; inspect " + output);
      require(classifierPassed, "Native tie classifier contract failed; inspect " + output);
      LogUtils.getLogger().info("Native biome tie classifier regression passed: points=7");
      LogUtils.getLogger().info("Biome mismatch diagnostic complete: seed=42 output={}", output);
    } catch (ReflectiveOperationException | IOException | RuntimeException failure) {
      report.addProperty("status", "error");
      report.addProperty("error", failure.toString());
      try {
        write(output, report);
      } catch (IOException writeFailure) {
        failure.addSuppressed(writeFailure);
      }
      throw new IllegalStateException(
          "Cannot diagnose seed-42 biome mismatch; inspect " + output, failure);
    }
  }

  private static JsonObject classifierChecks(
      ServerLevel level, BiomeSource outer, IslandBiomeSource owned, JsonObject point, int y)
      throws ReflectiveOperationException {
    BlockPos position = new BlockPos(X, y, Z);
    JsonObject result = new JsonObject();
    JsonObject positive = nativeTie(level, outer, position, DESERT, SAVANNA);
    boolean passed = positive != null;
    result.addProperty("known_pair_accepted", positive != null);
    if (positive != null) result.add("known_pair_proof", positive);
    JsonArray controls = new JsonArray();
    for (String excluded : habitatIds(owned)) {
      boolean firstRejected = nativeTie(level, outer, position, excluded, SAVANNA) == null;
      boolean secondRejected = nativeTie(level, outer, position, DESERT, excluded) == null;
      JsonObject control = new JsonObject();
      control.addProperty("biome", excluded);
      control.addProperty("first_position_rejected", firstRejected);
      control.addProperty("second_position_rejected", secondRejected);
      controls.add(control);
      passed &= firstRejected && secondRejected;
    }
    result.add("habitat_and_void_controls", controls);
    JsonObject nonMinimum = new JsonObject();
    nonMinimum.addProperty("biome", NON_MINIMUM_CONTROL);
    boolean registered =
        level
            .registryAccess()
            .registryOrThrow(Registries.BIOME)
            .getHolder(Biomes.PLAINS)
            .isPresent();
    boolean provenNonMinimum = false;
    for (var entry :
        point.getAsJsonObject("selected_region_fitness").getAsJsonArray("recorded_competitors")) {
      JsonObject competitor = entry.getAsJsonObject();
      if (!competitor.get("biome").getAsString().equals(NON_MINIMUM_CONTROL)) continue;
      provenNonMinimum = !competitor.get("is_global_minimum").getAsBoolean();
      nonMinimum.add("native_entry_fitness", competitor);
    }
    boolean rejected =
        nativeTie(level, outer, position, DESERT, NON_MINIMUM_CONTROL) == null
            && nativeTie(level, outer, position, NON_MINIMUM_CONTROL, SAVANNA) == null;
    nonMinimum.addProperty("registered", registered);
    nonMinimum.addProperty("native_non_minimum_proven", provenNonMinimum);
    nonMinimum.addProperty("both_pair_positions_rejected", rejected);
    result.add("non_minimum_control", nonMinimum);
    passed &= registered && provenNonMinimum && rejected;
    result.addProperty("passed", passed);
    return result;
  }

  private static JsonObject point(
      BiomeSource outer,
      IslandBiomeSource owned,
      IslandBiomeSource generation,
      MultiNoiseBiomeSource delegate,
      MultiNoiseBiomeSource generationDelegate,
      IExtendedParameterList<?> parameters,
      IExtendedParameterList<?> generationParameters,
      Climate.Sampler sampler,
      NativeTree nativeTree,
      String stored,
      int y)
      throws ReflectiveOperationException {
    int qx = X >> 2;
    int qy = y >> 2;
    int qz = Z >> 2;
    Climate.TargetPoint target = sampler.sample(qx, qy, qz);
    JsonObject point = new JsonObject();
    point.addProperty("x", X);
    point.addProperty("y", y);
    point.addProperty("z", Z);
    point.addProperty("quart_x", qx);
    point.addProperty("quart_y", qy);
    point.addProperty("quart_z", qz);
    point.addProperty("saved_FULL_biome", stored);
    point.add("target_quantized", target(target));
    point.addProperty("initial_outer", id(outer.getNoiseBiome(qx, qy, qz, sampler)));
    point.addProperty("initial_owned", id(owned.getNoiseBiome(qx, qy, qz, sampler)));
    point.addProperty("initial_delegate", id(delegate.getNoiseBiome(qx, qy, qz, sampler)));
    point.addProperty("initial_generation", id(generation.getNoiseBiome(qx, qy, qz, sampler)));
    point.addProperty(
        "initial_generation_delegate", id(generationDelegate.getNoiseBiome(qx, qy, qz, sampler)));
    int region = parameters.getUniqueness(qx, qy, qz);
    int generationRegion = generationParameters.getUniqueness(qx, qy, qz);
    point.addProperty("region_index", region);
    point.addProperty("generation_region_index", generationRegion);
    point.addProperty("live_and_generation_region_choice_equal", region == generationRegion);
    point.addProperty("region", parameters.getRegion(region).getName().toString());
    Object selectedTree = parameters.getTree(region);
    point.addProperty(
        "selected_tree_shared_with_generation",
        region == generationRegion
            && selectedTree == generationParameters.getTree(generationRegion));
    JsonObject selected = fitness(nativeTree, selectedTree, target);
    point.add("selected_region_fitness", selected);
    if (region != 0)
      point.add("fallback_tree0_fitness", fitness(nativeTree, parameters.getTree(0), target));
    point.addProperty(
        "fallback_applicable_if_deferred_wins",
        region != 0
            && selected.getAsJsonArray("minimum_biomes").asList().stream()
                .anyMatch(value -> value.getAsString().equals(DEFERRED)));
    return point;
  }

  private static void primers(
      BiomeSource outer, Climate.Sampler sampler, JsonObject point, int y, long deadline) {
    int qx = X >> 2;
    int qy = y >> 2;
    int qz = Z >> 2;
    Climate.TargetPoint before = sampler.sample(qx, qy, qz);
    Map<String, JsonObject> outcomes = new LinkedHashMap<>();
    int candidates = 0;
    for (int dy : new int[] {0, -16, 16}) {
      for (int dx : OFFSETS) {
        for (int dz : OFFSETS) {
          require(System.nanoTime() < deadline, "Primer search exceeded 60 seconds");
          String primer = id(outer.getNoiseBiome(qx + dx, qy + dy, qz + dz, sampler));
          String actual = id(outer.getNoiseBiome(qx, qy, qz, sampler));
          candidates++;
          if (!outcomes.containsKey(actual)) {
            JsonObject observation = new JsonObject();
            observation.addProperty("primer_quart_x", qx + dx);
            observation.addProperty("primer_quart_y", qy + dy);
            observation.addProperty("primer_quart_z", qz + dz);
            observation.addProperty("primer_biome", primer);
            observation.addProperty("target_biome", actual);
            outcomes.put(actual, observation);
          }
        }
      }
    }
    JsonArray observations = new JsonArray();
    outcomes.values().forEach(observations::add);
    point.add("outer_primer_outcomes", observations);
    point.addProperty("primer_candidates_examined", candidates);
    point.addProperty(
        "both_recorded_IDs_reproduced_on_same_outer_source",
        outcomes.containsKey(DESERT) && outcomes.containsKey(SAVANNA));
    point.addProperty(
        "target_climate_unchanged_after_primers",
        before.equals(sampler.sample(qx, qy, qz))
            && target(before).equals(point.get("target_quantized")));
  }

  private static JsonObject fitness(NativeTree nativeTree, Object tree, Climate.TargetPoint target)
      throws ReflectiveOperationException {
    return fitness(nativeTree, tree, target, DESERT, SAVANNA);
  }

  private static JsonObject fitness(
      NativeTree nativeTree, Object tree, Climate.TargetPoint target, String first, String second)
      throws ReflectiveOperationException {
    List<Score> scores = new ArrayList<>();
    long[] coordinates = {
      target.temperature(),
      target.humidity(),
      target.continentalness(),
      target.erosion(),
      target.depth(),
      target.weirdness(),
      0L
    };
    for (Leaf leaf : nativeTree.leaves(tree)) {
      Object result = nativeTree.distance().invoke(leaf.node(), (Object) coordinates);
      require(result instanceof Long, "Native distance did not return long");
      scores.add(new Score(leaf, (Long) result));
    }
    scores.sort(
        Comparator.comparingLong(Score::fitness).thenComparing(score -> score.leaf().biome()));
    require(!scores.isEmpty(), "Initialized tree contains no leaves");
    long minimum = scores.getFirst().fitness();
    TreeSet<String> winners = new TreeSet<>();
    for (Score score : scores) if (score.fitness() == minimum) winners.add(score.leaf().biome());
    JsonObject result = new JsonObject();
    result.addProperty("leaf_count", scores.size());
    result.addProperty("native_minimum_fitness", minimum);
    JsonArray minimumBiomes = new JsonArray();
    winners.forEach(minimumBiomes::add);
    result.add("minimum_biomes", minimumBiomes);
    JsonArray competitors = new JsonArray();
    for (String competitor :
        List.of(first, second, DEFERRED, NON_MINIMUM_CONTROL).stream().distinct().toList()) {
      List<Score> matching =
          scores.stream().filter(score -> score.leaf().biome().equals(competitor)).toList();
      if (matching.isEmpty()) continue;
      Score best = matching.getFirst();
      JsonObject entry = new JsonObject();
      entry.addProperty("biome", competitor);
      entry.addProperty("entry_count", matching.size());
      entry.addProperty("minimum_fitness", best.fitness());
      entry.addProperty("is_global_minimum", best.fitness() == minimum);
      entry.addProperty("best_native_parameter_space", best.leaf().node().toString());
      competitors.add(entry);
    }
    result.add("recorded_competitors", competitors);
    result.addProperty(
        "both_recorded_IDs_are_global_minima", winners.contains(first) && winners.contains(second));
    return result;
  }

  private static Map<Integer, String> savedBiomes(ServerLevel level) {
    ChunkPos position = new ChunkPos(X >> 4, Z >> 4);
    CompoundTag chunk =
        level
            .getChunkSource()
            .chunkMap
            .read(position)
            .orTimeout(30, TimeUnit.SECONDS)
            .join()
            .orElseThrow(
                () -> new IllegalStateException("Missing saved witness chunk " + position));
    require(chunk.getString("Status").equals("minecraft:full"), "Witness chunk is not saved FULL");
    require(
        chunk.getInt("xPos") == position.x && chunk.getInt("zPos") == position.z,
        "Saved witness chunk coordinates differ");
    var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
    var codec =
        PalettedContainer.codecRO(
            registry.asHolderIdMap(),
            registry.holderByNameCodec(),
            PalettedContainer.Strategy.SECTION_BIOMES,
            registry.getHolderOrThrow(Biomes.PLAINS));
    Map<Integer, String> result = new LinkedHashMap<>();
    for (Tag element : chunk.getList("sections", Tag.TAG_COMPOUND)) {
      CompoundTag section = (CompoundTag) element;
      int sectionY = section.getByte("Y");
      if (sectionY < (-34 >> 4) || sectionY > (-10 >> 4)) continue;
      var palette = codec.parse(NbtOps.INSTANCE, section.getCompound("biomes")).getOrThrow();
      for (int y = -34; y <= -10; y += 4)
        if ((y >> 4) == sectionY)
          require(
              result.put(y, id(palette.get((X >> 2) & 3, (y >> 2) & 3, (Z >> 2) & 3))) == null,
              "Duplicate saved witness section");
    }
    require(result.size() == 7, "Missing saved witness biome sections");
    return result;
  }

  private static IExtendedParameterList<?> effective(MultiNoiseBiomeSource source)
      throws ReflectiveOperationException {
    Object parameters =
        Class.forName("com.terraformersmc.biolith.impl.biome.InterfaceBiomeSource")
            .getMethod("biolith$getBiomeEntries")
            .invoke(source);
    require(parameters instanceof IExtendedParameterList<?>, "Missing effective TerraBlender list");
    return (IExtendedParameterList<?>) parameters;
  }

  private static IslandBiomeSource ownedSource(BiomeSource source)
      throws ReflectiveOperationException {
    for (int depth = 0; depth < 8; depth++) {
      if (source instanceof IslandBiomeSource owned) return owned;
      Object inner;
      if (source.getClass().getName().equals(INJECTOR)) {
        inner = source.getClass().getMethod("directDelegate").invoke(source);
      } else if (source.getClass().getName().equals(BLUEPRINT)) {
        Class<?> injector = Class.forName(INJECTOR);
        inner =
            injector
                .getMethod("rootDelegate")
                .invoke(injector.getConstructor(BiomeSource.class).newInstance(source));
      } else
        throw new IllegalStateException("Unexpected biome wrapper " + source.getClass().getName());
      require(inner instanceof BiomeSource, "Wrapper returned no biome source");
      source = (BiomeSource) inner;
    }
    throw new IllegalStateException("Too many biome wrappers");
  }

  private static MultiNoiseBiomeSource delegate(IslandBiomeSource source)
      throws ReflectiveOperationException {
    Object delegate = field(IslandBiomeSource.class, "delegate").get(source);
    require(delegate instanceof MultiNoiseBiomeSource, "Owned delegate is not MultiNoise");
    return (MultiNoiseBiomeSource) delegate;
  }

  private static TreeSet<String> habitatIds(IslandBiomeSource source)
      throws ReflectiveOperationException {
    TreeSet<String> result = new TreeSet<>();
    result.add("aeroscapes_reimagine:void");
    for (String name : List.of("oceans", "deepOceans", "shores", "mountains", "voidBiome")) {
      Object value = field(IslandBiomeSource.class, name).get(source);
      if (value instanceof Holder<?> biome) result.add(id(biome));
      else if (value instanceof List<?> biomes) {
        for (Object entry : biomes) {
          require(entry instanceof Holder<?>, "Habitat list contains a non-holder");
          result.add(id((Holder<?>) entry));
        }
      } else
        require(value == null && name.equals("voidBiome"), "Invalid owned habitat field " + name);
    }
    return result;
  }

  private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static String id(Holder<?> biome) {
    require(biome.value() instanceof Biome, "Expected biome holder");
    return biome.unwrapKey().orElseThrow().location().toString();
  }

  private static JsonArray target(Climate.TargetPoint target) {
    JsonArray values = new JsonArray();
    for (long value :
        new long[] {
          target.temperature(),
          target.humidity(),
          target.continentalness(),
          target.erosion(),
          target.depth(),
          target.weirdness(),
          0L
        }) values.add(value);
    return values;
  }

  private static void write(Path output, JsonObject report) throws IOException {
    Files.createDirectories(output.getParent());
    Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
