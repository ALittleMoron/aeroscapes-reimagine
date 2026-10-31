package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import dev.aeroscapes.worldgen.StructureBiomeCompatibility;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/** Bounded raw-marker explanations for the ten audited unresolved surface roots. */
public final class NativeRootForensics {
  private static final String ALTERNATE =
      "dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawStructure";
  private static final Set<String> TARGETS =
      Set.of(
          "ctov:large/village_mushroom",
          "ctov:medium/village_mushroom",
          "ctov:small/village_mushroom",
          "ctov:large/village_snowy_igloo",
          "ctov:large/village_taiga_fortified",
          "ctov:medium/village_mesa",
          "natures_spirit:village_coconut",
          "nova_structures:remnant_mud_brick_constructor",
          "terralith:igloo",
          "terralith:mage_complex");
  private static final int EXAMPLES_PER_CATEGORY = 8;
  private static final int ASSEMBLY_LIMIT = 16;
  private static final int GENERAL_ASSEMBLY_SAMPLES = 4;

  private record HeightRange(int minimum, int maximum) {}

  private final ServerLevel level;
  private final Structure root;
  private final Structure leaf;
  private final JsonObject leafDefinition;
  private final Method alternateMethod;
  private final int limit;
  private final List<HeightRange> heightRanges = new ArrayList<>();
  private final Map<String, Integer> counts = new TreeMap<>();
  private final Map<String, JsonArray> examples = new TreeMap<>();
  private final boolean surfaceSupport;
  private int examined;
  private int assemblies;
  private int generalAssemblies;
  private int skippedAfterLimit;

