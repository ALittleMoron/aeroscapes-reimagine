package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** Captures actual native registry/placement wiring without requesting any chunks or tickets. */
public final class StructureInventoryRegression {
  private StructureInventoryRegression() {}

  public static void verify(MinecraftServer server, Path output) {
    if (!server.isSameThread())
      throw new IllegalStateException("Structure inventory must run on the owning server thread");
    new Inventory(server, output.toAbsolutePath().normalize()).write();
  }

  private static final class Inventory {
    private final MinecraftServer server;
    private final Path output;
    private final Registry<Structure> structures;
    private final Registry<StructureSet> sets;
    private final RegistryOps<JsonElement> operations;
    private final Map<StructurePlacement, List<String>> placementSets = new IdentityHashMap<>();
    private final Map<String, List<String>> memberSets = new TreeMap<>();
    private final Map<String, JsonArray> dimensionMembership = new TreeMap<>();
    private final JsonObject report = new JsonObject();
    private final JsonArray errors = new JsonArray();

    private Inventory(MinecraftServer server, Path output) {
      this.server = server;
      this.output = output;
      structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
      sets = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
      operations = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
      report.addProperty("format", 1);
      report.addProperty("world_seed", server.getWorldData().worldGenOptions().seed());
      report.addProperty("structure_registry_size", structures.size());
      report.addProperty("structure_set_registry_size", sets.size());
      report.addProperty(
          "scope",
          "Actual registered structures, sets and loaded-dimension native placement states."
              + " Possible-biome intersection and registered placements establish wiring only;"
              + " this snapshot requests no chunks/tickets, samples no biome positions and proves"
              + " no natural FULL start. Native ring initialization may schedule its ordinary"
              + " finite biome searches; this snapshot never waits for or changes ring futures.");
    }

