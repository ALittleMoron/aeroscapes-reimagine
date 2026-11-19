package dev.aeroscapes.magic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MagicTalentsTest {
  private static final String ROOT = "magic_mastery_1";
  private static final List<String> DIRECTIONS =
      List.of("destruction", "restoration", "conjuration", "transformation");
  private static final String SPELL_IDS =
      "blood_needles magic_missile firebolt icicle ball_lightning shield heal oakskin "
          + "blood_slash wither_skull dragon_breath fang_strike fang_ward firecracker throw "
          + "burning_dash fire_breath flaming_strike fire_arrow wisp divine_smite cone_of_cold "
          + "ray_of_frost ice_spikes electrocute lightning_lance acid_orb poison_breath stomp "
          + "counterspell blessing_of_life cloud_of_regeneration fortify healing_circle "
          + "gust slow blight root teleport summon_horse wololo acupuncture devour ray_of_siphoning "
          + "magic_arrow starfall echoing_strikes black_hole shadow_slash chain_creeper lob_creeper "
          + "arrow_volley fang_swirl blaze_storm fireball magma_bomb wall_of_fire heat_surge scorch "
          + "flaming_barrage raise_hell guiding_bolt sunbeam ice_block frostwave snowball blizzard "
          + "chain_lightning lightning_bolt shockwave thunderstorm volt_strike poison_arrow "
          + "poison_splash earthquake sculk_tentacles sonic_boom eldritch_blast heartstop evasion "
          + "greater_heal haste cleanse ice_tomb charge gluttony abyssal_shroud raise_dead sacrifice "
          + "summon_swords arcane_shackle gravity_fissure summon_vex scapegoat summon_polar_bear "
          + "frostbite spider_aspect firefly_swarm telekinesis blood_step summon_ender_chest recall "
          + "portal invisibility spectral_hammer angel_wing frost_step ascension touch_dig "
          + "planar_sight pocket_dimension";

  private MagicTalentsTest() {}

  public static void main(String[] args) throws IOException {
    verify();
    dev.aeroscapes.progression.TalentBudgetTest.verify();
    System.out.println(
        "Magic talents: complete spell routes, ceilings, topology and effects passed");
  }

  public static void verify() throws IOException {
    catalogue();
    rankCeilings();
    bonuses();
    datapack();
  }

  private static void catalogue() throws IOException {
    JsonObject catalogue = resource("/aeroscapes_reimagine/magic-talents.json");
    Set<String> expected = new HashSet<>();
    for (String id : SPELL_IDS.split(" ")) {
      require(expected.add("irons_spellbooks:" + id), "Unique expected spell: " + id);
    }
    require(expected.size() == 111, "Complete pinned spell inventory");
    Set<String> actual = new HashSet<>();
    Map<String, Integer> roleCounts = new HashMap<>();
    int starters = 0;
    int bundles = 0;
    int individual = 0;
    for (var element : catalogue.getAsJsonArray("spells")) {
      var entry = element.getAsJsonObject();
      String id = entry.get("id").getAsString();
      require(actual.add(id), "Unique registered spell route: " + id);
      MagicTalents.Spell spell = MagicTalents.spell(id);
      require(spell != null, "Runtime spell route: " + id);
      require(MagicTalents.nodes().contains(spell.node()), "Known spell node: " + id);
      roleCounts.merge(spell.direction(), 1, Integer::sum);
      if (spell.node().equals(ROOT)) {
        starters++;
        require(spell.tier() == 1, "Introductory spells begin at first mastery");
      } else if (spell.node().startsWith("magic_spell_")) {
        individual++;
        require(
            spell.node().equals("magic_spell_" + id.substring(id.indexOf(':') + 1)),
            "Stable individual capability ID");
      } else {
        bundles++;
        require(
            spell.node().equals("magic_" + spell.direction() + "_mastery_" + spell.tier()),
            "Ordinary bundles belong to their functional mastery");
      }
    }
    require(actual.equals(expected), "Every pinned spell has exactly one route");
    require(starters == 8 && bundles == 33 && individual == 70, "Selected acquisition groups");
    require(
        roleCounts.equals(
            Map.of("destruction", 63, "restoration", 17, "conjuration", 16, "transformation", 15)),
        "Functional role classification");
    require(MagicTalents.totalCost() == MagicTalents.nodes().size(), "One point per magic node");
    require(MagicTalents.spell("irons_spellbooks:unknown") == null, "Unknown spell is not learned");
    require(
        !MagicTalents.learned("irons_spellbooks:unknown", MagicTalents.nodes()), "Unknown route");
  }

  private static void rankCeilings() {
    for (String direction : DIRECTIONS) {
      require(MagicTalents.mastery(Set.of(), direction) == 0, "No root means no mastery");
      Set<String> skills = new HashSet<>();
      skills.add("magic_" + direction + "_mastery_5");
      require(MagicTalents.mastery(skills, direction) == 0, "Saved later node cannot replace root");
      skills.add(ROOT);
      require(MagicTalents.mastery(skills, direction) == 1, "Missing intermediate mastery");
      skills.remove("magic_" + direction + "_mastery_5");
      for (int tier = 1; tier <= 5; tier++) {
        if (tier > 1) skills.add("magic_" + direction + "_mastery_" + tier);
        require(MagicTalents.mastery(skills, direction) == tier, "Contiguous direction mastery");
        String id = representative(direction);
        for (int maximum = 1; maximum <= 30; maximum++) {
          int expected = tier == 1 ? 1 : (int) Math.ceil(maximum * (tier - 1) / 4.0);
          require(MagicTalents.cap(id, maximum, skills) == expected, "Whole native rank ceiling");
        }
      }
      String middle = "magic_" + direction + "_mastery_3";
      skills.remove(middle);
      require(MagicTalents.mastery(skills, direction) == 2, "Mastery gaps stop later tiers");
    }
    require(MagicTalents.cap("irons_spellbooks:fireball", 10, Set.of()) == 0, "No novice rank");
    require(
        MagicTalents.cap("irons_spellbooks:unknown", 10, Set.of(ROOT)) == 0,
        "Unknown spell has no ceiling");
    require(
        MagicTalents.cap("irons_spellbooks:firebolt", 0, Set.of(ROOT)) == 0,
        "Disabled or invalid native rank range");
    require(MagicTalents.learned("irons_spellbooks:heal", Set.of(ROOT)), "Starter repertoire");
    require(
        !MagicTalents.learned("irons_spellbooks:greater_heal", Set.of(ROOT)),
        "Full healing remains a separate advanced route");
    require(
        !MagicTalents.learned(
            "irons_spellbooks:greater_heal", Set.of(ROOT, "magic_spell_greater_heal")),
        "Saved advanced unlock still requires its mastery");
    require(
        MagicTalents.learned("irons_spellbooks:greater_heal", MagicTalents.nodes()),
        "Full magic is attainable without exclusions");
    require(
        MagicTalents.cap("irons_spellbooks:fireball", Integer.MAX_VALUE, MagicTalents.nodes())
            == Integer.MAX_VALUE,
        "Full native rank range does not overflow");
  }

  private static String representative(String direction) {
    return "irons_spellbooks:"
        + switch (direction) {
          case "destruction" -> "firebolt";
          case "restoration" -> "heal";
          case "conjuration" -> "gust";
          default -> "teleport";
        };
  }

  private static void bonuses() {
    Set<String> all = MagicTalents.nodes();
    Map<String, List<Double>> expected =
        Map.of(
            "destruction", List.of(.05, .04, .04, .04),
            "restoration", List.of(.13, .10, .10, .10),
            "conjuration", List.of(.15, .12, .14, .04),
            "transformation", List.of(.05, .12, .14, .14));
    List<String> effects = List.of("power", "mana", "cooldown", "cast_speed");
    for (String direction : DIRECTIONS) {
      for (int index = 0; index < effects.size(); index++) {
        close(
            MagicTalents.bonus(all, direction, effects.get(index)),
            expected.get(direction).get(index),
            direction + " " + effects.get(index));
      }
      close(MagicTalents.bonus(all, direction, "native_max_mana"), 90, "Shared mana capacity");
      close(MagicTalents.bonus(all, direction, "native_mana_regen"), .24, "Shared regeneration");
      close(MagicTalents.bonus(all, direction, "native_cooldown"), .09, "Shared cooldown reward");
      close(MagicTalents.bonus(all, direction, "native_cast_speed"), .09, "Shared cast reward");
    }
    close(MagicTalents.bonus(all, "destruction", "fireball_area"), 1, "Fixed fireball radius");
    close(MagicTalents.bonus(all, "destruction", "blizzard_area"), .15, "Blizzard radius factor");
    close(MagicTalents.bonus(all, "restoration", "fireball_area"), 0, "No cross-role area bonus");
    close(MagicTalents.bonus(all, "destruction", "frozen_bonus"), .10, "Chilled direct-hit bonus");
    close(MagicTalents.bonus(all, "destruction", "poison_bonus"), .10, "Poison direct-hit bonus");
    close(MagicTalents.bonus(Set.of(), "destruction", "power"), 0, "No unpurchased reward");
    close(
        MagicTalents.bonus(Set.of("magic_restoration_gentle_power"), "destruction", "power"),
        0,
        "A functional direction is not a global native school bonus");
  }

  private static void datapack() throws IOException {
    Path category =
        Path.of(
            "../../pack/global_packs/required_data/aeroscapes_reimagine_rules/data/"
                + "aeroscapes_reimagine/puffish_skills/categories/combat");
    JsonObject definitions = read(category.resolve("definitions.json"));
    JsonObject skills = read(category.resolve("skills.json"));
    JsonObject settings = read(category.resolve("category.json"));
    require(
        settings.get("spent_points_limit").getAsInt() == MagicTalents.pointCap(),
        "All combat practices share the complete magic allocation ceiling");
    require(!settings.get("exclusive_root").getAsBoolean(), "Compatible physical and magic roots");
    Set<String> dataNodes = new HashSet<>();
    Set<String> roots = new HashSet<>();
    for (var entry : skills.entrySet()) {
      if (!entry.getKey().startsWith("magic_")) continue;
      dataNodes.add(entry.getKey());
      var node = entry.getValue().getAsJsonObject();
      if (node.has("root") && node.get("root").getAsBoolean()) roots.add(entry.getKey());
      require(node.get("definition").getAsString().equals(entry.getKey()), "Exact definition ID");
      var definition = definitions.getAsJsonObject(entry.getKey());
      require(definition != null, "Every displayed node has a definition");
      require(definition.get("cost").getAsInt() == 1, "Native purchase price matches policy");
      nativeRewards(entry.getKey(), definition);
    }
    require(dataNodes.equals(MagicTalents.nodes()), "Runtime and native magic node closure");
    require(roots.equals(Set.of(ROOT)), "One shared first mastery");
    Map<String, List<String>> children = new HashMap<>();
    Map<String, List<String>> parents = new HashMap<>();
    try (var reader = Files.newBufferedReader(category.resolve("connections.json"))) {
      var connections =
          JsonParser.parseReader(reader)
              .getAsJsonObject()
              .getAsJsonObject("normal")
              .getAsJsonArray("unidirectional");
      for (var element : connections) {
        var connection = element.getAsJsonArray();
        String parent = connection.get(0).getAsString();
        String child = connection.get(1).getAsString();
        if (!parent.startsWith("magic_") && !child.startsWith("magic_")) continue;
        require(dataNodes.contains(parent) && dataNodes.contains(child), "No dangling magic edges");
        children.computeIfAbsent(parent, unused -> new ArrayList<>()).add(child);
        parents.computeIfAbsent(child, unused -> new ArrayList<>()).add(parent);
      }
    }
    Set<String> reachable = new HashSet<>(reachable(ROOT, children));
    reachable.add(ROOT);
    require(reachable.equals(dataNodes), "Every magic talent is reachable");
    require(
        children.get(ROOT).size() == DIRECTIONS.size(),
        "The first mastery opens one path per functional direction");
    for (String id : dataNodes) {
      require(!reachable(id, children).stream().anyMatch(id::equals), "Acyclic talent progression");
    }
    JsonObject connections = read(category.resolve("connections.json"));
    for (var group : connections.getAsJsonObject("exclusive").entrySet()) {
      for (var edge : group.getValue().getAsJsonArray()) {
        for (var endpoint : edge.getAsJsonArray()) {
          require(!dataNodes.contains(endpoint.getAsString()), "Full magic has no exclusions");
        }
      }
    }
    for (String id : SPELL_IDS.split(" ")) {
      MagicTalents.Spell spell = MagicTalents.spell("irons_spellbooks:" + id);
      String mastery =
          spell.tier() == 1 ? ROOT : "magic_" + spell.direction() + "_mastery_" + spell.tier();
      require(
          spell.node().equals(mastery) || reachable(mastery, children).contains(spell.node()),
          "A spell route follows its own functional mastery");
    }
    Set<Integer> depths = new HashSet<>();
    for (String direction : DIRECTIONS) {
      String previous = ROOT;
      List<Integer> requirements = List.of(5, 15, 30, 45);
      for (int tier = 2; tier <= 5; tier++) {
        String mastery = "magic_" + direction + "_mastery_" + tier;
        for (String parent : parents.get(mastery))
          checkMasteryPath(previous, parent, parents, depths, 0);
        require(
            MagicTalents.requiredLevel(mastery) == requirements.get(tier - 2),
            "Practice readiness gates");
        previous = mastery;
      }
    }
    require(depths.equals(Set.of(1, 2, 3)), "Mastery paths vary between one and three upgrades");
    for (String id : dataNodes) {
      if (id.startsWith("magic_spell_")) {
        require(parents.get(id).size() == 1, "Single unlock route for " + id);
        require(!parents.get(id).getFirst().startsWith("magic_spell_"), "Independent spell choice");
      }
    }
    require(
        parents.get("magic_destruction_blast_geometry").equals(List.of("magic_spell_fireball"))
            && parents
                .get("magic_destruction_winter_front")
                .equals(List.of("magic_spell_blizzard")),
        "A spell-specific radius upgrade requires its spell");
    spacing(skills, dataNodes);
    text(definitions, dataNodes);
  }

  private static void checkMasteryPath(
      String previous,
      String node,
      Map<String, List<String>> parents,
      Set<Integer> depths,
      int length) {
    if (node.equals(previous)) {
      require(length >= 1 && length <= 3, "Every route has one to three intermediate purchases");
      depths.add(length);
      return;
    }
    require(
        length < 3 && !MagicTalents.definitions().get(node).effects().isEmpty(),
        "Every route must lead back to its preceding mastery through useful improvements: " + node);
    for (String parent : parents.get(node))
      checkMasteryPath(previous, parent, parents, depths, length + 1);
  }

  private static void nativeRewards(String id, JsonObject definition) {
    var effects = MagicTalents.definitions().get(id).effects();
    Map<String, String> attributes =
        Map.of(
            "native_max_mana", "max_mana",
            "native_mana_regen", "mana_regen",
            "native_cooldown", "cooldown_reduction",
            "native_cast_speed", "cast_time_reduction");
    int expected = 0;
    for (var effect : effects.entrySet()) {
      String attribute = attributes.get(effect.getKey());
      if (attribute == null) continue;
      expected++;
      require(
          MagicTalents.definitions().get(id).direction().isEmpty(),
          "General native attributes cannot impersonate functional role effects");
      var reward = definition.getAsJsonArray("rewards").get(0).getAsJsonObject();
      require(reward.get("type").getAsString().equals("puffish_skills:attribute"), "Native reward");
      var data = reward.getAsJsonObject("data");
      require(
          data.get("attribute").getAsString().equals("irons_spellbooks:" + attribute),
          "Pinned native attribute identity");
      close(data.get("value").getAsDouble(), effect.getValue(), "Native reward amount");
      String operation = effect.getKey().equals("native_mana_regen") ? "multiply_base" : "addition";
      require(data.get("operation").getAsString().equals(operation), "Exact attribute operation");
    }
    require(definition.getAsJsonArray("rewards").size() == expected, "No duplicate role rewards");
  }

  private static Set<String> reachable(String start, Map<String, List<String>> children) {
    Set<String> visited = new HashSet<>();
    ArrayDeque<String> pending = new ArrayDeque<>(children.getOrDefault(start, List.of()));
    while (!pending.isEmpty()) {
      String node = pending.remove();
      if (visited.add(node)) pending.addAll(children.getOrDefault(node, List.of()));
    }
    return visited;
  }

  private static void spacing(JsonObject skills, Set<String> dataNodes) {
    for (String left : dataNodes) {
      JsonObject first = skills.getAsJsonObject(left);
      for (String right : dataNodes) {
        if (left.compareTo(right) >= 0) continue;
        JsonObject second = skills.getAsJsonObject(right);
        double distance =
            Math.hypot(
                first.get("x").getAsDouble() - second.get("x").getAsDouble(),
                first.get("y").getAsDouble() - second.get("y").getAsDouble());
        require(distance >= 44, "Frames and halos stay apart: " + left + ", " + right);
      }
    }
  }

  private static void text(JsonObject definitions, Set<String> nodes) throws IOException {
    for (String locale : List.of("en_us", "ru_ru")) {
      JsonObject language =
          resource("/assets/aeroscapes_reimagine_magic_tree_translations/lang/" + locale + ".json");
      for (String id : nodes) {
        String key =
            definitions
                .getAsJsonObject(id)
                .getAsJsonObject("description")
                .get("translate")
                .getAsString();
        require(
            language.has(key) && !language.get(key).getAsString().isBlank(), "Localized talent");
        String title =
            definitions.getAsJsonObject(id).getAsJsonObject("title").get("translate").getAsString();
        if (!title.startsWith("spell.irons_spellbooks.")) {
          require(
              language.has(title) && !language.get(title).getAsString().isBlank(),
              "Localized mastery and passive names");
        }
      }
    }
  }

  private static JsonObject resource(String path) throws IOException {
    try (var stream = MagicTalentsTest.class.getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing talent resource: " + path);
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private static JsonObject read(Path path) throws IOException {
    try (var reader = Files.newBufferedReader(path)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  private static void close(double actual, double expected, String message) {
    require(Math.abs(actual - expected) < 0.0000001, message + ": " + actual);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
