package dev.aeroscapes.magic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** The shared spell routes, permanent rank ceilings and functional talent effects. */
public final class MagicTalents {
  private static final String ROOT = "magic_mastery_1";
  private static final Set<String> DIRECTIONS =
      Set.of("destruction", "restoration", "conjuration", "transformation");
  private static final Map<String, List<String>> PRIOR_MASTERIES = priorMasteries();
  private static final Set<String> EFFECTS =
      Set.of(
          "power",
          "mana",
          "cooldown",
          "cast_speed",
          "fireball_area",
          "blizzard_area",
          "frozen_bonus",
          "poison_bonus",
          "guardian_refund",
          "binding_refund",
          "native_max_mana",
          "native_mana_regen",
          "native_cooldown",
          "native_cast_speed");
  private static final Catalogue CATALOGUE = load();

  public record Spell(String id, String direction, String node, int tier) {}

  public record Node(
      String id, int requiredLevel, int cost, String direction, Map<String, Double> effects) {
    public Node {
      effects = Map.copyOf(effects);
    }
  }

  private record Catalogue(Map<String, Node> nodes, Map<String, Spell> spells, int pointCap) {}

  private MagicTalents() {}

  public static Spell spell(String id) {
    return CATALOGUE.spells().get(id);
  }

  public static Map<String, Node> definitions() {
    return CATALOGUE.nodes();
  }

  public static Set<String> nodes() {
    return CATALOGUE.nodes().keySet();
  }

  public static int totalCost() {
    return CATALOGUE.nodes().values().stream().mapToInt(Node::cost).sum();
  }

  public static int pointCap() {
    return CATALOGUE.pointCap();
  }

  public static int requiredLevel(String node) {
    Node definition = CATALOGUE.nodes().get(node);
    return definition == null ? 0 : definition.requiredLevel();
  }

  private static Map<String, List<String>> priorMasteries() {
    Map<String, List<String>> result = new HashMap<>();
    for (String direction : DIRECTIONS) {
      List<String> path = new ArrayList<>(List.of(ROOT));
      for (int tier = 2; tier <= 5; tier++) {
        String node = "magic_" + direction + "_mastery_" + tier;
        result.put(node, List.copyOf(path));
        path.add(node);
      }
    }
    return Map.copyOf(result);
  }

  public static String missingPriorMastery(String node, Predicate<String> unlocked) {
    for (String required : priorMasteries(node)) {
      if (!unlocked.test(required)) return required;
    }
    return null;
  }

  public static List<String> priorMasteries(String node) {
    return PRIOR_MASTERIES.getOrDefault(node, List.of());
  }

  public static int mastery(Set<String> skills, String direction) {
    if (!DIRECTIONS.contains(direction) || !skills.contains(ROOT)) return 0;
    int depth = 1;
    for (int tier = 2; tier <= 5; tier++) {
      if (!skills.contains("magic_" + direction + "_mastery_" + tier)) break;
      depth = tier;
    }
    return depth;
  }

  public static int cap(String id, int maximum, Set<String> skills) {
    Spell definition = spell(id);
    if (definition == null || maximum < 1) return 0;
    int depth = mastery(skills, definition.direction());
    return switch (depth) {
      case 0 -> 0;
      case 1 -> 1;
      case 2 -> (int) Math.max(1, (maximum + 3L) / 4);
      case 3 -> (int) Math.max(1, (maximum + 1L) / 2);
      case 4 -> (int) Math.max(1, (3L * maximum + 3) / 4);
      default -> maximum;
    };
  }

  public static boolean learned(String id, Set<String> skills) {
    Spell definition = spell(id);
    return definition != null
        && skills.contains(definition.node())
        && mastery(skills, definition.direction()) >= definition.tier();
  }

  public static double bonus(Set<String> skills, String direction, String effect) {
    double bonus = 0;
    for (String skill : skills) {
      Node definition = CATALOGUE.nodes().get(skill);
      if (definition != null
          && (definition.direction().isEmpty() || definition.direction().equals(direction))) {
        bonus += definition.effects().getOrDefault(effect, 0.0);
      }
    }
    return bonus;
  }