  public NativeRootForensics(
      ServerLevel level, String id, Structure root, JsonObject definition, int limit) {
    if (!level.getServer().isDedicatedServer() || !level.getServer().isSameThread())
      throw new IllegalArgumentException("Root forensics needs the dedicated server thread");
    if (!TARGETS.contains(id) || limit < 1 || limit > 8192)
      throw new IllegalArgumentException("Unaudited or unbounded root forensic target " + id);
    this.level = level;
    this.root = root;
    this.limit = limit;
    String expectedRootClass =
        switch (text(definition, "type")) {
          case "lithostitched:delegating" ->
              "dev.worldgen.lithostitched.worldgen.structure.DelegatingStructure";
          case "aeroscapes_reimagine:supported" -> "dev.aeroscapes.worldgen.SupportedStructure";
          default -> throw new IllegalArgumentException("Unaudited forensic registered root type");
        };
    if (!root.getClass().getName().equals(expectedRootClass))
      throw new IllegalArgumentException("Forensic registered root class/codec mismatch");
    JsonObject current = definition;
    boolean support = false;
    for (int depth = 0; ; depth++) {
      if (depth > 3) throw new IllegalArgumentException("Unaudited forensic wrapper depth");
      String type = text(current, "type");
      if (type.equals("lithostitched:delegating")) {
        if (current.has("spawn_condition")) {
          JsonObject condition = object(current, "spawn_condition");
          if (!text(condition, "type").equals("lithostitched:height_filter")
              || !text(condition, "range_type").equals("absolute")
              || condition.has("heightmap"))
            throw new IllegalArgumentException("Unaudited forensic spawn condition");
          JsonElement range = condition.get("permitted_range");
          if (range == null || !range.isJsonArray() || range.getAsJsonArray().size() != 2)
            throw new IllegalArgumentException("Expected exact forensic absolute range");
          int minimum = integer(range.getAsJsonArray().get(0));
          int maximum = integer(range.getAsJsonArray().get(1));
          if (minimum > maximum) throw new IllegalArgumentException("Reversed forensic range");
          heightRanges.add(new HeightRange(minimum, maximum));
        }
      } else if (type.equals("aeroscapes_reimagine:supported")) {
        if (!text(current, "terrain_check").equals("surface") || support)
          throw new IllegalArgumentException("Unaudited forensic terrain guard");
        support = true;
      } else if (type.equals("minecraft:jigsaw") || type.equals("lithostitched:jigsaw")) {
        break;
      } else throw new IllegalArgumentException("Unaudited forensic wrapper/leaf " + type);
      current = object(current, "delegate");
    }
    surfaceSupport = support;
    leafDefinition = current.deepCopy();
    if (leafDefinition.has("start_jigsaw_name"))
      throw new IllegalArgumentException("Unaudited forensic named start");
    JsonObject startHeight = object(leafDefinition, "start_height");
    if (startHeight.size() != 1)
      throw new IllegalArgumentException("Unaudited forensic start height");
    integer(startHeight.get("absolute"));
    for (String projection : List.of("project_start_to_heightmap", "start_projection"))
      if (leafDefinition.has(projection)
          && !Set.of("WORLD_SURFACE_WG", "OCEAN_FLOOR_WG")
              .contains(text(leafDefinition, projection)))
        throw new IllegalArgumentException("Unaudited forensic start projection");
    var operations = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    leaf = Structure.DIRECT_CODEC.parse(operations, leafDefinition).getOrThrow();
    if (!Structure.DIRECT_CODEC.encodeStart(operations, leaf).getOrThrow().equals(leafDefinition))
      throw new IllegalArgumentException("Decoded forensic leaf changed its definition");
    String type = text(leafDefinition, "type");
    if (type.equals("minecraft:jigsaw") && leaf.getClass() == JigsawStructure.class) {
      alternateMethod = null;
    } else if (type.equals("lithostitched:jigsaw") && leaf.getClass().getName().equals(ALTERNATE)) {
      try {
        alternateMethod =
            leaf.getClass().getMethod("findGenerationPoint", Structure.GenerationContext.class);
      } catch (NoSuchMethodException exception) {
        throw new IllegalArgumentException(
            "Missing audited public alternate raw-leaf API", exception);
      }
      if (!Modifier.isPublic(alternateMethod.getModifiers())
          || alternateMethod.getDeclaringClass() != leaf.getClass()
          || alternateMethod.getReturnType() != Optional.class)
        throw new IllegalArgumentException("Alternate raw-leaf API differs from its audit");
    } else
      throw new IllegalArgumentException("Forensic leaf class/codec mismatch " + leaf.getClass());
    Set<Holder<Biome>> actualBiomes = Set.copyOf(root.biomes().stream().toList());
    if (!actualBiomes.equals(Set.copyOf(leaf.biomes().stream().toList())))
      throw new IllegalArgumentException("Forensic leaf/root biome sets differ");
  }

