package dev.aeroscapes.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes maximum spend from the registered graphs, independently of the earned point ceilings.
 */
public final class TalentBudgetTest {
  private static final Path DATA =
      Path.of(
          "../../pack/global_packs/required_data/"
              + "aeroscapes_reimagine_rules/data/aeroscapes_reimagine/puffish_skills");

  private TalentBudgetTest() {}

  public static void verify() throws IOException {
    checkCompetingRoutes();
    JsonObject config = read(DATA.resolve("config.json"));
    supported(config, Set.of("version", "categories"), "registration");
    require(config.get("version").getAsInt() == 3, "Unsupported provider config version");
    var trainedProfessions = new HashMap<String, Long>();
    for (String skill : ProgressionBridge.PROFESSION_SKILLS)
      trainedProfessions.put(skill, Long.MAX_VALUE);
    require(
        ProgressionBridge.points(trainedProfessions, ProgressionBridge.PROFESSION_SKILLS)
            >= ProgressionBridge.PROFESSION_POINT_CAP,
        "The profession level rewards must be able to fund the whole pool");
    var caps =
        Map.of(
            "combat",
            MagicTalents.pointCap(),
            "profession",
            ProgressionBridge.PROFESSION_POINT_CAP);
    Set<String> registered = new HashSet<>();
    for (JsonElement value : config.getAsJsonArray("categories")) {
      String id = value.getAsString();
      require(
          registered.add(id) && caps.containsKey(id),
          "Missing or ambiguous point source for " + id);
      verifyCategory(id, caps.get(id));
    }
    require(
        registered.equals(caps.keySet()), "Earned point sources must have registered categories");
  }

  private static void verifyCategory(String id, int earnedCap) throws IOException {
    Path category = DATA.resolve("categories").resolve(id);
    require(
        !Files.exists(category.resolve("experience.json"))
            && !Files.exists(category.resolve("exchange.json")),
        "Account for alternate point sources in " + id);
    JsonObject settings = read(category.resolve("category.json"));
    supported(
        settings,
        Set.of(
            "title",
            "description",
            "extra_description",
            "icon",
            "background",
            "unlocked_by_default",
            "starting_points",
            "exclusive_root",
            "colors",
            "spent_points_limit"),
        id);
    require(
        settings.get("unlocked_by_default").getAsBoolean()
            && settings.get("starting_points").getAsInt() == 0
            && !settings.get("exclusive_root").getAsBoolean(),
        "Unsupported category access or initial budget in " + id);

    JsonObject definitions = read(category.resolve("definitions.json"));
    JsonObject skills = read(category.resolve("skills.json"));
    Map<String, Node> nodes = new HashMap<>();
    for (var skill : skills.entrySet()) {
      String name = skill.getKey();
      var placed = skill.getValue().getAsJsonObject();
      supported(placed, Set.of("definition", "x", "y", "root"), name);
      var definition = definitions.getAsJsonObject(placed.get("definition").getAsString());
      supported(
          definition,
          Set.of(
              "title",
              "description",
              "extra_description",
              "size",
              "icon",
              "frame",
              "rewards",
              "cost"),
          name);
      for (var reward : definition.getAsJsonArray("rewards")) {
        require(
            reward.getAsJsonObject().get("type").getAsString().equals("puffish_skills:attribute"),
            "Check purchase/point side effects of new reward on " + name);
      }
      int cost = definition.get("cost").getAsBigDecimal().intValueExact();
      require(cost >= 0, "Unsupported negative cost on " + name);
      require(
          !id.equals("combat")
              || MagicTalents.requiredLevel(name) <= MagicPracticeProgress.MAX_LEVEL,
          "Practice requirement is unattainable: " + name);
      var required = id.equals("combat") ? MagicTalents.priorMasteries(name) : List.<String>of();
      nodes.put(
          name,
          new Node(
              cost,
              placed.has("root") && placed.get("root").getAsBoolean(),
              new HashSet<>(),
              required));
    }
    JsonObject connections = read(category.resolve("connections.json"));
    supported(connections, Set.of("normal", "exclusive"), id);
    List<Edge> conflicts = new ArrayList<>();
    for (var group : connections.entrySet()) {
      boolean exclusion = group.getKey().equals("exclusive");
      var directions = group.getValue().getAsJsonObject();
      supported(
          directions,
          exclusion ? Set.of("bidirectional") : Set.of("bidirectional", "unidirectional"),
          id);
      for (var direction : directions.entrySet()) {
        for (var entry : direction.getValue().getAsJsonArray()) {
          var endpoints = entry.getAsJsonArray();
          require(endpoints.size() == 2, "Malformed " + group.getKey() + " edge: " + entry);
          String from = endpoints.get(0).getAsString(), to = endpoints.get(1).getAsString();
          require(
              nodes.containsKey(from) && nodes.containsKey(to) && !from.equals(to),
              "Invalid talent edge: " + entry);
          if (exclusion) conflicts.add(new Edge(from, to));
          else {
            nodes.get(to).parents().add(from);
            if (direction.getKey().equals("bidirectional")) nodes.get(from).parents().add(to);
          }
        }
      }
    }
    var everReachable = new HashSet<String>();
    long maximum = maximum(nodes, conflicts, Set.of(), everReachable);
    require(
        everReachable.equals(nodes.keySet()),
        "Some talents never occur in a legal allocation in "
            + id
            + ": "
            + nodes.keySet().stream()
                .filter(node -> !everReachable.contains(node))
                .sorted()
                .toList());
    require(
        maximum == earnedCap
            && maximum == settings.get("spent_points_limit").getAsBigDecimal().longValueExact(),
        id
            + " allows exactly "
            + maximum
            + " points of simultaneous purchases; earned cap = "
            + earnedCap
            + ", spent limit = "
            + settings.get("spent_points_limit"));
  }

