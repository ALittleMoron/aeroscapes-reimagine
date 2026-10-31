package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Independent effective-data snapshots; also runs with the pre-runtime-repair owned artifact. */
final class ResourceSnapshotRegression {
  private ResourceSnapshotRegression() {}

  static void verify(ServerLevel level, String output) {
    Path path = Path.of(output).toAbsolutePath().normalize();
    require(
        Path.of(output).isAbsolute() && path.toString().contains("/.runtime/"),
        "resourceSnapshot must be absolute in ignored .runtime");
    try {
      JsonObject jsonIds = readFixture("/json-patches.json").getAsJsonObject();
      var templateIds =
          readFixture("/template-repairs.json").getAsJsonObject().getAsJsonArray("templates");
      require(
          jsonIds.size() == 256 && templateIds.size() == 925, "Incomplete snapshot fixture IDs");
      JsonObject json = new JsonObject();
      JsonObject registries = new JsonObject();
      for (String id : new TreeSet<>(jsonIds.keySet())) {
        ResourceLocation resource = ResourceLocation.parse(id);
        try (var reader = level.getServer().getResourceManager().openAsReader(resource)) {
          json.addProperty(id, ResourceFingerprints.json(JsonParser.parseReader(reader)));
        }
        snapshotRegistry(level, resource, registries);
      }
      JsonObject templates = new JsonObject();
      var manager = level.getStructureManager();
      for (JsonElement value : templateIds) {
        ResourceLocation id =
            ResourceLocation.parse(value.getAsJsonObject().get("id").getAsString());
        manager.remove(id);
        var template = manager.get(id).orElseThrow();
        require(!templates.has(id.toString()), "Duplicate snapshot template: " + id);
        templates.addProperty(
            id.toString(), ResourceFingerprints.nbt(template.save(new CompoundTag())));
      }
      JsonObject result = new JsonObject();
      result.addProperty("format", 1);
      result.addProperty("seed", level.getSeed());
      result.add("json", json);
      result.add("templates", templates);
      result.add("registries", registries);
      Files.createDirectories(path.getParent());
      Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
      System.out.println(
          "Resource snapshot regression passed: resources=256 templates=925 registries="
              + registries.size());
    } catch (IOException exception) {
      throw new IllegalStateException("Resource snapshot regression failed", exception);
    }
  }

  private static JsonElement readFixture(String name) throws IOException {
    try (var input = ResourceSnapshotRegression.class.getResourceAsStream(name)) {
      if (input == null) throw new IOException("Missing resource snapshot fixture " + name);
      return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static void snapshotRegistry(
      ServerLevel level, ResourceLocation resource, JsonObject output) {
    var access = level.registryAccess();
    var operations = RegistryOps.create(JsonOps.INSTANCE, access);
    String path = resource.getPath();
    for (String prefix :
        List.of(
            "worldgen/structure/",
            "worldgen/template_pool/",
            "worldgen/placed_feature/",
            "tags/worldgen/biome/")) {
      if (!path.startsWith(prefix)) continue;
      ResourceLocation id =
          ResourceLocation.fromNamespaceAndPath(
              resource.getNamespace(),
              path.substring(prefix.length(), path.length() - ".json".length()));
      JsonElement definition;
      switch (prefix) {
        case "worldgen/structure/" -> {
          var value = access.registryOrThrow(Registries.STRUCTURE).get(id);
          require(value != null, "Missing snapshot structure: " + id);
          definition =
              Structure.DIRECT_CODEC
                  .encodeStart(operations, Objects.requireNonNull(value))
                  .getOrThrow();
        }
        case "worldgen/template_pool/" -> {
          var value = access.registryOrThrow(Registries.TEMPLATE_POOL).get(id);
          require(value != null, "Missing snapshot pool: " + id);
          definition =
              StructureTemplatePool.DIRECT_CODEC
                  .encodeStart(operations, Objects.requireNonNull(value))
                  .getOrThrow();
        }
        case "worldgen/placed_feature/" -> {
          var value = access.registryOrThrow(Registries.PLACED_FEATURE).get(id);
          require(value != null, "Missing snapshot placed feature: " + id);
          definition =
              PlacedFeature.DIRECT_CODEC
                  .encodeStart(operations, Objects.requireNonNull(value))
                  .getOrThrow();
        }
        case "tags/worldgen/biome/" -> {
          var tag =
              access
                  .registryOrThrow(Registries.BIOME)
                  .getTag(TagKey.create(Registries.BIOME, id))
                  .orElseThrow();
          TreeSet<String> members = new TreeSet<>();
          tag.forEach(
              holder -> members.add(holder.unwrapKey().orElseThrow().location().toString()));
          JsonArray values = new JsonArray();
          members.forEach(values::add);
          definition = values;
        }
        default -> throw new IllegalStateException("Unrecognized snapshot registry: " + prefix);
      }
      output.addProperty(resource.toString(), ResourceFingerprints.json(definition));
      return;
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
