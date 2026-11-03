package dev.aeroscapes.atmosphere;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Shared literal keys must not depend on the order of owned translation namespaces. */
public final class ConfigLiteralNamespaceTest {
  private static final String PREFIX = "aeroscapes_reimagine.config.literal.";
  private static final Pattern LANGUAGE_RESOURCE =
      Pattern.compile("assets/[^/]+/lang/([^/]+)\\.json");

  private ConfigLiteralNamespaceTest() {}

  private record TranslationKey(String locale, String key) {}

  private record Translation(String resource, String value) {}

  public static void verify() throws IOException {
    Set<String> resources = new TreeSet<>();
    collectResources(readObject("META-INF/localization-sources.json"), resources);
    if (resources.isEmpty())
      throw new AssertionError("Localization inventory lists no owned language resources");
    Map<TranslationKey, Translation> translations = new HashMap<>();
    for (String resource : resources) {
      var matcher = LANGUAGE_RESOURCE.matcher(resource);
      if (!matcher.matches()) throw new AssertionError("Invalid language resource: " + resource);
      String locale = matcher.group(1);
      for (var entry : readObject(resource).entrySet()) {
        if (!entry.getKey().startsWith(PREFIX)) continue;
        JsonElement value = entry.getValue();
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
          throw new AssertionError(
              "Non-string config literal in " + resource + ": " + entry.getKey());
        var key = new TranslationKey(locale, entry.getKey());
        var translation = new Translation(resource, value.getAsString());
        Translation previous = translations.putIfAbsent(key, translation);
        if (previous != null && !previous.value().equals(translation.value()))
          throw new AssertionError(
              "Conflicting config literal "
                  + key.locale()
                  + " "
                  + key.key()
                  + " in "
                  + previous.resource()
                  + " ("
                  + previous.value()
                  + ") and "
                  + resource
                  + " ("
                  + translation.value()
                  + ")");
      }
    }
    System.out.println(
        "Config literal namespace: "
            + resources.size()
            + " owned language resources, "
            + translations.size()
            + " locale/key entries checked");
  }

  private static void collectResources(JsonElement element, Set<String> resources) {
    if (element.isJsonObject()) {
      for (var entry : element.getAsJsonObject().entrySet()) {
        JsonElement value = entry.getValue();
        if (entry.getKey().equals("resource")
            && value.isJsonPrimitive()
            && value.getAsJsonPrimitive().isString()
            && LANGUAGE_RESOURCE.matcher(value.getAsString()).matches())
          resources.add(value.getAsString());
        collectResources(value, resources);
      }
    } else if (element.isJsonArray()) {
      for (JsonElement value : element.getAsJsonArray()) collectResources(value, resources);
    }
  }

  private static JsonObject readObject(String resource) throws IOException {
    try (var input = ConfigLiteralNamespaceTest.class.getResourceAsStream("/" + resource)) {
      if (input == null)
        throw new AssertionError("Missing owned localization resource: " + resource);
      JsonElement parsed =
          JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
      if (parsed instanceof JsonObject object) return object;
      throw new AssertionError("Expected localization object: " + resource);
    }
  }
}
