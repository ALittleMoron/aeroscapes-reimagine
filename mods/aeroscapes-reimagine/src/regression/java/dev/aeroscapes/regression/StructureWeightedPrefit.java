package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Template-only replay of the native weighted removal loop; never a placement witness. */
public final class StructureWeightedPrefit {
  private static final Map<String, String> ROOT_CLASSES =
      Map.of(
          "dev.worldgen.lithostitched.worldgen.structure.DelegatingStructure",
              "lithostitched:delegating",
          "dev.aeroscapes.worldgen.SupportedStructure", "aeroscapes_reimagine:supported",
          "dev.aeroscapes.worldgen.RootSupportedStructure", "aeroscapes_reimagine:root_supported",
          "net.minecraft.world.level.levelgen.structure.structures.JigsawStructure",
              "minecraft:jigsaw",
          "net.mehvahdjukaar.supplementaries.common.worldgen.RoadSignStructure",
              "supplementaries:road_sign");
  private static final Set<String> SURFACE_CHECKS =
      Set.of("surface", "grounded_surface", "anchored_complex");
  private static final Set<String> PROJECTIONS = Set.of("WORLD_SURFACE_WG", "OCEAN_FLOOR_WG");

  private StructureWeightedPrefit() {}

  public record Outcome(boolean selected, JsonObject evidence) {}

  public static Outcome evaluate(
      ServerLevel level,
      List<StructureSet.StructureSelectionEntry> members,
      Structure target,
      ChunkPos origin,
      Structure.GenerationStub targetStub) {
    if (!level.getServer().isDedicatedServer() || !level.getServer().isSameThread())
      throw new IllegalArgumentException("Weighted prefit needs the dedicated server thread");
    if (members.isEmpty()) throw new IllegalArgumentException("Weighted prefit needs members");
    if (targetStub == null) throw new IllegalArgumentException("Weighted prefit needs target stub");
    List<StructureSet.StructureSelectionEntry> remaining = new ArrayList<>(members);
    Set<Structure> unique = new HashSet<>();
    JsonArray chains = new JsonArray();
    int totalWeight = 0;
    for (var member : members) {
      Structure root = member.structure().value();
      if (member.weight() <= 0 || !unique.add(root))
        throw new IllegalArgumentException("Expected positive weights and distinct native roots");
      totalWeight = Math.addExact(totalWeight, member.weight());
      chains.add(validate(level, root));
    }
    if (!unique.contains(target))
      throw new IllegalArgumentException("Target is not an actual native set member");
    long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
    WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
    random.setLargeFeatureSeed(seed, origin.x, origin.z);
    JsonObject evidence = new JsonObject();
    evidence.addProperty("proof_kind", "native_weighted_template_prefit_not_placement");
    evidence.addProperty("structure_generation_seed", seed);
    evidence.addProperty("origin_chunk_x", origin.x);
    evidence.addProperty("origin_chunk_z", origin.z);
    evidence.addProperty("target", identifier(level, target));
    evidence.addProperty("member_count", members.size());
    evidence.addProperty(
        "scope",
        "Fresh-candidate template prediction only, after the caller's actual placement check."
            + " No existing-start lookup, chunks, tickets or block/entity placement."
            + " The supplied target stub must be the unconsumed native prefit for this origin."
            + " Actual native STARTS/FULL and observed commits remain required.");
    evidence.add("validated_member_chains", chains);
    JsonArray visited = new JsonArray();
    evidence.add("visited_members", visited);
    while (!remaining.isEmpty()) {
      int draw = members.size() == 1 ? 0 : random.nextInt(totalWeight);
      int index = 0;
      for (var member : remaining) {
        draw -= member.weight();
        if (draw < 0) break;
        index++;
      }
      var member = remaining.get(index);
      Structure root = member.structure().value();
      JsonObject attempt = new JsonObject();
      attempt.addProperty("structure", identifier(level, root));
      attempt.addProperty("weight", member.weight());
      attempt.addProperty("target", root == target);
      JsonArray predicates = new JsonArray();
      attempt.add("biome_predicate_calls", predicates);
      visited.add(attempt);
      Optional<Structure.GenerationStub> stub;
      if (root == target) {
        stub = Optional.of(targetStub);
        attempt.addProperty("prefit_status", "provided_native_target_stub");
        attempt.addProperty("biome_status", "accepted_by_callers_native_target_prefit");
      } else {
        var context =
            new Structure.GenerationContext(
                level.registryAccess(),
                level.getChunkSource().getGenerator(),
                level.getChunkSource().getGenerator().getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getStructureManager(),
                seed,
                origin,
                level,
                biome -> {
                  boolean accepted = root.biomes().contains(biome);
                  JsonObject call = new JsonObject();
                  call.addProperty("biome", biome.unwrapKey().orElseThrow().location().toString());
                  call.addProperty("accepted", accepted);
                  predicates.add(call);
                  return accepted;
                });
        stub = root.findValidGenerationPoint(context);
        attempt.addProperty("prefit_status", stub.isPresent() ? "stub_present" : "stub_absent");
        attempt.addProperty(
            "biome_status",
            predicates.isEmpty()
                ? "no_observed_predicate_call"
                : predicates.asList().stream()
                        .allMatch(call -> call.getAsJsonObject().get("accepted").getAsBoolean())
                    ? "all_observed_calls_accepted"
                    : "observed_rejection");
      }
      boolean valid = false;
      if (stub.isPresent()) {
        var pieces = stub.orElseThrow().getPiecesBuilder().build();
        valid = !pieces.isEmpty();
        attempt.addProperty("piece_count", pieces.pieces().size());
        attempt.addProperty("pieces_status", valid ? "nonempty" : "empty");
      } else attempt.addProperty("pieces_status", "not_assembled_without_stub");
      if (valid) {
        evidence.addProperty("selected_structure", identifier(level, root));
        evidence.addProperty("selected_target", root == target);
        evidence.addProperty("termination", "first_valid_native_weighted_member");
        return new Outcome(root == target, evidence);
      }
      remaining.remove(index);
      totalWeight -= member.weight();
    }
    evidence.addProperty("selected_target", false);
    evidence.addProperty("termination", "all_native_weighted_members_rejected");
    return new Outcome(false, evidence);
  }