  private record Node(int cost, boolean root, Set<String> parents, List<String> required) {}

  private record Edge(String a, String b) {}

  private static long maximum(
      Map<String, Node> nodes,
      List<Edge> conflicts,
      Set<String> excluded,
      Set<String> everReachable) {
    Set<String> reachable = new HashSet<>();
    int priorSize;
    do {
      priorSize = reachable.size();
      for (var entry : nodes.entrySet()) {
        Node node = entry.getValue();
        if (!excluded.contains(entry.getKey())
            && reachable.containsAll(node.required())
            && (node.root() || !Collections.disjoint(node.parents(), reachable))) {
          reachable.add(entry.getKey());
        }
      }
    } while (reachable.size() > priorSize);
    for (Edge edge : conflicts) {
      if (reachable.contains(edge.a()) && reachable.contains(edge.b())) {
        // Every legal allocation omits at least one endpoint. Recalculate reachability for both
        // choices because omitting a cheap gateway can also prevent expensive purchases behind it.
        var withoutA = new HashSet<>(excluded);
        withoutA.add(edge.a());
        var withoutB = new HashSet<>(excluded);
        withoutB.add(edge.b());
        return Math.max(
            maximum(nodes, conflicts, withoutA, everReachable),
            maximum(nodes, conflicts, withoutB, everReachable));
      }
    }
    everReachable.addAll(reachable);
    return reachable.stream().mapToLong(skill -> nodes.get(skill).cost()).sum();
  }

  private static void checkCompetingRoutes() {
    var nodes =
        Map.of(
            "root",
            new Node(2, true, Set.of(), List.of()),
            "left",
            new Node(2, false, Set.of("root"), List.of()),
            "middle",
            new Node(6, false, Set.of("root"), List.of()),
            "right",
            new Node(3, false, Set.of("root"), List.of()),
            "deep",
            new Node(8, false, Set.of("left"), List.of("right")),
            "orphan",
            new Node(40, false, Set.of(), List.of()));
    var reached = new HashSet<String>();
    require(
        maximum(
                nodes,
                List.of(new Edge("left", "middle"), new Edge("middle", "right")),
                Set.of(),
                reached)
            == 15,
        "Overlapping exclusions and weighted descendants must be optimized together");
    require(
        reached.equals(Set.of("root", "left", "middle", "right", "deep")),
        "A disconnected node cannot contribute points");
    require(
        maximum(nodes, List.of(new Edge("left", "right")), Set.of(), new HashSet<>()) == 11,
        "An unreachable prerequisite blocks its descendant even if its normal parent can be"
            + " reached");
  }

  private static void supported(JsonObject value, Set<String> fields, String context) {
    for (String field : value.keySet())
      require(fields.contains(field), "Unsupported talent rule in " + context + ": " + field);
  }

  private static JsonObject read(Path path) throws IOException {
    try (var reader = Files.newBufferedReader(path)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
