package dev.aeroscapes.aquatic;

import static dev.aeroscapes.aquatic.IronHeldMesh.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** Alchemical instruments and the cover selected by Modonomicon for The Hermetica. */
final class TheurgyHeldModels {
  static final String HERMETICA_MODEL = "theurgy:the_hermetica_icon";
  private static final String MODELS = "aeroscapes_reimagine:item/equipment/";
  static final List<String> RODS =
      List.of(
          "divination_rod_t1",
          "divination_rod_t2",
          "divination_rod_t3",
          "divination_rod_t4",
          "sulfur_attuned_divination_rod_abundant",
          "sulfur_attuned_divination_rod_common",
          "sulfur_attuned_divination_rod_rare",
          "sulfur_attuned_divination_rod_precious",
          "amethyst_divination_rod");

  private TheurgyHeldModels() {}

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Map<String, JsonObject> held = new LinkedHashMap<>();
    for (String rod : RODS) {
      String id = "theurgy:" + rod;
      JsonObject nativeModel = HeldItemModels.nativeModel(id);
      held.put(id, material("theurgy_divination_rod", id + "/0"));
      for (var override : nativeModel.getAsJsonArray("overrides")) {
        String state = override.getAsJsonObject().get("model").getAsString().replace(":item/", ":");
        held.put(state, material("theurgy_divination_rod", state));
      }
    }
    held.put(
        "theurgy:mercurial_wand", material("theurgy_mercurial_wand", "theurgy:mercurial_wand"));
    held.put(HERMETICA_MODEL, hermetica());
    HeldItemModels.add(assets, output, held);
    Path equipment = assets.resolve("aeroscapes_reimagine/models/item/equipment");
    EquipmentModels.text(
        output, equipment.resolve("theurgy_divination_rod.json"), rod().toString());
    EquipmentModels.text(
        output, equipment.resolve("theurgy_mercurial_wand.json"), wand().toString());
  }

  private static JsonObject hermetica() throws IOException {
    JsonObject model = new JsonObject();
    JsonObject textures = new JsonObject();
    textures.addProperty(
        "surface",
        HeldItemModels.nativeModel(HERMETICA_MODEL)
            .getAsJsonObject("textures")
            .get("layer0")
            .getAsString());
    textures.addProperty("particle", "#surface");
    model.add("textures", textures);
    JsonArray parts = new JsonArray();
    model.add("elements", parts);

    double[] leather = {8, 2, 11, 4};
    box(parts, "back board", new double[] {2.65, 1.95, 6.35, 13.35, 14.05, 6.95}, leather);
    box(parts, "front board", new double[] {2.65, 1.95, 9.05, 13.35, 14.05, 9.65}, leather);
    box(
        parts,
        "bound spine",
        new double[] {2.25, 1.97, 6.56, 3.6, 14.03, 9.45},
        new double[] {8, 8, 10, 10});
    JsonObject pages =
        box(
            parts,
            "text block",
            new double[] {3.4, 2.38, 6.9, 12.93, 13.62, 9.1},
            new double[] {3, 9, 4, 12});
    for (String edge : List.of("east", "west")) {
      pages.getAsJsonObject("faces").getAsJsonObject(edge).addProperty("rotation", 90);
    }
    for (double y : new double[] {4.6, 10.6}) {
      box(
          parts,
          "red closure",
          new double[] {12.0, y, 6.22, 13.48, y + .74, 9.79},
          new double[] {13, 8, 14, 10});
    }
    JsonObject setting =
        box(
            parts,
            "seal mount",
            new double[] {6.1, 6.53, 9.59, 9.9, 9.87, 9.9},
            new double[] {7, 7, 10, 8});
    rotate(setting, "z", 45, vector(8, 8.2, 9.65));
    JsonObject seal =
        box(
            parts,
            "rose seal",
            new double[] {6.76, 7.08, 9.82, 9.24, 9.32, 10.11},
            new double[] {7, 5, 10, 7});
    rotate(seal, "z", 45, vector(8, 8.2, 9.9));

    JsonObject display = new JsonObject();
    for (String hand : List.of("righthand", "lefthand")) {
      boolean left = hand.equals("lefthand");
      JsonObject slots = new JsonObject();
      for (String perspective : List.of("firstperson", "thirdperson")) {
        JsonObject transform = new JsonObject();
        transform.add("rotation", vector(26, -36, 18));
        transform.add("scale", vector(.61, .61, .61));
        slots.add(perspective + "_" + hand, transform);
      }
      HeldItemModels.anchorHeld(slots, new Vector3f(left ? 3.8f : 12.15f, 2.65f, 8));
      for (var slot : slots.entrySet()) display.add(slot.getKey(), slot.getValue());
    }
    model.add("display", display);
    return model;
  }

  private static JsonObject material(String geometry, String id) throws IOException {
    JsonObject model = new JsonObject();
    model.addProperty("parent", MODELS + geometry);
    String sprite =
        HeldItemModels.nativeModel(id).getAsJsonObject("textures").get("layer0").getAsString();
    JsonObject textures = new JsonObject();
    textures.addProperty("surface", sprite);
    textures.addProperty("particle", "#surface");
    model.add("textures", textures);
    return model;
  }

  private static JsonObject rod() {
    JsonObject model = instrument(3.8f, .76);
    JsonArray parts = model.getAsJsonArray("elements");
    double[] wood = {5, 6, 7, 9};
    box(parts, "shaft", new double[] {7.36, .8, 7.37, 8.64, 7.2, 8.63}, wood);
    box(
        parts,
        "copper grip",
        new double[] {7.16, 2, 7.19, 8.84, 5.6, 8.81},
        new double[] {3, 11, 5, 13});
    box(parts, "fork socket", new double[] {6.65, 6.2, 7.05, 9.35, 8.05, 8.95}, wood);
    fork(parts, wood, 7.5, 7.65, 15.1, .77, .56);
    box(parts, "crystal seat", new double[] {7.12, 7.85, 7.24, 8.88, 8.9, 8.76}, wood);
    crystal(
        parts,
        8.75,
        15.65,
        1.52,
        new double[] {8, 6, 11, 8},
        new double[] {10, 3, 13, 5},
        new double[] {12, 1, 15, 3});
    return model;
  }

  private static JsonObject wand() {
    JsonObject model = instrument(4.1f, .73);
    JsonArray parts = model.getAsJsonArray("elements");
    double[] copper = {7, 3, 10, 5};
    box(
        parts,
        "copper shaft",
        new double[] {7.33, .45, 7.34, 8.67, 9.05, 8.66},
        new double[] {4, 10, 6, 12});
    box(parts, "heel cap", new double[] {7.21, .35, 7.22, 8.79, 1.55, 8.78}, copper);
    box(parts, "ferrule", new double[] {6.76, 7.7, 6.88, 9.24, 9.55, 9.12}, copper);
    fork(parts, copper, 7.4, 8.75, 15.6, .66, .62);
    box(
        parts,
        "blue crosspiece",
        new double[] {5.7, 9.45, 7.11, 10.3, 10.3, 8.89},
        new double[] {5, 7, 8, 8});
    crystal(
        parts,
        8.8,
        13.45,
        1.65,
        new double[] {8, 8, 10, 11},
        new double[] {8, 5, 11, 8},
        new double[] {8, 5, 10, 7});
    return model;
  }

  private static JsonObject instrument(float grip, double scale) {
    JsonObject model = new JsonObject();
    JsonObject display = new JsonObject();
    for (String hand : List.of("righthand", "lefthand")) {
      JsonObject third = new JsonObject();
      third.add("rotation", vector(0, -56, 45));
      third.add("scale", vector(scale, scale, scale));
      display.add("thirdperson_" + hand, third);
      JsonObject first = new JsonObject();
      first.add("rotation", vector(0, -55, 33));
      first.add("scale", vector(scale, scale, scale));
      display.add("firstperson_" + hand, first);
    }
    HeldItemModels.anchorHeld(display, new Vector3f(8, grip, 8));
    model.add("display", display);
    model.add("elements", new JsonArray());
    return model;
  }

  private static void fork(
      JsonArray parts,
      double[] region,
      double left,
      double base,
      double tip,
      double radius,
      double halfDepth) {
    double lowerLength = (tip - base) * .66;
    double elbowHeight = base + lowerLength * Math.cos(Math.PI / 8);
    double elbowOffset = lowerLength * Math.sin(Math.PI / 8);
    for (int side : new int[] {-1, 1}) {
      double center = side < 0 ? left : 16 - left;
      JsonObject prong =
          box(
              parts,
              "fork branch",
              new double[] {
                center - radius,
                base - .35,
                8 - halfDepth,
                center + radius,
                base + lowerLength + .16,
                8 + halfDepth
              },
              region);
      rotate(prong, "z", -side * 22.5, vector(center, base, 8));
      double elbow = center + side * elbowOffset;
      JsonObject tine =
          box(
              parts,
              "fork tine",
              new double[] {
                elbow - radius * .75,
                elbowHeight - .35,
                8 - halfDepth * .82,
                elbow + radius * .75,
                tip,
                8 + halfDepth * .82
              },
              region);
      rotate(tine, "z", side * 22.5, vector(elbow, elbowHeight, 8));
    }
  }

  private static void crystal(
      JsonArray parts,
      double base,
      double tip,
      double radius,
      double[] dark,
      double[] body,
      double[] light) {
    double lowerShoulder = base + (tip - base) * .24;
    double upperShoulder = base + (tip - base) * .79;
    for (int band = 0; band < 3; band++) {
      double start = band == 0 ? base : band == 1 ? lowerShoulder : upperShoulder;
      double end = band == 0 ? lowerShoulder : band == 1 ? upperShoulder : tip;
      double half = radius * (band == 1 ? .75 : .47);
      double[] region = band == 0 ? dark : band == 1 ? body : light;
      JsonObject facet =
          box(
              parts,
              "crystal facet",
              new double[] {8 - half, start, 8 - half, 8 + half, end, 8 + half},
              region);
      rotate(facet, "y", 45, vector(8, start, 8));
    }
  }

  private static void rotate(JsonObject part, String axis, double angle, JsonArray origin) {
    JsonObject rotation = new JsonObject();
    rotation.addProperty("axis", axis);
    rotation.addProperty("angle", angle);
    rotation.add("origin", origin);
    part.add("rotation", rotation);
  }

  private static JsonObject box(JsonArray parts, String name, double[] bounds, double[] region) {
    JsonObject part = new JsonObject();
    part.addProperty("name", name);
    part.add("from", vector(bounds[0], bounds[1], bounds[2]));
    part.add("to", vector(bounds[3], bounds[4], bounds[5]));
    JsonObject faces = new JsonObject();
    for (String side : List.of("north", "south", "east", "west", "up", "down")) {
      JsonObject face = new JsonObject();
      face.add("uv", vector(region[0] + .16, region[1] + .16, region[2] - .16, region[3] - .16));
      face.addProperty("texture", "#surface");
      faces.add(side, face);
    }
    part.add("faces", faces);
    parts.add(part);
    return part;
  }
}