  private static Catalogue load() {
    try (var stream =
        MagicTalents.class.getResourceAsStream("/aeroscapes_reimagine/magic-talents.json")) {
      if (stream == null) throw new IOException("Missing magic talent catalogue");
      JsonObject document =
          object(
              JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)),
              "catalogue");
      Map<String, Node> nodes = new HashMap<>();
      for (JsonElement element : array(document.get("nodes"), "nodes")) {
        JsonObject entry = object(element, "node");
        String id = string(entry, "id");
        String direction = string(entry, "direction");
        if (!id.matches("magic_[a-z0-9_]+")
            || (!direction.isEmpty() && !DIRECTIONS.contains(direction))) {
          throw new IOException("Invalid magic node identity: " + id);
        }
        Map<String, Double> effects = new HashMap<>();
        for (var effect : object(entry.get("effects"), "effects").entrySet()) {
          JsonElement value = effect.getValue();
          if (!EFFECTS.contains(effect.getKey())
              || !value.isJsonPrimitive()
              || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Invalid magic effect: " + id + ":" + effect.getKey());
          }
          double amount = value.getAsDouble();
          double maximum = effect.getKey().equals("native_max_mana") ? 100 : 1;
          if (!Double.isFinite(amount) || amount <= 0 || amount > maximum) {
            throw new IOException("Invalid magic effect amount: " + id);
          }
          effects.put(effect.getKey(), amount);
        }
        Node node =
            new Node(
                id,
                integer(entry, "requiredLevel", 0, 50),
                integer(entry, "cost", 1, 1),
                direction,
                effects);
        if (nodes.putIfAbsent(id, node) != null) {
          throw new IOException("Duplicate magic node: " + id);
        }
      }
      if (!nodes.containsKey(ROOT)) throw new IOException("Missing first magic mastery");
      Map<String, Spell> spells = new HashMap<>();
      for (JsonElement element : array(document.get("spells"), "spells")) {
        JsonObject entry = object(element, "spell");
        Spell spell =
            new Spell(
                string(entry, "id"),
                string(entry, "direction"),
                string(entry, "node"),
                integer(entry, "tier", 1, 5));
        Node node = nodes.get(spell.node());
        if (!spell.id().matches("irons_spellbooks:[a-z0-9_]+")
            || !DIRECTIONS.contains(spell.direction())
            || node == null
            || (!node.direction().isEmpty() && !node.direction().equals(spell.direction()))) {
          throw new IOException("Invalid magic spell route: " + spell.id());
        }
        if (spells.putIfAbsent(spell.id(), spell) != null) {
          throw new IOException("Duplicate magic spell route: " + spell.id());
        }
      }
      int pointCap = integer(document, "pointCap", nodes.size(), 1000);
      return new Catalogue(Map.copyOf(nodes), Map.copyOf(spells), pointCap);
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot load magic talent catalogue", failure);
    }
  }

  private static JsonObject object(JsonElement value, String name) throws IOException {
    if (!(value instanceof JsonObject object)) throw new IOException("Expected object: " + name);
    return object;
  }

  private static JsonArray array(JsonElement value, String name) throws IOException {
    if (!(value instanceof JsonArray array)) throw new IOException("Expected array: " + name);
    return array;
  }

  private static String string(JsonObject object, String key) throws IOException {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IOException("Expected string: " + key);
    }
    return value.getAsString();
  }

  private static int integer(JsonObject object, String key, int minimum, int maximum)
      throws IOException {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
      throw new IOException("Expected integer: " + key);
    }
    double number = value.getAsDouble();
    if (!Double.isFinite(number)
        || number != Math.rint(number)
        || number < minimum
        || number > maximum) {
      throw new IOException("Invalid integer: " + key);
    }
    return (int) number;
  }
}
