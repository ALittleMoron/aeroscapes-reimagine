package dev.aeroscapes.atmosphere;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Resolves criterion names through the player's effective item, entity and biome text. */
public final class AdvancementCriterionTranslations {
  private static final Map<String, String> ALIASES = loadAliases();
  private static final Map<String, String> ZETA_DOMAINS =
      Map.of(
          "minecraft:adventure/adventuring_time", "biome",
          "minecraft:husbandry/balanced_diet", "item",
          "minecraft:husbandry/bred_all_animals", "entity",
          "minecraft:adventure/kill_all_mobs", "entity");

  private AdvancementCriterionTranslations() {}

  public static String resolve(String key, String criterion, Predicate<String> exists) {
    if (exists.test(key)) return key;
    String alias = ALIASES.get(key);
    if (alias != null && exists.test(alias)) return alias;
    for (var entry : ZETA_DOMAINS.entrySet()) {
      if (!key.equals("betteradvancements.criterion." + entry.getKey() + "." + criterion)) continue;
      ResourceLocation id = ResourceLocation.tryParse(criterion);
      if (id == null) return key;
      String suffix = "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
      String candidate = entry.getValue() + suffix;
      if (exists.test(candidate)) return candidate;
      if (entry.getValue().equals("item") && exists.test("block" + suffix)) return "block" + suffix;
    }
    return key;
  }

  private static Map<String, String> loadAliases() {
    String path = "/assets/aeroscapes_reimagine/advancement_criterion_aliases.json";
    try (var stream = AdvancementCriterionTranslations.class.getResourceAsStream(path)) {
      if (stream == null) throw new IllegalStateException("Missing criterion aliases");
      JsonElement document =
          JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      if (!(document instanceof JsonObject object)) {
        throw new IllegalStateException("Criterion aliases must be an object");
      }
      Map<String, String> aliases = new HashMap<>();
      for (var entry : object.entrySet()) {
        JsonElement value = entry.getValue();
        if (!entry.getKey().startsWith("betteradvancements.criterion.")
            || !value.isJsonPrimitive()
            || !value.getAsJsonPrimitive().isString()
            || value.getAsString().isBlank()) {
          throw new IllegalStateException("Invalid criterion alias: " + entry.getKey());
        }
        aliases.put(entry.getKey(), value.getAsString());
      }
      return Map.copyOf(aliases);
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read criterion aliases", failure);
    }
  }
}
