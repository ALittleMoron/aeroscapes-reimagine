package dev.aeroscapes.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Applies project-authored field edits to verified, locally installed provider resources. */
public final class RuntimeJsonPatches {
  private RuntimeJsonPatches() {}

  public static JsonObject readRecipes() throws IOException {
    try (InputStream input =
        RuntimeJsonPatches.class.getResourceAsStream("/aeroscapes_reimagine/data-patches.json")) {
      if (input == null) throw new IOException("Missing runtime data patch recipes");
      try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        JsonObject recipes = new JsonObject();
        for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
          JsonObject recipe = element.getAsJsonObject();
          String id = recipe.get("id").getAsString();
          if (recipes.has(id)) throw new IOException("Duplicate runtime data patch: " + id);
          recipes.add(id, recipe);
        }
        return recipes;
      }
    }
  }

  public static JsonElement apply(JsonObject recipe, byte[] original) throws IOException {
    if (!sha256(original).equals(recipe.get("sha256").getAsString()))
      throw new IOException("Re-audit changed provider resource: " + recipe.get("id"));
    JsonElement result = JsonParser.parseString(new String(original, StandardCharsets.UTF_8));
    for (JsonElement value : recipe.getAsJsonArray("edits")) {
      JsonObject edit = value.getAsJsonObject();
      var path = edit.getAsJsonArray("path");
      if (path.isEmpty()) {
        result = edit.get("value").deepCopy();
        continue;
      }
      JsonElement parent = result;
      for (int index = 0; index < path.size() - 1; index++) {
        String key = path.get(index).getAsString();
        parent =
            parent.isJsonArray()
                ? parent.getAsJsonArray().get(Integer.parseInt(key))
                : parent.getAsJsonObject().get(key);
      }
      String key = path.get(path.size() - 1).getAsString();
      String operation = edit.get("op").getAsString();
      JsonElement replacement = edit.has("value") ? edit.get("value").deepCopy() : null;
      if (parent.isJsonArray()) {
        var array = parent.getAsJsonArray();
        int index = Integer.parseInt(key);
        switch (operation) {
          case "remove" -> array.remove(index);
          case "set" -> array.set(index, replacement);
          case "insert" -> array.asList().add(index, replacement);
          default -> throw new IOException("Unknown runtime edit: " + operation);
        }
      } else {
        var object = parent.getAsJsonObject();
        switch (operation) {
          case "remove" -> object.remove(key);
          case "set" -> object.add(key, replacement);
          default -> throw new IOException("Unknown runtime object edit: " + operation);
        }
      }
    }
    if (recipe.has("wrappers")) {
      var wrappers = recipe.getAsJsonArray("wrappers");
      for (int index = wrappers.size() - 1; index >= 0; index--) {
        JsonObject wrapper = wrappers.get(index).getAsJsonObject().deepCopy();
        wrapper.add("delegate", result);
        result = wrapper;
      }
    }
    return result;
  }

  public static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("JVM has no SHA-256", error);
    }
  }
}