    private void write() {
      RuntimeException failure = null;
      try {
        collectSets();
        collectDimensions();
        collectStructures();
        if (structures.size() == 0 || sets.size() == 0 || dimensionMembership.isEmpty())
          errors.add("Required structure/set/dimension inventory is empty");
      } catch (RuntimeException exception) {
        failure = exception;
        errors.add(exception.getClass().getName() + ": " + exception.getMessage());
      }
      report.add("errors", errors);
      report.addProperty("status", errors.isEmpty() ? "complete" : "error");
      try {
        Files.createDirectories(output.getParent());
        Files.writeString(
            output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot write structure inventory " + output, exception);
      }
      if (!errors.isEmpty())
        throw new IllegalStateException("Structure inventory failed; see " + output, failure);
      LogUtils.getLogger()
          .info(
              "Structure inventory regression passed: structures={} sets={} dimensions={} output={}",
              structures.size(),
              sets.size(),
              dimensionMembership.size(),
              output);
    }

    private void collectSets() {
      JsonArray records = new JsonArray();
      report.add("structure_sets", records);
      for (ResourceLocation id : sets.keySet().stream().sorted().toList()) {
        StructureSet set = sets.get(id);
        if (set == null) throw new IllegalStateException("Missing registered structure set " + id);
        String setId = id.toString();
        JsonObject record = new JsonObject();
        record.addProperty("id", setId);
        record.addProperty("class", set.getClass().getName());
        record.add("definition", encode(StructureSet.DIRECT_CODEC, set, "structure set " + setId));
        record.add("placement", placement(set.placement(), "structure set " + setId));
        placementSets.computeIfAbsent(set.placement(), ignored -> new ArrayList<>()).add(setId);
        JsonArray members = new JsonArray();
        int totalWeight = 0;
        for (var selection : set.structures()) {
          String structureId = structureId(selection.structure().value());
          JsonObject member = new JsonObject();
          member.addProperty("structure", structureId);
          member.addProperty("weight", selection.weight());
          members.add(member);
          memberSets.computeIfAbsent(structureId, ignored -> new ArrayList<>()).add(setId);
          if (selection.weight() <= 0) errors.add("Nonpositive structure weight in " + setId);
          totalWeight = Math.addExact(totalWeight, selection.weight());
        }
        if (set.structures().isEmpty()) errors.add("Empty registered structure set " + setId);
        record.addProperty("total_selection_weight", totalWeight);
        record.add("members", members);
        records.add(record);
      }
    }

    private void collectDimensions() {
      List<ServerLevel> levels = new ArrayList<>();
      server.getAllLevels().forEach(levels::add);
      levels.sort(Comparator.comparing(level -> level.dimension().location().toString()));
      JsonArray records = new JsonArray();
      report.add("dimensions", records);
      for (ServerLevel level : levels) {
        String dimension = level.dimension().location().toString();
        var generator = level.getChunkSource().getGenerator();
        BiomeSource source = generator.getBiomeSource();
        JsonObject record = new JsonObject();
        record.addProperty("id", dimension);
        record.addProperty("seed", level.getSeed());
        record.addProperty("min_y", level.getMinBuildHeight());
        record.addProperty("max_y_exclusive", level.getMaxBuildHeight());
        record.addProperty("sea_level", generator.getSeaLevel());
        record.addProperty("generator_class", generator.getClass().getName());
        record.add(
            "generator_definition",
            encode(ChunkGenerator.CODEC, generator, dimension + " generator"));
        record.addProperty("biome_source_class", source.getClass().getName());
        record.add(
            "biome_source_definition",
            encode(BiomeSource.CODEC, source, dimension + " biome source"));
        var possible = source.possibleBiomes();
        record.add("possible_biomes", biomeIds(possible.stream()));
        var state = level.getChunkSource().getGeneratorState();
        JsonArray possibleSets = new JsonArray();
        for (var holder : state.possibleStructureSets()) {
          String id = holder.unwrapKey().orElseThrow().location().toString();
          if (!sets.containsKey(ResourceLocation.parse(id)))
            errors.add(dimension + " has unknown possible structure set " + id);
          possibleSets.add(id);
        }
        record.add("possible_structure_sets", possibleSets);
        JsonArray memberships = new JsonArray();
        record.add("structures", memberships);
        for (ResourceLocation id : structures.keySet().stream().sorted().toList()) {
          Holder.Reference<Structure> holder = structures.getHolder(id).orElseThrow();
          JsonObject member = new JsonObject();
          member.addProperty("id", id.toString());
          JsonArray intersection =
              biomeIds(holder.value().biomes().stream().filter(possible::contains));
          member.add("possible_biome_intersection", intersection);
          JsonArray placements = new JsonArray();
          for (StructurePlacement value : state.getPlacementsForStructure(holder)) {
            JsonObject entry = placement(value, dimension + " structure " + id);
            List<String> owners = placementSets.get(value);
            if (owners == null) {
              errors.add(
                  dimension + " structure " + id + " has a placement absent from registered sets");
              owners = List.of();
            }
            var containingSets = memberSets.getOrDefault(id.toString(), List.of());
            List<String> selectedOwners = owners.stream().filter(containingSets::contains).toList();
            if (selectedOwners.isEmpty())
              errors.add(
                  dimension + " structure " + id + " placement has no matching set membership");
            entry.add("registered_structure_sets", strings(selectedOwners.stream()));
            placements.add(entry);
          }
          if (!placements.isEmpty() && intersection.isEmpty())
            errors.add(dimension + " structure " + id + " has placements without possible biomes");
          member.add("placements", placements);
          member.addProperty(
              "wiring_status",
              placements.isEmpty()
                  ? "no_placement_in_native_dimension_state"
                  : "placement_and_possible_biome_wired_only");
          memberships.add(member);
        }
        if (dimensionMembership.put(dimension, memberships) != null)
          errors.add("Duplicate loaded dimension " + dimension);
        records.add(record);
      }
    }

    private void collectStructures() {
      JsonArray records = new JsonArray();
      report.add("structures", records);
      for (ResourceLocation id : structures.keySet().stream().sorted().toList()) {
        Structure structure = structures.get(id);
        if (structure == null)
          throw new IllegalStateException("Missing registered structure " + id);
        JsonObject record = new JsonObject();
        record.addProperty("id", id.toString());
        record.addProperty("class", structure.getClass().getName());
        var type = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
        if (type == null) errors.add("Unregistered structure type for " + id);
        else record.addProperty("type", type.toString());
        record.add("definition", encode(Structure.DIRECT_CODEC, structure, "structure " + id));
        record.add("resolved_biomes", biomeIds(structure.biomes().stream()));
        record.addProperty("generation_step", structure.step().name());
        record.addProperty("terrain_adjustment", structure.terrainAdaptation().name());
        List<String> memberships = memberSets.getOrDefault(id.toString(), List.of());
        record.add("registered_structure_sets", strings(memberships.stream()));
        record.addProperty(
            "placement_status",
            memberships.isEmpty()
                ? "no_registered_structure_set_membership"
                : "registered_set_membership_only");
        records.add(record);
      }
      if (records.size() != structures.size())
        errors.add("Incomplete structure registry traversal");
    }

    private JsonObject placement(StructurePlacement placement, String owner) {
      JsonObject result = new JsonObject();
      result.addProperty("class", placement.getClass().getName());
      var type = BuiltInRegistries.STRUCTURE_PLACEMENT.getKey(placement.type());
      if (type == null) errors.add("Unregistered placement type for " + owner);
      else result.addProperty("type", type.toString());
      result.add("definition", encode(StructurePlacement.CODEC, placement, owner + " placement"));
      return result;
    }

    private <T> JsonObject encode(Codec<T> codec, T value, String owner) {
      JsonObject result = new JsonObject();
      try {
        var encoded = codec.encodeStart(operations, value);
        if (encoded.error().isPresent()) {
          String message = encoded.error().orElseThrow().message();
          result.addProperty("error", message);
          errors.add(owner + " serialization: " + message);
        } else if (encoded.result().isEmpty()) {
          result.addProperty("error", "Codec produced neither a complete result nor an error");
          errors.add(owner + " serialization has no complete result");
        } else result.add("value", encoded.result().orElseThrow());
      } catch (RuntimeException exception) {
        String message = exception.getClass().getName() + ": " + exception.getMessage();
        result.addProperty("error", message);
        errors.add(owner + " serialization: " + message);
      }
      return result;
    }

    private String structureId(Structure structure) {
      ResourceLocation id = structures.getKey(structure);
      if (id == null)
        throw new IllegalStateException("Set references an unregistered structure instance");
      return id.toString();
    }
  }

  private static JsonArray biomeIds(Stream<Holder<Biome>> biomes) {
    return strings(biomes.map(holder -> holder.unwrapKey().orElseThrow().location().toString()));
  }

  private static JsonArray strings(Stream<String> values) {
    JsonArray result = new JsonArray();
    new TreeSet<>(values.toList()).forEach(result::add);
    return result;
  }
}
