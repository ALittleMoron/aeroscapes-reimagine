package dev.aeroscapes.aquatic;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Retains provider item dispatch and selects physical models only for hand contexts. */
final class IronHeldModels {
  static final String MOD = "irons_spellbooks:";
  static final List<String> WEAPONS =
      List.of(
          "magehunter",
          "spellbreaker",
          "amethyst_rapier",
          "keeper_flamberge",
          "legionnaire_flamberge",
          "decrepit_scythe",
          "hellrazor",
          "boreal_blade",
          "twilight_gale",
          "autoloader_crossbow",
          "misery");
  static final List<String> PAPERS =
      List.of(
          "scroll",
          "furled_map",
          "furled_map_ancient",
          "furled_map_citadel",
          "furled_map_ice_spider_den",
          "chronicle",
          "ruined_book",
          "unchained_book",
          "archevoker_logbook_translated",
          "archevoker_logbook_untranslated",
          "eldritch_manuscript");
  static final List<String> RITUALS =
      List.of(
          "energized_core",
          "cinderous_soulcaller",
          "decrepit_key",
          "bone_key",
          "dead_king_phylactery",
          "tincture_of_forgetfulness",
          "netherward_tincture");
  static final List<String> SCHOOLS =
      List.of(
          "blood", "eldritch", "ender", "evocation", "fire", "holy", "ice", "lightning", "nature");
  static final String COMPASS = "wayward_compass";
  static final String WAND = "hither_thither_wand";
  static final String PUNCHY_PROFILE = "minecraft/punchy/compat/aeroscapes_iron_equipment.json";
  private static final String COMPASS_FRAME = "compass/wayward_compass_";

  private IronHeldModels() {}

  static Map<String, String> states() throws IOException {
    Map<String, String> states = new LinkedHashMap<>();
    for (var roots : List.of(WEAPONS, PAPERS, RITUALS, List.of(COMPASS, WAND)))
      for (String root : roots) addState(states, root, root);
    for (String school : SCHOOLS) addState(states, "scroll", "scroll_" + school);
    return states;
  }