  private static JsonObject validate(ServerLevel level, Structure root) {
    String expectedType = ROOT_CLASSES.get(root.getClass().getName());
    if (expectedType == null)
      throw new IllegalArgumentException("Unreviewed weighted root class " + root.getClass());
    var operations = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    JsonObject definition =
        Structure.DIRECT_CODEC.encodeStart(operations, root).getOrThrow().getAsJsonObject();
    if (!text(definition, "type").equals(expectedType))
      throw new IllegalArgumentException("Weighted root class/codec mismatch");
    JsonArray types = new JsonArray();
    validateChain(definition, types, 0);
    JsonObject result = new JsonObject();
    result.addProperty("structure", identifier(level, root));
    result.addProperty("root_class", root.getClass().getName());
    result.add("types", types);
    return result;
  }

  private static void validateChain(JsonObject definition, JsonArray types, int depth) {
    if (depth > 3) throw new IllegalArgumentException("Unreviewed weighted wrapper depth");
    String type = text(definition, "type");
    types.add(type);
    switch (type) {
      case "lithostitched:delegating" -> {
        if (definition.has("spawn_condition")) {
          JsonObject condition = definition.getAsJsonObject("spawn_condition");
          if (!text(condition, "type").equals("lithostitched:height_filter")
              || !text(condition, "range_type").equals("absolute")
              || condition.has("heightmap"))
            throw new IllegalArgumentException("Unreviewed weighted spawn condition");
        }
        validateChain(object(definition, "delegate"), types, depth + 1);
      }
      case "aeroscapes_reimagine:supported" -> {
        if (!SURFACE_CHECKS.contains(text(definition, "terrain_check")))
          throw new IllegalArgumentException("Unreviewed weighted terrain check");
        validateChain(object(definition, "delegate"), types, depth + 1);
      }
      case "aeroscapes_reimagine:root_supported" ->
          validateChain(object(definition, "delegate"), types, depth + 1);
      case "minecraft:jigsaw", "lithostitched:jigsaw" -> {
        ResourceLocation.parse(text(definition, "start_pool"));
        JsonObject height = object(definition, "start_height");
        if (height.size() != 1
            || !height.has("absolute")
            || !height.get("absolute").isJsonPrimitive()
            || !height.getAsJsonPrimitive("absolute").isNumber())
          throw new IllegalArgumentException("Unreviewed weighted start height");
        if (definition.has("project_start_to_heightmap")
            && !PROJECTIONS.contains(text(definition, "project_start_to_heightmap")))
          throw new IllegalArgumentException("Unreviewed weighted projection");
        if (definition.has("start_projection")
            && !text(definition, "start_projection").equals("WORLD_SURFACE_WG"))
          throw new IllegalArgumentException("Unreviewed weighted alternate projection");
        if (definition.has("pool_aliases"))
          for (JsonElement alias : definition.getAsJsonArray("pool_aliases"))
            validateAlias(alias.getAsJsonObject());
      }
      case "supplementaries:road_sign" -> {
        if (depth != 0) throw new IllegalArgumentException("Unreviewed nested road sign root");
        ResourceLocation.parse(text(definition, "start_pool"));
      }
      default -> throw new IllegalArgumentException("Unreviewed weighted structure type " + type);
    }
  }

  private static void validateAlias(JsonObject alias) {
    switch (text(alias, "type")) {
      case "minecraft:direct" -> {
        ResourceLocation.parse(text(alias, "alias"));
        ResourceLocation.parse(text(alias, "target"));
      }
      case "minecraft:random_group" -> {
        for (JsonElement group : alias.getAsJsonArray("groups"))
          for (JsonElement member : group.getAsJsonObject().getAsJsonArray("data"))
            validateAlias(member.getAsJsonObject());
      }
      default -> throw new IllegalArgumentException("Unreviewed weighted pool alias");
    }
  }

  private static String identifier(ServerLevel level, Structure root) {
    var key = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(root);
    if (key == null) throw new IllegalArgumentException("Unregistered weighted root");
    return key.toString();
  }

  private static JsonObject object(JsonObject definition, String key) {
    JsonElement value = definition.get(key);
    if (value == null || !value.isJsonObject())
      throw new IllegalArgumentException("Expected inline weighted definition " + key);
    return value.getAsJsonObject();
  }

  private static String text(JsonObject definition, String key) {
    JsonElement value = definition.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException("Expected weighted identifier " + key);
    return value.getAsString();
  }
}
