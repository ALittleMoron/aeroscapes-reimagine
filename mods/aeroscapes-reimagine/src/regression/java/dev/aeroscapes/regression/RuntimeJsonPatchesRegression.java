package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.worldgen.RuntimeJsonPatches;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.fml.ModList;

/**
 * Exercises recipe edits and actual resource-pack selection against the former JSON definitions.
 */
final class RuntimeJsonPatchesRegression {
  private RuntimeJsonPatchesRegression() {}

  static void verify(ServerLevel level) {
    try {
      JsonObject recipes = RuntimeJsonPatches.readRecipes();
      require(recipes.size() == 253, "Incomplete runtime JSON recipe closure");
      String baseline = System.getProperty("aeroscapes.regression.jsonRepairBaseline");
      JsonObject expected = baseline == null ? readExpected() : null;
      JsonObject report = new JsonObject();
      Map<ResourceLocation, Resource> listed = new HashMap<>();
      for (String prefix : List.of("worldgen", "advancement", "tags")) {
        listed.putAll(
            level
                .getServer()
                .getResourceManager()
                .listResources(prefix, id -> recipes.has(id.toString())));
      }
      require(listed.size() == recipes.size(), "Resource listing lost local repairs");
      for (var entry : recipes.entrySet()) {
        String id = entry.getKey();
        JsonObject recipe = entry.getValue().getAsJsonObject();
        var provider = ModList.get().getModFileById(recipe.get("mod").getAsString());
        byte[] original =
            Files.readAllBytes(
                Objects.requireNonNull(provider)
                    .getFile()
                    .findResource(recipe.get("source").getAsString()));
        JsonElement patched = RuntimeJsonPatches.apply(recipe, original);
        JsonElement effective;
        try (var reader =
            level.getServer().getResourceManager().openAsReader(ResourceLocation.parse(id))) {
          effective = JsonParser.parseReader(reader);
        }
        require(patched.equals(effective), "Virtual pack precedence differs: " + id);
        try (var reader = listed.get(ResourceLocation.parse(id)).openAsReader()) {
          require(
              patched.equals(JsonParser.parseReader(reader)), "Resource listing differs: " + id);
        }
        String fingerprint = ResourceFingerprints.json(effective);
        if (baseline != null) {
          String relative = "data/" + id.replace(':', '/');
          JsonElement previous =
              JsonParser.parseString(Files.readString(Path.of(baseline, relative)));
          require(effective.equals(previous), "Former override differs: " + id);
        } else {
          require(
              Objects.requireNonNull(expected).has(id)
                  && fingerprint.equals(expected.get(id).getAsString()),
              "Changed JSON result: " + id);
        }
        report.addProperty(id, fingerprint);
        byte[] changed = original.clone();
        changed[0] ^= 1;
        try {
          RuntimeJsonPatches.apply(recipe, changed);
          throw new IllegalStateException("Changed provider accepted: " + id);
        } catch (IOException rejected) {
          require(rejected.getMessage().contains("Re-audit"), "Wrong source rejection: " + id);
        }
      }
      String output = System.getProperty("aeroscapes.regression.jsonRepairReport");
      if (output != null)
        Files.writeString(
            Path.of(output), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      System.out.println("Local JSON repair regression passed: resources=" + recipes.size());
    } catch (IOException error) {
      throw new IllegalStateException("Local JSON repair regression failed", error);
    }
  }

  private static JsonObject readExpected() throws IOException {
    try (var input = RuntimeJsonPatchesRegression.class.getResourceAsStream("/json-patches.json")) {
      if (input == null) throw new IOException("Missing JSON parity fixture");
      return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
