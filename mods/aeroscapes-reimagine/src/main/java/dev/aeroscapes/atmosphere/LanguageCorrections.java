package dev.aeroscapes.atmosphere;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repairs verified provider strings after namespace merging, preserving resource-pack replacements.
 */
public final class LanguageCorrections {
  private static final List<Correction> CORRECTIONS = loadCorrections();

  private LanguageCorrections() {}

  public static Map<String, String> apply(
      Map<String, String> translations, List<String> languages) {
    if (languages.isEmpty()) {
      return Map.copyOf(translations);
    }
    Map<String, String> corrected = new HashMap<>(translations);
    for (Correction correction : CORRECTIONS) {
      if (correction.language().equals(languages.getLast())) {
        corrected.replace(correction.key(), correction.expected(), correction.replacement());
      }
    }
    return Map.copyOf(corrected);
  }

  private static List<Correction> loadCorrections() {
    String path = "/assets/aeroscapes_reimagine/language_corrections.json";
    try (var stream = LanguageCorrections.class.getResourceAsStream(path)) {
      if (stream == null) throw new IllegalStateException("Missing language corrections: " + path);
      JsonElement document =
          JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      if (!(document instanceof JsonArray rows)) {
        throw new IllegalStateException("Language corrections must be an array");
      }
      List<Correction> corrections = new ArrayList<>();
      Set<String> keys = new HashSet<>();
      for (JsonElement row : rows) {
        if (!(row instanceof JsonObject object)
            || !(object.keySet().equals(Set.of("key", "expected", "replacement"))
                || object.keySet().equals(Set.of("language", "key", "expected", "replacement")))) {
          throw new IllegalStateException("Invalid language correction: " + row);
        }
        String key = string(object, "key");
        String language = object.has("language") ? string(object, "language") : "ru_ru";
        String expected = string(object, "expected");
        String replacement = string(object, "replacement");
        if (!Set.of("en_us", "ru_ru").contains(language)
            || key.isBlank()
            || !keys.add(language + ":" + key)
            || expected.equals(replacement)) {
          throw new IllegalStateException("Invalid or duplicate language correction: " + key);
        }
        corrections.add(new Correction(language, key, expected, replacement));
      }
      return List.copyOf(corrections);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read language corrections: " + path, exception);
    }
  }

  private static String string(JsonObject object, String field) {
    JsonElement value = object.get(field);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalStateException("Language correction field must be a string: " + field);
    }
    return value.getAsString();
  }

  private record Correction(String language, String key, String expected, String replacement) {}
}
