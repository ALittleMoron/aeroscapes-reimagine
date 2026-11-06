package dev.aeroscapes.atmosphere;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Missing-key fallbacks run after all provider and player resource namespaces have merged. */
public final class MiscTranslations {
  private static final Map<String, String> ENGLISH = load("en_us");
  private static final Map<String, String> RUSSIAN = load("ru_ru");

  private MiscTranslations() {}

  public static Map<String, String> apply(
      Map<String, String> translations, List<String> languages) {
    if (languages.isEmpty()) return Map.copyOf(translations);
    Map<String, String> result = new HashMap<>(translations);
    Map<String, String> fallback = languages.getLast().equals("ru_ru") ? RUSSIAN : ENGLISH;
    fallback.forEach(result::putIfAbsent);
    return Map.copyOf(result);
  }

  private static Map<String, String> load(String language) {
    String path = "/assets/aeroscapes_reimagine/misc_translations/" + language + ".json";
    try (var stream = MiscTranslations.class.getResourceAsStream(path)) {
      if (stream == null) throw new IllegalStateException("Missing translation fallback: " + path);
      var document = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      if (!document.isJsonObject())
        throw new IllegalStateException("Translation fallback must be an object: " + path);
      Map<String, String> result = new HashMap<>();
      for (var entry : document.getAsJsonObject().entrySet()) {
        var value = entry.getValue();
        if (entry.getKey().isBlank()
            || !value.isJsonPrimitive()
            || !value.getAsJsonPrimitive().isString())
          throw new IllegalStateException("Invalid translation fallback: " + entry.getKey());
        result.put(entry.getKey(), value.getAsString());
      }
      return Map.copyOf(result);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load translation fallback: " + path, exception);
    }
  }
}