  public void observe(
      ChunkPos origin, Optional<BlockPos> actualMarker, JsonArray actualPredicates) {
    if (examined >= limit) {
      skippedAfterLimit++;
      return;
    }
    examined++;
    JsonObject row = new JsonObject();
    row.addProperty("chunk_x", origin.x);
    row.addProperty("chunk_z", origin.z);
    row.addProperty("actual_root_stub_present", actualMarker.isPresent());
    actualMarker.ifPresent(marker -> row.add("actual_root_marker", position(marker)));
    row.add("actual_root_biome_predicate_calls", actualPredicates.deepCopy());
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
            root.biomes()::contains);
    Optional<Structure.GenerationStub> raw = rawStub(context);
    if (raw.isEmpty()) {
      retain(
          actualMarker.isPresent() ? "raw_leaf_absent_actual_root_present" : "raw_leaf_stub_absent",
          row);
      return;
    }
    Structure.GenerationStub stub = raw.orElseThrow();
    BlockPos marker = stub.position();
    row.add("raw_leaf_marker", position(marker));
    JsonObject quart = new JsonObject();
    quart.addProperty("x", QuartPos.fromBlock(marker.getX()));
    quart.addProperty("y", QuartPos.fromBlock(marker.getY()));
    quart.addProperty("z", QuartPos.fromBlock(marker.getZ()));
    row.add("raw_leaf_marker_quart", quart);
    Climate.Sampler sampler = context.randomState().sampler();
    IslandBiomeSampler binding = (IslandBiomeSampler) (Object) sampler;
    var originalMask = binding.aeroscapesReimagine$terrainMask();
    boolean originalDefer = binding.aeroscapesReimagine$deferTerrainMask();
    Climate.Sampler deferred = StructureBiomeCompatibility.deferredSampler(sampler);
    if (binding.aeroscapesReimagine$terrainMask() != originalMask
        || binding.aeroscapesReimagine$deferTerrainMask() != originalDefer
        || (originalMask != null && !originalDefer && deferred == sampler))
      throw new IllegalStateException("Forensic control changed the normal sampler context");
    row.addProperty("normal_sampler_binding_and_defer_preserved", true);
    row.addProperty("deferred_control_is_independent_copy", deferred != sampler);
    Holder<Biome> normalBiome = biome(context, marker, sampler);
    Holder<Biome> deferredBiome = biome(context, marker, deferred);
    boolean normalAccepted = root.biomes().contains(normalBiome);
    boolean deferredAccepted = root.biomes().contains(deferredBiome);
    row.addProperty("normal_marker_biome", biomeId(normalBiome));
    row.addProperty("normal_marker_biome_accepted", normalAccepted);
    row.addProperty("deferred_control_marker_biome", biomeId(deferredBiome));
    row.addProperty("deferred_control_biome_accepted", deferredAccepted);
    JsonArray vertical = new JsonArray();
    for (int offset : new int[] {-8, -4, -1, 0, 1, 4, 8}) {
      BlockPos point = marker.offset(0, offset, 0);
      JsonObject sample = position(point);
      sample.addProperty("relative_y", offset);
      sample.addProperty("quart_y", QuartPos.fromBlock(point.getY()));
      Holder<Biome> selected = biome(context, point, sampler);
      sample.addProperty("normal_biome", biomeId(selected));
      sample.addProperty("normal_biome_accepted", root.biomes().contains(selected));
      vertical.add(sample);
    }
    row.add("normal_vertical_biome_samples", vertical);
    JsonObject heights = new JsonObject();
    for (Heightmap.Types heightmap :
        List.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG))
      heights.addProperty(
          heightmap.getSerializationKey(),
          context
              .chunkGenerator()
              .getFirstFreeHeight(
                  marker.getX(),
                  marker.getZ(),
                  heightmap,
                  context.heightAccessor(),
                  context.randomState()));
    heights.addProperty(
        "configured_start_offset", integer(object(leafDefinition, "start_height").get("absolute")));
    row.add("native_first_free_heights", heights);
    boolean heightAccepted = true;
    JsonArray ranges = new JsonArray();
    for (HeightRange range : heightRanges) {
      boolean accepted = marker.getY() >= range.minimum && marker.getY() <= range.maximum;
      heightAccepted &= accepted;
      JsonObject hint = new JsonObject();
      hint.addProperty("minimum_inclusive", range.minimum);
      hint.addProperty("maximum_inclusive", range.maximum);
      hint.addProperty("marker_y_accepted", accepted);
      ranges.add(hint);
    }
    row.add("independent_absolute_height_hints_not_native_call_trace", ranges);
    JsonObject assembly = null;
    boolean interesting = actualMarker.isEmpty() && (normalAccepted || deferredAccepted);
    if (assemblies < ASSEMBLY_LIMIT
        && (interesting || generalAssemblies < GENERAL_ASSEMBLY_SAMPLES)) {
      assemblies++;
      if (!interesting) generalAssemblies++;
      assembly = assembly(context, stub);
      row.add("raw_leaf_assembly_hints", assembly);
    }
    String category;
    if (actualMarker.isPresent())
      category =
          actualMarker.orElseThrow().equals(marker)
              ? "actual_root_accepted"
              : "actual_root_raw_leaf_marker_disagreement";
    else if (!normalAccepted)
      category =
          deferredAccepted
              ? "normal_biome_rejected_deferred_accepts"
              : "normal_marker_biome_rejected";
    else if (!heightAccepted) category = "biome_accepted_absolute_height_rejected_hint";
    else if (surfaceSupport
        && assembly != null
        && !assembly.get("all_piece_bounds_inside_build_height").getAsBoolean())
      category = "biome_height_accepted_assembly_bounds_rejected_hint";
    else if (assembly == null) category = "biome_height_accepted_assembly_not_sampled";
    else if (assembly.get("piece_count").getAsInt() == 0) category = "raw_leaf_assembly_empty";
    else if (assembly.has("surface_support_hint")
        && !assembly.getAsJsonObject("surface_support_hint").get("accepted").getAsBoolean())
      category = "biome_height_accepted_surface_support_rejected_hint";
    else category = "actual_root_rejection_unexplained_by_sampled_hints";
    retain(category, row);
  }

  private JsonObject assembly(Structure.GenerationContext context, Structure.GenerationStub stub) {
    var pieces = stub.getPiecesBuilder().build();
    JsonObject result = new JsonObject();
    result.addProperty("piece_count", pieces.pieces().size());
    boolean inside =
        pieces.pieces().stream()
            .allMatch(
                piece ->
                    !context.heightAccessor().isOutsideBuildHeight(piece.getBoundingBox().minY())
                        && !context
                            .heightAccessor()
                            .isOutsideBuildHeight(piece.getBoundingBox().maxY()));
    result.addProperty("all_piece_bounds_inside_build_height", inside);
    if (pieces.isEmpty()) return result;
    StructurePiece first = pieces.pieces().getFirst();
    result.add("bounds", bounds(pieces.calculateBoundingBox()));
    result.add("first_piece_bounds", bounds(first.getBoundingBox()));
    result.addProperty("first_piece_class", first.getClass().getName());
    if (!(first instanceof PoolElementStructurePiece pool))
      throw new IllegalStateException("Audited raw jigsaw leaf returned a non-pool first piece");
    result.addProperty("first_piece_rotation", pool.getRotation().name());
    result.add("first_piece_position", position(pool.getPosition()));
    result.addProperty("first_piece_element", pool.getElement().toString());
    result.addProperty("first_piece_ground_level_delta", pool.getGroundLevelDelta());
    if (surfaceSupport) {
      int groundY = first.getBoundingBox().minY() + pool.getGroundLevelDelta();
      BlockPos center = first.getBoundingBox().getCenter();
      int floorHeight =
          context
              .chunkGenerator()
              .getBaseHeight(
                  center.getX(),
                  center.getZ(),
                  Heightmap.Types.OCEAN_FLOOR_WG,
                  context.heightAccessor(),
                  context.randomState());
      int supportY = Math.max(groundY - 1, Math.min(groundY, floorHeight));
      var column =
          context
              .chunkGenerator()
              .getBaseColumn(
                  center.getX(), center.getZ(), context.heightAccessor(), context.randomState());
      JsonObject hint = new JsonObject();
      hint.addProperty(
          "scope",
          "Source-matched surface guard hint; never changes or proves the actual root result");
      hint.addProperty("ground_y", groundY);
      hint.addProperty("support_y", supportY);
      hint.addProperty("x", center.getX());
      hint.addProperty("z", center.getZ());
      boolean accepted =
          !context.heightAccessor().isOutsideBuildHeight(supportY - 4)
              && !context.heightAccessor().isOutsideBuildHeight(supportY);
      JsonArray samples = new JsonArray();
      for (int y : new int[] {supportY - 1, supportY - 4}) {
        BlockState state = column.getBlock(y);
        boolean solid =
            state.getFluidState().isEmpty()
                && Block.isFaceFull(
                    state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                    Direction.UP);
        accepted &= solid;
        JsonObject sample = new JsonObject();
        sample.addProperty("y", y);
        sample.addProperty("state", state.toString());
        sample.addProperty("fluid_free_full_upward_face", solid);
        samples.add(sample);
      }
      hint.add("samples", samples);
      hint.addProperty("accepted", accepted);
      result.add("surface_support_hint", hint);
    }
    return result;
  }

  private Optional<Structure.GenerationStub> rawStub(Structure.GenerationContext context) {
    if (leaf instanceof JigsawStructure nativeLeaf) return nativeLeaf.findGenerationPoint(context);
    try {
      Object returned = alternateMethod.invoke(leaf, context);
      if (!(returned instanceof Optional<?> optional))
        throw new IllegalStateException("Alternate raw-leaf result is not Optional");
      if (optional.isEmpty()) return Optional.empty();
      if (!(optional.orElseThrow() instanceof Structure.GenerationStub stub))
        throw new IllegalStateException("Alternate raw-leaf result is not a generation stub");
      return Optional.of(stub);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Cannot invoke audited public alternate API", exception);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtime) throw runtime;
      if (cause instanceof Error error) throw error;
      throw new IllegalStateException("Audited alternate raw-leaf call failed", cause);
    }
  }

  private void retain(String category, JsonObject row) {
    counts.merge(category, 1, Integer::sum);
    JsonArray samples = examples.computeIfAbsent(category, ignored -> new JsonArray());
    if (samples.size() < EXAMPLES_PER_CATEGORY) samples.add(row);
  }

  public JsonObject report() {
    JsonObject result = new JsonObject();
    result.addProperty(
        "scope",
        "Independent fresh-context decoded-leaf diagnostics only; actual root and weighted stub are untouched. No placement, chunks or world writes. Deferred biomes and support calculations are explanatory controls, never eligibility overrides.");
    result.addProperty("candidates_examined", examined);
    result.addProperty("candidates_skipped_after_limit", skippedAfterLimit);
    result.addProperty("candidate_limit", limit);
    result.addProperty("assemblies_evaluated", assemblies);
    result.addProperty("assembly_limit", ASSEMBLY_LIMIT);
    result.addProperty("general_assembly_sample_limit", GENERAL_ASSEMBLY_SAMPLES);
    result.addProperty("examples_per_category_limit", EXAMPLES_PER_CATEGORY);
    result.addProperty("leaf_class", leaf.getClass().getName());
    result.add("decoded_unchanged_leaf_definition", leafDefinition.deepCopy());
    JsonObject totals = new JsonObject();
    counts.forEach(totals::addProperty);
    result.add("category_counts", totals);
    JsonObject samples = new JsonObject();
    examples.forEach(samples::add);
    result.add("examples_by_category", samples);
    return result;
  }

  private static Holder<Biome> biome(
      Structure.GenerationContext context, BlockPos position, Climate.Sampler sampler) {
    return context
        .chunkGenerator()
        .getBiomeSource()
        .getNoiseBiome(
            QuartPos.fromBlock(position.getX()),
            QuartPos.fromBlock(position.getY()),
            QuartPos.fromBlock(position.getZ()),
            sampler);
  }

  private static String biomeId(Holder<Biome> biome) {
    return biome.unwrapKey().orElseThrow().location().toString();
  }

  private static JsonObject position(BlockPos position) {
    JsonObject result = new JsonObject();
    result.addProperty("x", position.getX());
    result.addProperty("y", position.getY());
    result.addProperty("z", position.getZ());
    return result;
  }

  private static JsonArray bounds(net.minecraft.world.level.levelgen.structure.BoundingBox bounds) {
    JsonArray result = new JsonArray();
    for (int value :
        new int[] {
          bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()
        }) result.add(value);
    return result;
  }

  private static JsonObject object(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonObject())
      throw new IllegalArgumentException("Expected forensic definition object " + key);
    return value.getAsJsonObject();
  }

  private static String text(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException("Expected forensic definition text " + key);
    return value.getAsString();
  }

  private static int integer(JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException("Expected exact forensic definition integer");
    return value.getAsBigDecimal().intValueExact();
  }
}