  private static void addState(Map<String, String> states, String root, String state)
      throws IOException {
    if (states.putIfAbsent(state, root) != null) return;
    var model = HeldItemModels.nativeModel(MOD + state);
    if (model.has("overrides")) {
      for (var entry : model.getAsJsonArray("overrides")) {
        String target = entry.getAsJsonObject().get("model").getAsString();
        String prefix = MOD + "item/";
        if (!target.startsWith(prefix)) throw new IOException("Unexpected state: " + target);
        addState(states, root, target.substring(prefix.length()));
      }
    }
  }

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    var models = states();
    var replaced =
        models.keySet().stream().map(name -> MOD + name).collect(Collectors.toUnmodifiableSet());
    JsonObject compass = compass(assets, output);
    for (var entry : models.entrySet()) {
      String state = entry.getKey(), root = entry.getValue();
      String held = MOD + "item/equipment/held/" + state;
      var nativeModel = HeldItemModels.nativeModel(MOD + state);
      JsonObject model;
      if (root.equals(COMPASS)) {
        model = compass.deepCopy();
        String texture = texture(nativeModel);
        var textures = model.getAsJsonObject("textures");
        textures.addProperty("particle", texture);
        textures.addProperty("dial", texture);
      } else {
        var mesh = new IronHeldMesh(texture(nativeModel));
        JsonObject display;
        if (WEAPONS.contains(root))
          display = IronHeldWeapons.build(mesh, root, state, nativeDisplay(nativeModel));
        else if (PAPERS.contains(root)) display = IronHeldPaper.build(mesh, root, state);
        else if (root.equals(WAND)) display = wand(mesh);
        else display = IronHeldRitual.build(mesh, root);
        model = mesh.write(assets, output, state, display);
      }
      write(assets, output, "equipment/held/" + state, model);
      var wrapper =
          nativeModel.has("loader")
              ? nativeModel
              : HeldItemModels.wrapper(MOD + state, held, replaced);
      var perspectives = wrapper.getAsJsonObject("perspectives");
      for (String hand : IronHeldMesh.HANDS) {
        var physical = new JsonObject();
        physical.addProperty("parent", held);
        perspectives.add(hand, physical);
      }
      write(assets, output, state, wrapper);
    }
    var items = new JsonObject();
    for (var roots : List.of(PAPERS, RITUALS, List.of(COMPASS, WAND))) {
      for (String root : roots) {
        var arm = new JsonObject();
        arm.addProperty("scale", 1);
        arm.addProperty("offY", -.18);
        var sides = new JsonObject();
        sides.add("left", arm);
        sides.add("right", arm.deepCopy());
        var item = new JsonObject();
        item.add("armTransforms", sides);
        items.add(MOD + root, item);
      }
    }
    var profile = new JsonObject();
    profile.add("itemSpecific", items);
    EquipmentModels.text(output, assets.resolve(PUNCHY_PROFILE), profile + "\n");
  }

  private static JsonObject providerBase(JsonObject model) {
    return model.has("base") ? model.getAsJsonObject("base") : model;
  }

  static String texture(JsonObject model) throws IOException {
    var base = providerBase(model);
    var textures = base.getAsJsonObject("textures");
    if (textures != null && textures.has("layer0")) return textures.get("layer0").getAsString();
    return texture(HeldItemModels.nativeModel(providerParent(base)));
  }

  private static JsonObject nativeDisplay(JsonObject model) throws IOException {
    var base = providerBase(model);
    if (base.has("display")) return base.getAsJsonObject("display");
    if (!base.has("parent")) return new JsonObject();
    String parent = base.get("parent").getAsString();
    if (parent.startsWith(MOD))
      return nativeDisplay(HeldItemModels.nativeModel(providerParent(base)));
    if (parent.equals("item/crossbow"))
      return HeldItemModels.nativeModel("minecraft:crossbow").getAsJsonObject("display");
    return new JsonObject();
  }

  private static String providerParent(JsonObject model) throws IOException {
    if (!model.has("parent") || !model.get("parent").getAsString().startsWith(MOD + "item/"))
      throw new IOException("Expected an Iron's Spells model parent: " + model);
    return model.get("parent").getAsString().replace(":item/", ":");
  }

  private static void write(Path assets, Map<Path, byte[]> output, String state, JsonObject model) {
    EquipmentModels.text(
        output,
        assets.resolve("irons_spellbooks/models/item/" + state + ".json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(model) + "\n");
  }

  private static JsonObject compass(Path assets, Map<Path, byte[]> output) throws IOException {
    var mesh = new IronHeldMesh(MOD + "item/" + COMPASS_FRAME + "16");
    mesh.patch("case", 5, 1, 11, 2);
    mesh.field("steel", 7, 2, 10, 3);
    mesh.field("bezel", 6, 10, 10, 11);
    mesh.dial("dial", 3.6, 3.22, 12.4, 8.82);
    int sides = 12;
    var front = new P(0, 0, 1);
    var back = new P(0, 0, -1);
    var center = new P(8, 8, 8.45);
    double[][] panel = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
    for (int side = 0; side < sides; side++) {
      double a = side * 2 * Math.PI / sides, b = (side + 1) * 2 * Math.PI / sides;
      var backA = rim(a, 2.5, 7.05);
      var backB = rim(b, 2.5, 7.05);
      var outerA = rim(a, 2.75, 8.55);
      var outerB = rim(b, 2.75, 8.55);
      var bezelA = rim(a, 2.56, 9.05);
      var bezelB = rim(b, 2.56, 9.05);
      var mouthA = rim(a, 2.20, 9.05);
      var mouthB = rim(b, 2.20, 9.05);
      var dialA = rim(a, 2.05, 8.45);
      var dialB = rim(b, 2.05, 8.45);
      var outward = new P(Math.cos((a + b) / 2), Math.sin((a + b) / 2), 0);
      mesh.obj.quad("case", new P[] {new P(8, 8, 7.05), backB, backA, backA}, back, panel);
      mesh.obj.quad("steel", new P[] {backA, backB, outerB, outerA}, outward, panel);
      mesh.obj.quad("steel", new P[] {outerA, outerB, bezelB, bezelA}, outward, panel);
      mesh.obj.quad("bezel", new P[] {bezelA, bezelB, mouthB, mouthA}, front, panel);
      mesh.obj.quad("case", new P[] {mouthA, mouthB, dialB, dialA}, front, panel);
      mesh.obj.quad(
          "dial",
          new P[] {center, dialA, dialB, dialB},
          front,
          new double[][] {{.5, .5}, dialUv(a), dialUv(b), dialUv(b)});
    }
    return mesh.write(
        assets, output, COMPASS, IronHeldMesh.display(new P(8, 6.0, 7.7), .86, -32, -15, 0));
  }

  private static P rim(double angle, double radius, double z) {
    return new P(8 + radius * Math.cos(angle), 8 + radius * Math.sin(angle), z);
  }

  private static double[] dialUv(double angle) {
    return new double[] {.5 + .5 * Math.cos(angle), .5 - .5 * Math.sin(angle)};
  }

  private static JsonObject wand(IronHeldMesh mesh) throws IOException {
    mesh.field("wood", 6, 9, 8, 11);
    mesh.field("gold", 1, 13, 2, 15);
    mesh.field("crystal", 12, 3, 15, 5);
    mesh.glow("crystal", 6);
    var body = mesh.obj;
    body.plate(
        "wood",
        "wood",
        List.of(
            new P(6.48, 3.25, 8),
            new P(8.44, 3.25, 8),
            new P(8.55, 7.0, 8),
            new P(9.55, 9.3, 8),
            new P(9.0, 11.7, 8),
            new P(10.12, 14.85, 8),
            new P(8.42, 15.25, 8),
            new P(7.44, 11.72, 8),
            new P(7.93, 9.32, 8),
            new P(6.78, 7.02, 8)),
        1.66);
    body.box("gold", 6.23, 2.25, 6.98, 8.65, 3.62, 9.02);
    body.shell(
        "gold",
        new P[][] {
          EquipmentObjMesh.octagonalRing(9.29, 13.6, 8, 1.08, 1.06),
          EquipmentObjMesh.octagonalRing(9.4, 14.9, 8, 2.05, 1.82),
          EquipmentObjMesh.octagonalRing(9.4, 15.62, 8, 2.46, 2.15)
        });
    body.shell(
        "crystal",
        new P[][] {
          EquipmentObjMesh.octagonalRing(9.4, 15.31, 8, 1.94, 1.7),
          EquipmentObjMesh.octagonalRing(9.5, 17.15, 8, 2.85, 2.48),
          EquipmentObjMesh.octagonalRing(9.63, 19.28, 8, 1.97, 1.74),
          EquipmentObjMesh.octagonalRing(9.63, 20.0, 8, .93, .84)
        });
    return IronHeldMesh.display(new P(7.65, 6.2, 8), .84, 14, -12, 9);
  }
}
