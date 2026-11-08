package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import dev.aeroscapes.atmosphere.EquipmentStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Item-specific utility forms, skinned with their installed provider artwork. */
final class UtilityEquipmentModels {
  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/utility_equipment/";

  private UtilityEquipmentModels() {}

  static boolean supports(String id) {
    return id.startsWith("naturescompass:")
        || id.startsWith("explorerscompass:")
        || id.startsWith("sophisticatedcore:ender_linker")
        || List.of(
                "chipped:watering_can",
                "chipped:alchemy_book",
                "chipped:chisel",
                "chipped:multimeter",
                "chipped:needles",
                "chipped:saw",
                "littletiles:hammer",
                "hybrid_aquatic:coral_hoe",
                "toms_storage:paint_kit",
                "toms_storage:inventory_configurator",
                "supplementaries:wrench",
                "sophisticatedstorage:storage_tool",
                "galosphere:rope_dart",
                "waystones:warp_stone",
                "immersive_weathering:ice_sickle",
                "immersive_weathering:steel_wool")
            .contains(id);
  }

  static JsonObject model(String id, EquipmentStyle style, JsonObject original) {
    JsonObject model = original.deepCopy();
    model.addProperty("loader", "neoforge:obj");
    model.addProperty("model", DIRECTORY + name(id) + ".obj");
    model.addProperty("automatic_culling", false);
    model.addProperty("emissive_ambient", false);
    model.addProperty("flip_v", false);
    model.addProperty("ambientocclusion", false);
    model.addProperty("render_type", "minecraft:cutout");
    model.getAsJsonObject("textures").addProperty("particle", "#layer0");
    JsonObject display = EquipmentSculpture.transforms(id, style, original);
    if (id.equals("littletiles:hammer"))
      display =
          com.google.gson.JsonParser.parseString(
                  """
          {
            "firstperson_righthand": {"rotation": [15, 90, 5], "translation": [1.25, 2.5, 0.5], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [15, 90, 5], "translation": [1.25, 2.5, 0.5], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1.25, 1.5], "scale": [0.95, 0.95, 0.95]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 1.25, 1.5], "scale": [0.95, 0.95, 0.95]}
          }
          """)
              .getAsJsonObject();
    if (id.equals("hybrid_aquatic:coral_hoe"))
      display =
          com.google.gson.JsonParser.parseString(
                  """
          {
            "firstperson_righthand": {"rotation": [15, -180, 5], "translation": [1.25, 1.75, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [15, -180, 5], "translation": [1.25, 1.75, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_righthand": {"rotation": [0, -180, 0], "translation": [0, 1.25, 1.5], "scale": [1, 1, 1]},
            "thirdperson_lefthand": {"rotation": [0, -180, 0], "translation": [0, 1.25, 1.5], "scale": [1, 1, 1]}
          }
          """)
              .getAsJsonObject();
    if (id.equals("chipped:watering_can"))
      display =
          com.google.gson.JsonParser.parseString(
                  """
          {
            "firstperson_righthand": {"rotation": [0, 110, 10], "translation": [1, -1.6, 0], "scale": [0.55, 0.55, 0.55]},
            "firstperson_lefthand": {"rotation": [0, 250, -10], "translation": [1, -1.6, 0], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, -1.8, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, -1.8, 0], "scale": [0.6, 0.6, 0.6]}
          }
          """)
              .getAsJsonObject();
    if (id.equals("chipped:watering_can"))
      HeldItemModels.resizeHeld(display, .9f, 1f, new org.joml.Vector3f(8, 11, 8));
    if (id.equals("galosphere:rope_dart"))
      display =
          com.google.gson.JsonParser.parseString(
                  """
          {
            "firstperson_righthand": {"rotation": [0, -70, 10], "translation": [1.3, -0.7, 0], "scale": [0.55, 0.55, 0.55]},
            "firstperson_lefthand": {"rotation": [0, 70, -10], "translation": [1.3, -0.7, 0], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, -0.85, 0], "scale": [0.65, 0.65, 0.65]},
            "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, -0.85, 0], "scale": [0.65, 0.65, 0.65]}
          }
          """)
              .getAsJsonObject();
    if (id.equals("immersive_weathering:ice_sickle")) {
      // The blade lies in XY, matching the pinned Refined pickaxe's functional axes.
      display =
          com.google.gson.JsonParser.parseString(
                  """
          {
            "firstperson_righthand": {"rotation": [15, 90, 5], "translation": [1.25, 2.5, 0.5], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [15, 90, 5], "translation": [1.25, 2.5, 0.5], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1.25, 1.5], "scale": [0.95, 0.95, 0.95]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 1.25, 1.5], "scale": [0.95, 0.95, 0.95]}
          }
          """)
              .getAsJsonObject();
    }
    if (style == EquipmentStyle.WRENCH || style == EquipmentStyle.GLUE) {
      for (var hand : display.entrySet()) {
        var angles = hand.getValue().getAsJsonObject().getAsJsonArray("rotation");
        angles.set(0, new com.google.gson.JsonPrimitive(style == EquipmentStyle.GLUE ? -25 : 0));
        angles.set(1, new com.google.gson.JsonPrimitive(0));
        angles.set(2, new com.google.gson.JsonPrimitive(0));
      }
    }
    if (id.equals("sophisticatedstorage:storage_tool"))
      for (var hand : display.entrySet()) {
        var scale = hand.getValue().getAsJsonObject().getAsJsonArray("scale");
        for (int axis = 0; axis < 3; axis++) scale.set(axis, new com.google.gson.JsonPrimitive(.6));
      }
    if (id.equals("chipped:needles"))
      for (var hand : display.entrySet())
        hand.getValue()
            .getAsJsonObject()
            .getAsJsonArray("rotation")
            .set(1, new com.google.gson.JsonPrimitive(0));
    if (id.equals("chipped:needles"))
      HeldItemModels.anchorHeld(display, new org.joml.Vector3f(7.4f, 6.27f, 8));
    if (id.equals("chipped:chisel"))
      HeldItemModels.anchorHeld(display, new org.joml.Vector3f(8, 3.8f, 8));
    if (id.equals("chipped:saw"))
      HeldItemModels.anchorHeld(display, new org.joml.Vector3f(12.6f, 3.6f, 8));
    if (id.startsWith("naturescompass:") || id.startsWith("explorerscompass:"))
      HeldItemModels.resizeHeld(display, .72f, .72f, new org.joml.Vector3f(8, 3.17f, 8));
    model.add("display", display);
    return model;
  }

  static void addResources(Path assets, Map<Path, byte[]> output, Map<String, JsonObject> models)
      throws IOException {
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/utility_equipment/saw.png"),
        EquipmentModels.png(sawTexture()));
    StringBuilder materials = new StringBuilder();
    Path directory = assets.resolve("aeroscapes_reimagine/models/item/utility_equipment");
    for (var entry : models.entrySet()) {
      String id = entry.getKey();
      if (!supports(id)) continue;
      EquipmentObjMesh mesh = new EquipmentObjMesh();
      String texture =
          NativeEquipmentModels.inherited(id)
              .getAsJsonObject("textures")
              .get("layer0")
              .getAsString();
      while (texture.startsWith("#"))
        texture =
            entry.getValue().getAsJsonObject("textures").get(texture.substring(1)).getAsString();
      if (id.equals("chipped:saw")) texture = "aeroscapes_reimagine:item/utility_equipment/saw";
      for (var material :
          regions(id).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        String key = name(id) + "_" + material.getKey();
        double[] uv = material.getValue();
        mesh.material(material.getKey(), key);
        double inset = material.getKey().equals("native") ? 0 : .25;
        mesh.region(
            key,
            (uv[0] + inset) / 16,
            (uv[1] + inset) / 16,
            (uv[2] - inset) / 16,
            (uv[3] - inset) / 16);
        materials
            .append("newmtl ")
            .append(key)
            .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd ")
            .append(id.contains("compass:") ? texture.replaceAll("_[0-9]{2}$", "_16") : texture)
            .append("\n\n");
      }
      if (id.contains("compass:")) {
        mesh.material("silver", name(id) + "_body");
        mesh.material("red", name(id) + "_tip");
        mesh.material("needle", name(id) + "_tail");
        mesh.material("marks", name(id) + "_marks");
        mesh.material("dial", name(id) + "_dial");
        VanillaEquipmentMesh.compass(mesh, texture);
        if (id.startsWith("naturescompass:")) leaves(mesh);
      } else if (id.contains("ender_linker")) eye(mesh);
      else if (id.equals("chipped:watering_can")) wateringCan(mesh);
      else if (id.equals("chipped:alchemy_book")) alchemyBook(mesh);
      else if (id.equals("chipped:chisel")) chisel(mesh);
      else if (id.equals("chipped:multimeter")) multimeter(mesh);
      else if (id.equals("chipped:needles")) knittingNeedles(mesh);
      else if (id.equals("chipped:saw")) handSaw(mesh);
      else if (id.equals("hybrid_aquatic:coral_hoe")) coralHoe(mesh);
      else if (id.equals("littletiles:hammer")) littleHammer(mesh);
      else if (id.equals("toms_storage:paint_kit")) paintKit(mesh);
      else if (id.equals("galosphere:rope_dart")) dart(mesh);
      else if (id.equals("waystones:warp_stone")) crystal(mesh);
      else if (id.equals("immersive_weathering:ice_sickle")) iceSickle(mesh);
      else if (id.equals("immersive_weathering:steel_wool")) steelWool(mesh);
      else tool(mesh, id);
      output.put(directory.resolve(name(id) + ".obj"), mesh.obj().getBytes(StandardCharsets.UTF_8));
    }
    output.put(
        directory.resolve("equipment.mtl"),
        (materials.toString().stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8));
  }

  private static String name(String id) {
    return id.replace(':', '_');
  }

  static Map<String, double[]> regions(String id) {
    if (id.equals("littletiles:hammer"))
      return Map.of(
          "shaft", new double[] {6, 10, 7, 11},
          "shaft_light", new double[] {5, 9, 6, 10},
          "shaft_dark", new double[] {7, 10, 8, 11},
          "body", new double[] {8, 6, 10, 7},
          "face", new double[] {6, 2, 8, 4},
          "edge", new double[] {7, 5, 9, 6},
          "shadow", new double[] {6, 5, 7, 6},
          "cap", new double[] {2, 14, 4, 15});
    if (id.startsWith("naturescompass:"))
      return Map.of(
          "body",
          new double[] {5, 2, 11, 4},
          "back",
          new double[] {5, 2, 6, 3},
          "dial",
          new double[] {2, 6, 7, 9},
          "tip",
          new double[] {8, 5, 9, 8},
          "tail",
          new double[] {8, 8, 9, 9},
          "marks",
          new double[] {5, 2, 11, 4},
          "leaf",
          new double[] {3, 1, 4, 4});
    if (id.startsWith("explorerscompass:"))
      return Map.of(
          "body",
          new double[] {5, 2, 8, 4},
          "back",
          new double[] {5, 2, 6, 3},
          "dial",
          new double[] {3, 5, 7, 7},
          "tip",
          new double[] {8, 5, 9, 8},
          "tail",
          new double[] {8, 8, 9, 9},
          "marks",
          new double[] {14, 3, 15, 4});
    if (id.startsWith("sophisticatedcore:ender_linker")) id = "sophisticatedcore:ender_linker";
    return switch (id) {
      case "chipped:alchemy_book" ->
          Map.of(
              "body",
              new double[] {3, 4, 4, 5},
              "spine",
              new double[] {2, 5, 3, 6},
              "binding",
              new double[] {3, 1, 4, 2},
              "paper",
              new double[] {11, 5, 12, 6},
              "edge",
              new double[] {2, 3, 3, 4},
              "gem",
              new double[] {4, 7, 5, 8},
              "gem_dark",
              new double[] {5, 9, 6, 10});
      case "chipped:chisel" ->
          Map.of(
              "grip",
              new double[] {3, 11, 4, 12},
              "grip_dark",
              new double[] {3, 10, 4, 11},
              "body",
              new double[] {11, 4, 12, 5},
              "edge",
              new double[] {12, 4, 13, 5},
              "shadow",
              new double[] {12, 1, 13, 2});
      case "chipped:multimeter" ->
          Map.of(
              "body",
              new double[] {5, 1, 6, 2},
              "edge",
              new double[] {5, 3, 6, 4},
              "face",
              new double[] {6, 2, 7, 3},
              "screen",
              new double[] {6, 4, 7, 5},
              "metal",
              new double[] {3, 8, 4, 9},
              "knob",
              new double[] {5, 0, 6, 1});
      case "chipped:needles" ->
          Map.of(
              "body",
              new double[] {6, 1, 7, 2},
              "edge",
              new double[] {6, 7, 7, 8},
              "shadow",
              new double[] {7, 3, 8, 4});
      case "chipped:saw" ->
          Map.of(
              "grip",
              new double[] {8, 0, 16, 8},
              "grip_dark",
              new double[] {8, 8, 12, 16},
              "body",
              new double[] {0, 0, 8, 16},
              "edge",
              new double[] {12, 8, 16, 12},
              "shadow",
              new double[] {12, 12, 16, 16});
      case "hybrid_aquatic:coral_hoe" ->
          Map.of(
              "grip", new double[] {5, 11, 6, 12},
              "shaft_side", new double[] {4, 11, 5, 12},
              "shaft_dark", new double[] {2, 14, 3, 15},
              "shaft_light", new double[] {3, 13, 4, 14},
              "body", new double[] {11, 5, 13, 7},
              "edge", new double[] {6, 1, 10, 2},
              "collar", new double[] {9, 6, 11, 7});
      case "immersive_weathering:ice_sickle" ->
          Map.of(
              "native", new double[] {0, 0, 16, 16},
              "blade", new double[] {4, 2, 6, 4},
              "edge", new double[] {4, 5, 7, 6},
              "collar", new double[] {4, 5, 7, 6},
              "grip", new double[] {2, 10, 4, 12});
      case "immersive_weathering:steel_wool" ->
          Map.of(
              "body", new double[] {3, 5, 12, 9},
              "edge", new double[] {4, 8, 7, 9});
      case "chipped:watering_can" ->
          Map.of(
              "body",
              new double[] {5, 8, 9, 13},
              "grip",
              new double[] {5, 5, 7, 8},
              "edge",
              new double[] {6, 6, 10, 8},
              "handle",
              new double[] {8, 3, 9, 4},
              "handle_edge",
              new double[] {11, 11, 12, 12},
              "inside",
              new double[] {10, 11, 11, 12},
              "spout",
              new double[] {9, 8, 11, 10},
              "native",
              new double[] {0, 0, 16, 16});
      case "galosphere:rope_dart" ->
          Map.of(
              "body",
              new double[] {10, 3, 12, 5},
              "grip",
              new double[] {7, 13, 10, 14},
              "edge",
              new double[] {13, 3, 14, 5},
              "stopper",
              new double[] {1, 11, 3, 12},
              "native",
              new double[] {0, 0, 16, 16});
      case "supplementaries:wrench" ->
          Map.of(
              "body",
              new double[] {12, 2, 15, 5},
              "grip",
              new double[] {3, 10, 6, 13},
              "edge",
              new double[] {12, 2, 13, 5},
              "native",
              new double[] {0, 0, 16, 16});
      case "toms_storage:inventory_configurator" ->
          Map.of(
              "body",
              new double[] {9, 1, 15, 3},
              "grip",
              new double[] {4, 10, 6, 12},
              "edge",
              new double[] {11, 3, 13, 5},
              "native",
              new double[] {0, 0, 16, 16});
      case "toms_storage:paint_kit" ->
          Map.of(
              "body",
              new double[] {3, 8, 6, 11},
              "grip",
              new double[] {6, 10, 8, 12},
              "edge",
              new double[] {3, 3, 7, 5},
              "band",
              new double[] {3, 5, 6, 7},
              "paint",
              new double[] {12, 2, 15, 4},
              "bristles",
              new double[] {10, 3, 12, 5},
              "native",
              new double[] {0, 0, 16, 16});
      case "sophisticatedcore:ender_linker" ->
          Map.of(
              "body",
              new double[] {5, 7, 6, 10},
              "grip",
              new double[] {5, 3, 11, 4},
              "edge",
              new double[] {5, 3, 11, 4},
              "center",
              new double[] {8, 7, 9, 8},
              "native",
              new double[] {0, 0, 16, 16});
      case "sophisticatedstorage:storage_tool" ->
          Map.of(
              "body",
              new double[] {11, 1, 13, 2},
              "grip",
              new double[] {6, 8, 8, 10},
              "edge",
              new double[] {11, 1, 13, 2},
              "marker",
              new double[] {2, 13, 3, 14},
              "native",
              new double[] {0, 0, 16, 16});
      case "waystones:warp_stone" ->
          Map.of(
              "body",
              new double[] {4, 7, 5, 8},
              "grip",
              new double[] {11, 10, 12, 11},
              "edge",
              new double[] {10, 6, 11, 7},
              "native",
              new double[] {0, 0, 16, 16});
      default -> throw new IllegalArgumentException("Missing utility skin: " + id);
    };
  }

  private static void littleHammer(EquipmentObjMesh mesh) {
    mesh.group("shaft");
    P[] lower = {new P(7, .8, 7), new P(9, .8, 7), new P(9, .8, 9), new P(7, .8, 9)};
    P[] upper = {new P(7, 13.1, 7), new P(9, 13.1, 7), new P(9, 13.1, 9), new P(7, 13.1, 9)};
    String[] surfaces = {"shaft_dark", "shaft", "shaft_light", "shaft"};
    for (int side = 0; side < 4; side++) {
      int next = (side + 1) % 4;
      mesh.quad(
          surfaces[side],
          new P[] {lower[side], lower[next], upper[next], upper[side]},
          lower[side].add(lower[next]).scale(.5).add(new P(-8, 0, -8)),
          new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
    }
    mesh.group("end_cap");
    box(mesh, "cap", 6.9, .1, 6.9, 9.1, 1.1, 9.1);
    mesh.group("socket");
    box(mesh, "shadow", 6.7, 11.9, 6.7, 9.3, 13.4, 9.3);
    mesh.group("head");
    double[][] section = {
      {13, 6.1}, {13.4, 5.7}, {17, 5.7}, {17.4, 6.1},
      {17.4, 9.9}, {17, 10.3}, {13.4, 10.3}, {13, 9.9}
    };
    double[] positions = {3, 3.4, 12.6, 13};
    P[][] rings = new P[positions.length][section.length];
    for (int ring = 0; ring < positions.length; ring++) {
      double inset = ring == 0 || ring == positions.length - 1 ? .35 : 0;
      for (int corner = 0; corner < section.length; corner++) {
        double y = section[corner][0], z = section[corner][1];
        rings[ring][corner] =
            new P(
                positions[ring], y + Math.signum(15.2 - y) * inset, z + Math.signum(8 - z) * inset);
      }
    }
    for (int ring = 0; ring < positions.length - 1; ring++)
      for (int side = 0; side < section.length; side++) {
        int next = (side + 1) % section.length;
        String material = ring != 1 || side % 2 == 0 ? "edge" : "body";
        if (ring == 1 && side == 7) material = "shadow";
        P center = rings[ring][side].add(rings[ring][next]).scale(.5);
        mesh.quad(
            material,
            new P[] {
              rings[ring][side], rings[ring + 1][side], rings[ring + 1][next], rings[ring][next]
            },
            new P(0, center.y() - 15.2, center.z() - 8),
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      }
    for (int ring : new int[] {0, positions.length - 1})
      for (int side = 0; side < section.length; side++)
        triangle(
            mesh,
            "face",
            new P(positions[ring], 15.2, 8),
            rings[ring][side],
            rings[ring][(side + 1) % section.length],
            new P(ring == 0 ? -1 : 1, 0, 0));
  }

  private static void iceSickle(EquipmentObjMesh mesh) {
    sickleHandle(mesh);
    mesh.group("ice_socket");
    mesh.plate(
        "collar",
        "collar",
        List.of(new P(6.6, 14, 8), new P(9.4, 14, 8), new P(9.4, 16.5, 8), new P(6.6, 16.5, 8)),
        2.8);
    mesh.group("ice_blade");
    double[][] sections = {
      {5.8, 15.9, 1.7, 1.3},
      {8, 16.25, 2.5, 1.6},
      {11, 15.8, 2.1, 1.4},
      {13.8, 14.7, 1.6, 1.1},
      {15.8, 12.8, .45, .5}
    };
    P[][] rings = new P[sections.length][4];
    for (int ring = 0; ring < sections.length; ring++) {
      double[] section = sections[ring];
      double top = section[1] + section[2] / 2, bottom = section[1] - section[2] / 2;
      double front = 8 + section[3] / 2, back = 8 - section[3] / 2;
      rings[ring] =
          new P[] {
            new P(section[0], top, front),
            new P(section[0], bottom, front),
            new P(section[0], bottom, back),
            new P(section[0], top, back)
          };
    }
    for (int ring = 0; ring < rings.length - 1; ring++)
      for (int side = 0; side < 4; side++) {
        int next = (side + 1) % 4;
        P a = rings[ring][side], b = rings[ring + 1][side];
        P center = new P(sections[ring][0], sections[ring][1], 8);
        mesh.quad(
            side == 0 || side == 2 ? "blade" : "edge",
            new P[] {a, b, rings[ring + 1][next], rings[ring][next]},
            a.add(rings[ring][next]).scale(.5).add(center.scale(-1)),
            new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
      }
    for (int end : new int[] {0, rings.length - 1})
      mesh.quad(
          "edge",
          rings[end],
          new P(end == 0 ? -1 : 1, 0, 0),
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
  }

  private static void sickleHandle(EquipmentObjMesh mesh) {
    mesh.group("wood_handle");
    double[][] samples = {
      {2, 14.7}, {2, 13.2}, {2.5, 12.2}, {3, 11.2}, {3.5, 10.2}, {4, 9.2}, {4.5, 8.2}
    };
    for (int ring = 0; ring < samples.length - 1; ring++) {
      double lower = (14.7 - samples[ring][1]) / 6.5, upper = (14.7 - samples[ring + 1][1]) / 6.5;
      double u = Math.floor((samples[ring][0] + samples[ring + 1][0]) / 2);
      double v = Math.floor((samples[ring][1] + samples[ring + 1][1]) / 2);
      for (int side = 0; side < 4; side++) {
        int next = (side + 1) % 4;
        mesh.quad(
            "native",
            new P[] {
              sickleHandlePoint(lower, side),
              sickleHandlePoint(upper, side),
              sickleHandlePoint(upper, next),
              sickleHandlePoint(lower, next)
            },
            sickleHandlePoint(0, side)
                .add(sickleHandlePoint(0, next))
                .scale(.5)
                .add(new P(-8, 0, -8)),
            new double[][] {
              {(u + .1) / 16, (v + .9) / 16},
              {(u + .1) / 16, (v + .1) / 16},
              {(u + .9) / 16, (v + .1) / 16},
              {(u + .9) / 16, (v + .9) / 16}
            });
      }
    }
    for (int end : new int[] {0, 1})
      mesh.quad(
          "grip",
          new P[] {
            sickleHandlePoint(end, 0), sickleHandlePoint(end, 1),
            sickleHandlePoint(end, 2), sickleHandlePoint(end, 3)
          },
          new P(0, end == 0 ? -1 : 1, 0),
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
  }

  private static P sickleHandlePoint(double along, int corner) {
    return new P(corner == 0 || corner == 3 ? 7 : 9, 14.8 * along, corner < 2 ? 7 : 9);
  }

  private static void steelWool(EquipmentObjMesh mesh) {
    mesh.group("matted_core");
    mesh.shell(
        "edge",
        new P[][] {
          EquipmentObjMesh.octagonalRing(7.63, 6.05, 8.1, 1.88, .78),
          EquipmentObjMesh.octagonalRing(7.86, 6.92, 8.15, 2.72, 1.18),
          EquipmentObjMesh.octagonalRing(8.17, 8.21, 7.94, 2.55, 1.25),
          EquipmentObjMesh.octagonalRing(8.46, 9.19, 7.94, 1.52, .66)
        });
    mesh.group("folded_fibers");
    woolFold(
        mesh,
        .42,
        new P(5.51, 7.6, 8.69),
        new P(5.23, 8.14, 7.72),
        new P(6.03, 8.95, 7.09),
        new P(7.32, 8.88, 6.53),
        new P(8.66, 8.21, 6.62),
        new P(9.78, 7.42, 7.14),
        new P(10.38, 7.19, 8.09),
        new P(9.81, 7.5, 9.18),
        new P(8.15, 8.12, 9.48),
        new P(6.45, 7.9, 9.27),
        new P(5.51, 7.6, 8.69));
    woolFold(
        mesh,
        .36,
        new P(5.49, 6.62, 7.83),
        new P(6.35, 6.44, 6.89),
        new P(7.23, 6.89, 6.57),
        new P(8.42, 7.08, 6.74),
        new P(9.61, 8.06, 6.82),
        new P(10.51, 8.27, 7.59),
        new P(10.87, 7.76, 8.32),
        new P(9.91, 6.74, 8.97),
        new P(8.48, 6.42, 9.26),
        new P(6.86, 6.35, 9.03),
        new P(5.49, 6.62, 7.83));
    woolFold(
        mesh,
        .4,
        new P(6.1, 7.39, 7.52),
        new P(6.31, 8.38, 6.98),
        new P(7.24, 9.38, 6.84),
        new P(8.13, 9.75, 7.29),
        new P(9.34, 9.48, 7.36),
        new P(10.03, 9.07, 8.11),
        new P(9.21, 8.63, 8.99),
        new P(8.19, 9, 9.08),
        new P(6.87, 8.39, 8.87),
        new P(6.1, 7.39, 7.52));
    woolFold(
        mesh,
        .25,
        new P(6.24, 6.6, 7.35),
        new P(6.94, 5.91, 7.49),
        new P(8.41, 6.1, 7.57),
        new P(9.61, 6.28, 8.3),
        new P(10.04, 7.25, 8.58),
        new P(9.52, 7.58, 8.19));
  }

  private static void woolFold(EquipmentObjMesh mesh, double width, P... path) {
    mesh.tube("body", List.of(path), width, 5, 2.2);
  }

  private static void wateringCan(EquipmentObjMesh mesh) {
    P[] outline = {
      new P(5, 0, 6.2), new P(5.6, 0, 5.6), new P(10.4, 0, 5.6), new P(11, 0, 6.2),
      new P(11, 0, 9.8), new P(10.4, 0, 10.4), new P(5.6, 0, 10.4), new P(5, 0, 9.8)
    };
    mesh.group("body");
    for (int side = 0; side < outline.length; side++) {
      P first = outline[side], next = outline[(side + 1) % outline.length];
      P inner = new P(8 + (first.x() - 8) * .8, 0, 8 + (first.z() - 8) * .8);
      P innerNext = new P(8 + (next.x() - 8) * .8, 0, 8 + (next.z() - 8) * .8);
      P outward = new P(first.x() + next.x() - 16, 0, first.z() + next.z() - 16);
      mesh.group("body");
      mesh.quad(
          "body",
          new P[] {
            first.add(new P(0, 3.2, 0)),
            next.add(new P(0, 3.2, 0)),
            next.add(new P(0, 8.4, 0)),
            first.add(new P(0, 8.4, 0))
          },
          outward,
          new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      mesh.quad(
          "inside",
          new P[] {
            inner.add(new P(0, 3.7, 0)),
            innerNext.add(new P(0, 3.7, 0)),
            innerNext.add(new P(0, 8.4, 0)),
            inner.add(new P(0, 8.4, 0))
          },
          outward.scale(-1),
          new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      mesh.group("rim");
      mesh.quad(
          "edge",
          new P[] {
            first.add(new P(0, 8.4, 0)),
            next.add(new P(0, 8.4, 0)),
            innerNext.add(new P(0, 8.4, 0)),
            inner.add(new P(0, 8.4, 0))
          },
          new P(0, 1, 0),
          new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      mesh.group("base");
      triangle(
          mesh,
          "body",
          new P(8, 3.2, 8),
          first.add(new P(0, 3.2, 0)),
          next.add(new P(0, 3.2, 0)),
          new P(0, -1, 0));
      triangle(
          mesh,
          "inside",
          new P(8, 3.7, 8),
          inner.add(new P(0, 3.7, 0)),
          innerNext.add(new P(0, 3.7, 0)),
          new P(0, 1, 0));
    }
    mesh.group("handle");
    handleArch(
        mesh,
        new double[][] {{5.1, 8.2}, {5.1, 10.3}, {6, 11.4}, {10, 11.4}, {10.9, 10.3}, {10.9, 8.2}},
        new double[][] {
          {5.9, 8.2}, {5.9, 9.95}, {6.45, 10.5}, {9.55, 10.5}, {10.1, 9.95}, {10.1, 8.2}
        },
        1.2);
    mesh.group("side_handle");
    handleArch(
        mesh,
        new double[][] {
          {10.8, 4.2}, {13.7, 4.2}, {14.3, 4.9}, {14.3, 7.7}, {13.7, 8.3}, {10.8, 8.3}
        },
        new double[][] {
          {10.8, 5.15}, {13, 5.15}, {13.35, 5.5}, {13.35, 7}, {13, 7.35}, {10.8, 7.35}
        },
        1.0);
    mesh.group("spout");
    P[][] rings = new P[4][4];
    double[][] sections = {
      {5.4, 4.8, .7, .75}, {1.3, 9, .65, .75}, {.2, 10.1, 1.05, 1.05}, {.2, 10.1, .75, .75}
    };
    for (int ring = 0; ring < rings.length; ring++)
      for (int corner = 0; corner < 4; corner++) {
        double across = (corner == 0 || corner == 3 ? -1 : 1) * sections[ring][2];
        double depth = (corner < 2 ? -1 : 1) * sections[ring][3];
        rings[ring][corner] =
            new P(
                sections[ring][0] + across * .70710678,
                sections[ring][1] + across * .70710678,
                8 + depth);
      }
    for (int ring = 1; ring < rings.length; ring++)
      for (int side = 0; side < 4; side++) {
        int next = (side + 1) % 4;
        P center = new P(sections[ring][0], sections[ring][1], 8);
        P outward =
            ring == 3
                ? new P(-1, 1, 0)
                : rings[ring][side].add(rings[ring][next]).scale(.5).add(center.scale(-1));
        mesh.quad(
            ring == 3 ? "edge" : "spout",
            new P[] {
              rings[ring - 1][side], rings[ring - 1][next], rings[ring][next], rings[ring][side]
            },
            outward,
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      }
    for (int side = 0; side < 4; side++) {
      int next = (side + 1) % 4;
      P inward = new P(.2, 10.1, 8).add(rings[3][side].add(rings[3][next]).scale(-.5));
      mesh.quad(
          "inside",
          new P[] {
            rings[3][side],
            rings[3][next],
            rings[3][next].add(new P(.6, -.6, 0)),
            rings[3][side].add(new P(.6, -.6, 0))
          },
          inward,
          new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
    }
  }

  private static void handleArch(
      EquipmentObjMesh mesh, double[][] outer, double[][] inner, double depth) {
    double signedArea = 0;
    for (int side = 0; side < outer.length; side++) {
      int next = (side + 1) % outer.length;
      signedArea += outer[side][0] * outer[next][1] - outer[next][0] * outer[side][1];
    }
    for (int side = 0; side < outer.length - 1; side++) {
      int next = side + 1;
      for (int face : new int[] {-1, 1})
        mesh.quad(
            "handle",
            new P[] {
              new P(outer[side][0], outer[side][1], 8 + face * depth / 2),
              new P(outer[next][0], outer[next][1], 8 + face * depth / 2),
              new P(inner[next][0], inner[next][1], 8 + face * depth / 2),
              new P(inner[side][0], inner[side][1], 8 + face * depth / 2)
            },
            new P(0, 0, face),
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      for (boolean inside : new boolean[] {false, true}) {
        double[][] profile = inside ? inner : outer;
        P outward =
            new P(profile[next][1] - profile[side][1], profile[side][0] - profile[next][0], 0)
                .scale((signedArea > 0 ? 1 : -1) * (inside ? -1 : 1));
        mesh.quad(
            "handle_edge",
            new P[] {
              new P(profile[side][0], profile[side][1], 8 - depth / 2),
              new P(profile[next][0], profile[next][1], 8 - depth / 2),
              new P(profile[next][0], profile[next][1], 8 + depth / 2),
              new P(profile[side][0], profile[side][1], 8 + depth / 2)
            },
            outward,
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      }
    }
    for (int end : new int[] {0, outer.length - 1}) {
      int neighbor = end == 0 ? 1 : end - 1;
      mesh.quad(
          "handle_edge",
          new P[] {
            new P(outer[end][0], outer[end][1], 8 - depth / 2),
            new P(inner[end][0], inner[end][1], 8 - depth / 2),
            new P(inner[end][0], inner[end][1], 8 + depth / 2),
            new P(outer[end][0], outer[end][1], 8 + depth / 2)
          },
          new P(outer[end][0] - outer[neighbor][0], outer[end][1] - outer[neighbor][1], 0),
          new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
    }
  }

  private static void coralHoe(EquipmentObjMesh mesh) {
    mesh.group("shaft");
    String[] bands = {
      "shaft_side",
      "shaft_light",
      "shaft_light",
      "grip",
      "grip",
      "grip",
      "shaft_light",
      "grip",
      "grip"
    };
    P[] section = {new P(7, 0, 7), new P(9, 0, 7), new P(9, 0, 9), new P(7, 0, 9)};
    for (int band = 0; band < bands.length; band++) {
      double bottom = band * 2, top = Math.min((band + 1) * 2, 16.4);
      for (int side = 0; side < 4; side++) {
        int next = (side + 1) % 4;
        String material = bands[band];
        if (side == 1) material = "shaft_dark";
        if (side == 3) material = "shaft_side";
        mesh.quad(
            material,
            new P[] {
              section[side].add(new P(0, bottom, 0)),
              section[next].add(new P(0, bottom, 0)),
              section[next].add(new P(0, top, 0)),
              section[side].add(new P(0, top, 0))
            },
            section[side].add(section[next]).scale(.5).add(new P(-8, 0, -8)),
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      }
    }
    for (double end : new double[] {0, 16.4})
      mesh.quad(
          "shaft_dark",
          new P[] {
            section[0].add(new P(0, end, 0)),
            section[1].add(new P(0, end, 0)),
            section[2].add(new P(0, end, 0)),
            section[3].add(new P(0, end, 0))
          },
          new P(0, end == 0 ? -1 : 1, 0),
          new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
    mesh.group("socket");
    box(mesh, "collar", 6.7, 14.5, 6.5, 9.3, 16.5, 9.5);
    mesh.group("blade");
    double[][] sections = {
      {8.6, 15.8, 1.2, .65}, {10.2, 15.8, 2, .65}, {14.4, 15.1, 2.2, .6}, {16.3, 14.5, 1.8, .4}
    };
    P[][] rings = new P[sections.length][4];
    for (int ring = 0; ring < sections.length; ring++)
      for (int corner = 0; corner < 4; corner++)
        rings[ring][corner] =
            new P(
                8 + (corner == 0 || corner == 3 ? -1 : 1) * sections[ring][2],
                sections[ring][1] + (corner < 2 ? -1 : 1) * sections[ring][3],
                sections[ring][0]);
    for (int ring = 1; ring < rings.length; ring++)
      for (int side = 0; side < 4; side++) {
        int next = (side + 1) % 4;
        P outward =
            rings[ring][side]
                .add(rings[ring][next])
                .scale(.5)
                .add(new P(-8, -sections[ring][1], -sections[ring][0]));
        mesh.quad(
            side % 2 == 0 ? "body" : "edge",
            new P[] {
              rings[ring - 1][side], rings[ring - 1][next], rings[ring][next], rings[ring][side]
            },
            outward,
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      }
    mesh.quad("edge", rings[0], new P(0, 0, -1), new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
    mesh.quad(
        "edge",
        rings[rings.length - 1],
        new P(0, 0, 1),
        new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
  }

  private static void paintKit(EquipmentObjMesh mesh) {
    mesh.group("bucket");
    frustum(mesh, "body", 6, 8, 2.7, 2.7, 1.8, 6.9, true, false);
    frustum(mesh, "band", 6, 8, 2.7, 2.7, 6.9, 7.8, false, false);
    frustum(mesh, "edge", 6, 8, 2.7, 2.7, 7.8, 8.4, false, true);
    mesh.group("brush");
    box(mesh, "grip", 10, 1.5, 7.5, 11.3, 11.5, 8.5);
    box(mesh, "body", 9.8, 11.5, 7.2, 11.5, 12.5, 8.8);
    box(mesh, "bristles", 9.8, 12.5, 7.2, 11.5, 14, 8.8);
    box(mesh, "paint", 9.8, 14, 7.2, 11.5, 15.5, 8.8);
  }

  private static void alchemyBook(EquipmentObjMesh mesh) {
    mesh.group("pages");
    box(mesh, "paper", 3.5, 2, 6.5, 12, 13.3, 9.5);
    mesh.group("covers");
    box(mesh, "body", 3.5, 1.5, 6, 12.5, 14, 6.5);
    box(mesh, "body", 3.5, 1.5, 9.5, 12.5, 14, 10);
    box(mesh, "spine", 2.5, 1.5, 6, 3.5, 14, 10);
    for (double y : new double[] {1.65, 13.05})
      box(mesh, "binding", 2.48, y, 5.98, 3.52, y + .8, 10.02);
    for (int x : new int[] {3, 11})
      for (double y : new double[] {2, 12.5}) box(mesh, "edge", x, y, 10, x + 1, y + 1, 10.2);
    mesh.group("jewel");
    mesh.beam("gem_dark", new P(7.5, 8, 10), new P(7.5, 8, 10.2), 1.3, 6);
    mesh.beam("gem", new P(7.5, 8, 10.2), new P(7.5, 8, 10.5), 1.1, 6);
  }

  private static void chisel(EquipmentObjMesh mesh) {
    mesh.group("grip");
    box(mesh, "grip", 7, 1, 7, 9, 6.8, 9);
    box(mesh, "grip_dark", 6.9, .6, 6.9, 9.1, 1.3, 9.1);
    mesh.group("ferrule");
    box(mesh, "shadow", 6.9, 6.8, 6.9, 9.1, 7.6, 9.1);
    mesh.group("steel");
    double[][] sections = {{7.6, .65, .65}, {11.8, .65, .65}, {13.2, 1.1, .5}, {15.5, 1.1, .06}};
    P[][] rings = new P[sections.length][4];
    for (int ring = 0; ring < sections.length; ring++) {
      double[] section = sections[ring];
      rings[ring] =
          new P[] {
            new P(8 - section[1], section[0], 8 - section[2]),
            new P(8 + section[1], section[0], 8 - section[2]),
            new P(8 + section[1], section[0], 8 + section[2]),
            new P(8 - section[1], section[0], 8 + section[2])
          };
    }
    for (int ring = 0; ring < rings.length - 1; ring++)
      for (int side = 0; side < 4; side++) {
        int next = (side + 1) % 4;
        P center = rings[ring][side].add(rings[ring][next]).scale(.5);
        mesh.quad(
            side % 2 == 0 ? "edge" : "body",
            new P[] {
              rings[ring][side], rings[ring][next], rings[ring + 1][next], rings[ring + 1][side]
            },
            new P(center.x() - 8, 0, center.z() - 8),
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      }
    for (int ring : new int[] {0, rings.length - 1})
      mesh.quad(
          "body",
          rings[ring],
          new P(0, ring == 0 ? -1 : 1, 0),
          new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
  }

  private static void multimeter(EquipmentObjMesh mesh) {
    mesh.group("housing");
    box(mesh, "body", 3, 2.5, 6.7, 13, 13.5, 9.3);
    mesh.group("face");
    box(mesh, "face", 3.7, 3.2, 9.25, 12.3, 12.8, 9.48);
    mesh.group("screen");
    box(mesh, "screen", 4.8, 10, 9.48, 11.2, 11.9, 9.6);
    mesh.group("selector");
    mesh.beam("metal", new P(8, 6.5, 9.48), new P(8, 6.5, 10), 1.85, 8);
    mesh.beam("knob", new P(8, 6.5, 10), new P(8, 6.5, 10.16), 1.2, 8);
    mesh.plate(
        "metal",
        "metal",
        List.of(
            new P(7.5, 5.9, 10.2), new P(7.8, 5.75, 10.2),
            new P(8.5, 7.1, 10.2), new P(8.2, 7.25, 10.2)),
        .1);
    for (int side : new int[] {-1, 1})
      mesh.beam("edge", new P(8 + side * 1.1, 3.8, 9.48), new P(8 + side * 1.1, 3.8, 9.6), .35, 6);
  }

  private static void knittingNeedles(EquipmentObjMesh mesh) {
    for (int needle = 0; needle < 2; needle++) {
      mesh.group(needle == 0 ? "first_needle" : "second_needle");
      double angle = needle == 0 ? 0 : -Math.PI / 6;
      double center = needle == 0 ? 7.4 : 8.4;
      double z = needle == 0 ? 7.6 : 8.4;
      double[][] sections = {{1, .045}, {3.0, .32}, {14, .32}, {14, .62}, {15, .62}};
      P[][] rings = new P[sections.length][6];
      for (int ring = 0; ring < sections.length; ring++)
        for (int side = 0; side < 6; side++) {
          double turn = side * Math.PI / 3;
          double x = sections[ring][1] * Math.cos(turn), y = sections[ring][0] - 8;
          rings[ring][side] =
              new P(
                  center + x * Math.cos(angle) - y * Math.sin(angle),
                  8 + x * Math.sin(angle) + y * Math.cos(angle),
                  z + sections[ring][1] * Math.sin(turn));
        }
      for (int ring = 0; ring < rings.length - 1; ring++)
        for (int side = 0; side < 6; side++) {
          int next = (side + 1) % 6;
          P middle = rings[ring][side].add(rings[ring][next]).scale(.5);
          P axis =
              new P(
                  center - (sections[ring][0] - 8) * Math.sin(angle),
                  8 + (sections[ring][0] - 8) * Math.cos(angle),
                  z);
          P outward = middle.add(axis.scale(-1));
          if (ring == 2) outward = new P(Math.sin(angle), -Math.cos(angle), 0);
          mesh.quad(
              side % 3 == 0 ? "edge" : side % 3 == 1 ? "body" : "shadow",
              new P[] {
                rings[ring][side], rings[ring][next], rings[ring + 1][next], rings[ring + 1][side]
              },
              outward,
              new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
        }
      for (int ring : new int[] {0, rings.length - 1}) {
        P midpoint =
            new P(
                center - (sections[ring][0] - 8) * Math.sin(angle),
                8 + (sections[ring][0] - 8) * Math.cos(angle),
                z);
        for (int side = 0; side < 6; side++)
          triangle(
              mesh,
              "body",
              midpoint,
              rings[ring][side],
              rings[ring][(side + 1) % 6],
              new P(-Math.sin(angle), Math.cos(angle), 0).scale(ring == 0 ? -1 : 1));
      }
    }
  }

  static java.awt.image.BufferedImage sawTexture() throws IOException {
    var nativeImage = NativeEquipmentModels.image("chipped:item/saw");
    var image =
        new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    int steel = nativeImage.getRGB(4, 1),
        steelLight = nativeImage.getRGB(4, 2),
        edge = nativeImage.getRGB(3, 2),
        dark = nativeImage.getRGB(4, 0),
        wood = nativeImage.getRGB(9, 9),
        woodLight = nativeImage.getRGB(10, 9),
        woodDark = nativeImage.getRGB(13, 8);
    for (int y = 0; y < 32; y++)
      for (int x = 0; x < 32; x++) {
        int color;
        if (x < 16) {
          color = steel;
          if (x >= 14 || x == 4 && y >= 6 && y < 16 || x == 9 && y >= 22 && y < 28)
            color = steelLight;
          if (x < 2 || x == 6 && y >= 18 && y < 20) color = edge;
        } else if (y < 16) {
          int column = (x - 16) / 2, row = y / 2;
          color = wood;
          if (column == 1 && row < 6 || column == 5 && row > 1 || column == 6 && row < 2)
            color = woodLight;
          if (column == 3 && row > 2 && row < 7 || column == 7) color = woodDark;
        } else if (x < 24) color = x < 18 || y > 27 && x >= 22 ? wood : woodDark;
        else if (y < 24) color = x < 26 ? steelLight : edge;
        else color = dark;
        image.setRGB(x, y, color);
      }
    return image;
  }

  private static void handSaw(EquipmentObjMesh mesh) {
    mesh.group("blade");
    List<P> outline = new java.util.ArrayList<>();
    outline.add(new P(7.7, 5.1, 8));
    outline.add(new P(4, 6.7, 8));
    for (int tooth = 0; tooth < 10; tooth++) {
      double y = 7 + tooth * 1.1;
      outline.add(new P(4 + (y - 7) * .18, y, 8));
      outline.add(new P(3.2 + (y - 7) * .18, y + .4, 8));
      outline.add(new P(4 + (y + .85 - 7) * .18, y + .85, 8));
    }
    outline.addAll(
        List.of(new P(6.1, 18.5, 8), new P(9, 18.5, 8), new P(11.3, 6.7, 8), new P(10.1, 5.1, 8)));
    mesh.plate("body", "edge", outline, .6);
    mesh.group("handle");
    List<P> outer =
        List.of(
            new P(8.7, .8, 8),
            new P(12.4, 1.5, 8),
            new P(13.4, 2.8, 8),
            new P(13.2, 5.2, 8),
            new P(11.2, 7.2, 8),
            new P(8.4, 6.7, 8),
            new P(7.3, 4.5, 8),
            new P(7.3, 2.1, 8));
    List<P> inner =
        List.of(
            new P(9.1, 2.3, 8),
            new P(11.5, 2.7, 8),
            new P(11.7, 3.3, 8),
            new P(11.35, 4.7, 8),
            new P(10.6, 5.6, 8),
            new P(9.2, 5.25, 8),
            new P(8.8, 4.2, 8),
            new P(8.65, 3, 8));
    for (int index = 0; index < outer.size(); index++) {
      int next = (index + 1) % outer.size();
      for (int side : new int[] {-1, 1}) {
        P a = sawCheek(outer.get(index), false, side),
            b = sawCheek(outer.get(next), false, side),
            c = sawCheek(inner.get(next), true, side),
            d = sawCheek(inner.get(index), true, side);
        mesh.quad("grip", new P[] {a, b, c, d}, new P(0, 0, side), sawWoodUv(a, b, c, d));
        for (boolean hole : new boolean[] {false, true}) {
          P first = (hole ? inner : outer).get(index), last = (hole ? inner : outer).get(next);
          P cheekFirst = sawCheek(first, hole, side), cheekLast = sawCheek(last, hole, side);
          mesh.quad(
              "grip",
              new P[] {
                first.add(new P(0, 0, side * .65)),
                last.add(new P(0, 0, side * .65)),
                cheekLast,
                cheekFirst
              },
              new P(
                  (last.y() - first.y()) * (hole ? -1 : 1),
                  (first.x() - last.x()) * (hole ? -1 : 1),
                  side),
              sawWoodUv(first, last, cheekLast, cheekFirst));
        }
      }
      for (boolean hole : new boolean[] {false, true}) {
        P first = (hole ? inner : outer).get(index), last = (hole ? inner : outer).get(next);
        mesh.quad(
            "grip_dark",
            new P[] {
              first.add(new P(0, 0, -.65)),
              last.add(new P(0, 0, -.65)),
              last.add(new P(0, 0, .65)),
              first.add(new P(0, 0, .65))
            },
            new P(last.y() - first.y(), first.x() - last.x(), 0).scale(hole ? -1 : 1),
            new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
      }
    }
    mesh.group("rivets");
    for (int side : new int[] {-1, 1})
      for (P rivet : List.of(new P(8.25, 4.8, 8), new P(10.3, 6.25, 8)))
        box(
            mesh,
            "edge",
            rivet.x() - .3,
            rivet.y() - .3,
            8 + side * .96 - .08,
            rivet.x() + .3,
            rivet.y() + .3,
            8 + side * .96 + .08);
  }

  private static P sawCheek(P point, boolean inner, int side) {
    double scale = inner ? 1.07 : .95;
    return new P(10.3 + (point.x() - 10.3) * scale, 3.9 + (point.y() - 3.9) * scale, 8 + side * .9);
  }

  private static double[][] sawWoodUv(P... points) {
    double[][] uv = new double[points.length][2];
    for (int index = 0; index < points.length; index++) {
      uv[index][0] = (points[index].x() - 7) / 7;
      uv[index][1] = (7.5 - points[index].y()) / 7;
    }
    return uv;
  }

  private static void dart(EquipmentObjMesh mesh) {
    mesh.group("cord");
    mesh.tube(
        "grip",
        List.of(
            new P(8, 9.7, 8),
            new P(7.5, 7.4, 8.5),
            new P(5.3, 6.3, 8.9),
            new P(4.6, 4.7, 8.5),
            new P(6, 3.7, 7.5),
            new P(8.6, 3.9, 6.9),
            new P(10.2, 5.2, 7.1),
            new P(9.2, 6.6, 7.8),
            new P(6.8, 6.4, 8.5),
            new P(4.2, 5.1, 9.1)),
        .23,
        4,
        3);
    mesh.group("neck");
    box(mesh, "grip", 7.55, 9.2, 7.65, 8.45, 10.8, 8.35);
    mesh.group("point");
    double[][] sections = {{10.5, .55, .4}, {11.9, 1.2, .8}, {13.2, 1, .65}, {15.6, .04, .03}};
    P[][] rings = new P[sections.length][4];
    for (int ring = 0; ring < rings.length; ring++)
      for (int corner = 0; corner < 4; corner++)
        rings[ring][corner] =
            new P(
                8 + (corner == 0 || corner == 3 ? -1 : 1) * sections[ring][1],
                sections[ring][0],
                8 + (corner < 2 ? -1 : 1) * sections[ring][2]);
    for (int ring = 1; ring < rings.length; ring++)
      for (int side = 0; side < 4; side++) {
        int next = (side + 1) % 4;
        mesh.quad(
            side % 2 == 0 ? "body" : "edge",
            new P[] {
              rings[ring - 1][side], rings[ring - 1][next], rings[ring][next], rings[ring][side]
            },
            rings[ring][side]
                .add(rings[ring][next])
                .scale(.5)
                .add(new P(-8, -sections[ring][0], -8)),
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
      }
    for (int end : new int[] {0, rings.length - 1})
      mesh.quad(
          "edge",
          rings[end],
          new P(0, end == 0 ? -1 : 1, 0),
          new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
    mesh.group("stopper");
    box(mesh, "stopper", 3.8, 4.65, 8.65, 4.6, 5.55, 9.55);
  }

  private static void tool(EquipmentObjMesh mesh, String id) {
    if (id.equals("toms_storage:inventory_configurator")) {
      mesh.beam("grip", new P(8, 1.5, 8), new P(8, 10.3, 8), .6, 4);
      mesh.group("closed_head");
      box(mesh, "body", 4.8, 10.3, 6.4, 11.2, 16, 9.6);
      box(mesh, "edge", 7.3, 12, 9.6, 8.7, 14, 9.9);
    } else if (id.equals("sophisticatedstorage:storage_tool")) {
      mesh.group("wood_handle");
      mesh.beam("grip", new P(8, -1.2, 8), new P(8, 12, 8), .55, 6);
      double joint = .5 / Math.sqrt(2);
      mesh.plate(
          "grip",
          "grip",
          List.of(
              new P(7.45, 11.6, 8),
              new P(7.293 - joint, 12.95 - joint, 8),
              new P(7.293 + joint, 12.95 + joint, 8),
              new P(8.707, 13.8, 8),
              new P(10.121 - joint, 12.95 + joint, 8),
              new P(10.121 + joint, 12.95 - joint, 8),
              new P(8.55, 11.6, 8)),
          1.0);
      for (int side : new int[] {-1, 1})
        mesh.quad(
            "marker",
            new P[] {
              new P(8, .62, 8 + side * .52),
              new P(8.4, .22, 8 + side * .52),
              new P(8, -.18, 8 + side * .52),
              new P(7.6, .22, 8 + side * .52)
            },
            new P(0, 0, side),
            new double[][] {{.5, 0}, {1, .5}, {.5, 1}, {0, .5}});
      mesh.group("left_jaw");
      metalBranch(
          mesh,
          List.of(
              new P(7.293, 12.95, 8),
              new P(6.586, 13.657, 8),
              new P(6.586, 15.071, 8),
              new P(7.293, 15.778, 8)));
      mesh.group("right_jaw");
      metalBranch(
          mesh,
          List.of(
              new P(10.121, 12.95, 8),
              new P(10.828, 13.657, 8),
              new P(10.828, 15.071, 8),
              new P(10.121, 15.778, 8)));
    } else {
      socket(mesh);
      box(mesh, "grip", 7, 4, 7.3, 9, 10, 8.7);
      mesh.plate(
          "body",
          "edge",
          List.of(
              new P(7, 10, 8),
              new P(5.9, 11.2, 8),
              new P(5.1, 12.7, 8),
              new P(5.5, 14.2, 8),
              new P(6, 14, 8),
              new P(6.9, 12.5, 8),
              new P(8.1, 12, 8),
              new P(9, 12.8, 8),
              new P(8.7, 14, 8),
              new P(8, 15.2, 8),
              new P(8.2, 16, 8),
              new P(10, 15.5, 8),
              new P(11.1, 14, 8),
              new P(10.6, 11.6, 8),
              new P(9, 10, 8)),
          1.4);
    }
  }

  private static void metalBranch(EquipmentObjMesh mesh, List<P> path) {
    List<P> left = new java.util.ArrayList<>(), right = new java.util.ArrayList<>();
    for (int index = 0; index < path.size(); index++) {
      P point = path.get(index),
          before = path.get(Math.max(0, index - 1)),
          after = path.get(Math.min(path.size() - 1, index + 1));
      org.joml.Vector3d first =
          (index == 0 ? after.vector().sub(point.vector()) : point.vector().sub(before.vector()))
              .normalize();
      org.joml.Vector3d second =
          (index == path.size() - 1
              ? first
              : new org.joml.Vector3d(after.vector()).sub(point.vector()).normalize());
      org.joml.Vector3d normal =
          new org.joml.Vector3d(-first.y, first.x, 0)
              .add(new org.joml.Vector3d(-second.y, second.x, 0))
              .normalize();
      double distance = .5 / normal.dot(new org.joml.Vector3d(-second.y, second.x, 0));
      P offset = new P(normal.x * distance, normal.y * distance, 0);
      left.add(point.add(offset));
      right.add(point.add(offset.scale(-1)));
    }
    left.addAll(right.reversed());
    mesh.plate("body", "edge", left, .9);
  }

  private static void eye(EquipmentObjMesh mesh) {
    double[][] profile = {{5.5, 4.3, 0}, {4.6, 3.6, .85}, {2, 1.6, .55}, {1.6, 1.3, .18}};
    for (int side : new int[] {-1, 1}) {
      for (int row = 1; row < profile.length; row++)
        for (int sector = 0; sector < 8; sector++) {
          double a = sector * Math.PI / 4, b = (sector + 1) * Math.PI / 4;
          patch(
              mesh,
              row == 1 ? "grip" : "body",
              new P[] {
                eyePoint(profile[row - 1], a, side),
                eyePoint(profile[row - 1], b, side),
                eyePoint(profile[row], b, side),
                eyePoint(profile[row], a, side)
              },
              new P(0, 0, side),
              new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
        }
      for (int sector = 0; sector < 8; sector++)
        triangle(
            mesh,
            "center",
            new P(8, 8, 8 + side * .18),
            eyePoint(profile[3], sector * Math.PI / 4, side),
            eyePoint(profile[3], (sector + 1) * Math.PI / 4, side),
            new P(0, 0, side));
    }
  }

  private static P eyePoint(double[] profile, double angle, int side) {
    return new P(
        8 + profile[0] * Math.cos(angle), 8 + profile[1] * Math.sin(angle), 8 + side * profile[2]);
  }

  private static void socket(EquipmentObjMesh mesh) {
    List<P> outer =
        List.of(
            new P(8, 1, 8),
            new P(9.2, 1.5, 8),
            new P(9.5, 2.5, 8),
            new P(9, 4, 8),
            new P(7, 4, 8),
            new P(6.5, 2.5, 8),
            new P(6.8, 1.5, 8));
    for (int index = 0; index < outer.size(); index++) {
      P a = outer.get(index), b = outer.get((index + 1) % outer.size());
      P c = new P(8 + (b.x() - 8) * .4, 2.6 + (b.y() - 2.6) * .4, 8),
          d = new P(8 + (a.x() - 8) * .4, 2.6 + (a.y() - 2.6) * .4, 8);
      for (int side : new int[] {-1, 1}) {
        P[] points = {a, b, c, d};
        double[][] uv = new double[4][2];
        for (int vertex = 0; vertex < 4; vertex++) {
          P point = points[vertex];
          uv[vertex] = new double[] {(point.x() - 6.5) / 3, (4 - point.y()) / 3};
          points[vertex] = point.add(new P(0, 0, side * .7));
        }
        mesh.quad("grip", points, new P(0, 0, side), uv);
      }
      mesh.quad(
          "edge",
          new P[] {
            a.add(new P(0, 0, -.7)),
            b.add(new P(0, 0, -.7)),
            b.add(new P(0, 0, .7)),
            a.add(new P(0, 0, .7))
          },
          new P(b.y() - a.y(), a.x() - b.x(), 0),
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
      mesh.quad(
          "edge",
          new P[] {
            c.add(new P(0, 0, -.7)),
            d.add(new P(0, 0, -.7)),
            d.add(new P(0, 0, .7)),
            c.add(new P(0, 0, .7))
          },
          new P(d.y() - c.y(), c.x() - d.x(), 0),
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
    }
  }

  private static void patch(
      EquipmentObjMesh mesh, String material, P[] points, P outward, double[][] uv) {
    mesh.quad(
        material,
        new P[] {points[0], points[1], points[2], points[2]},
        outward,
        new double[][] {uv[0], uv[1], uv[2], uv[2]});
    mesh.quad(
        material,
        new P[] {points[0], points[2], points[3], points[3]},
        outward,
        new double[][] {uv[0], uv[2], uv[3], uv[3]});
  }

  private static void crystal(EquipmentObjMesh mesh) {
    P top = new P(7.3, 12.35, 8.1), bottom = new P(8.65, 3.7, 7.8);
    P[] belt = {
      new P(12.4, 8.4, 8.1), new P(10.1, 8.8, 11.35), new P(5.5, 7.5, 10.8),
      new P(3.6, 8.5, 7.95), new P(5.7, 9.4, 5), new P(10.3, 7.75, 5.4)
    };
    for (int sector = 0; sector < 6; sector++) {
      double a = sector * Math.PI / 3, b = (sector + 1) * Math.PI / 3;
      P first = belt[sector], second = belt[(sector + 1) % belt.length];
      P outward = new P(Math.cos((a + b) / 2), 0, Math.sin((a + b) / 2));
      P topFirst = top.scale(.66).add(first.scale(.34));
      P topSecond = top.scale(.66).add(second.scale(.34));
      P bottomFirst = bottom.scale(.76).add(first.scale(.24));
      P bottomSecond = bottom.scale(.76).add(second.scale(.24));
      P topCenter = new P(7.5, 10.95, 8);
      P bottomCenter = new P(8.5, 4.85, 8);
      mesh.quad(
          sector == 1 || sector == 4 ? "edge" : "body",
          new P[] {topFirst, topSecond, second, first},
          outward,
          null);
      mesh.quad(
          sector < 2 ? "body" : "grip",
          new P[] {bottomFirst, bottomSecond, second, first},
          outward,
          null);
      triangle(mesh, "edge", topCenter, topFirst, topSecond, new P(0, 1, 0));
      triangle(mesh, "grip", bottomCenter, bottomSecond, bottomFirst, new P(0, -1, 0));
    }
  }

  private static void leaves(EquipmentObjMesh mesh) {
    for (int side : new int[] {-1, 1})
      for (int row : new int[] {-1, 1}) {
        P base = new P(8 + side * 3.65, 8 + row * 1.85, 8.38);
        P tip = new P(8 + side * 5.25, 8 + row * 3.45, 8.7);
        P edge = new P(8 + side * 3.88, 8 + row * 3.22, 8.53);
        for (int face : new int[] {-1, 1})
          triangle(mesh, "leaf", base, tip, edge, new P(0, 0, face));
      }
  }

  private static void frustum(
      EquipmentObjMesh mesh,
      String material,
      double x,
      double z,
      double first,
      double second,
      double bottom,
      double top,
      boolean capBottom,
      boolean capTop) {
    wall(mesh, material, x, z, first, second, bottom, top, false);
    if (capBottom) cap(mesh, material, x, z, first, bottom, -1);
    if (capTop) cap(mesh, material, x, z, second, top, 1);
  }

  private static void wall(
      EquipmentObjMesh mesh,
      String material,
      double x,
      double z,
      double first,
      double second,
      double bottom,
      double top,
      boolean inside) {
    for (int index = 0; index < 8; index++) {
      double a = index * Math.PI / 4, b = (index + 1) * Math.PI / 4;
      mesh.quad(
          material,
          new P[] {
            new P(x + first * Math.cos(a), bottom, z + first * Math.sin(a)),
            new P(x + first * Math.cos(b), bottom, z + first * Math.sin(b)),
            new P(x + second * Math.cos(b), top, z + second * Math.sin(b)),
            new P(x + second * Math.cos(a), top, z + second * Math.sin(a))
          },
          new P(Math.cos((a + b) / 2), 0, Math.sin((a + b) / 2)).scale(inside ? -1 : 1),
          new double[][] {
            {index / 8.0, 1}, {(index + 1) / 8.0, 1}, {(index + 1) / 8.0, 0}, {index / 8.0, 0}
          });
    }
  }

  private static void cap(
      EquipmentObjMesh mesh,
      String material,
      double x,
      double z,
      double radius,
      double y,
      int side) {
    for (int index = 0; index < 8; index++) {
      double a = index * Math.PI / 4, b = (index + 1) * Math.PI / 4;
      mesh.quad(
          material,
          new P[] {
            new P(x, y, z),
            new P(x + radius * Math.cos(a), y, z + radius * Math.sin(a)),
            new P(x + radius * Math.cos(b), y, z + radius * Math.sin(b)),
            new P(x + radius * Math.cos(b), y, z + radius * Math.sin(b))
          },
          new P(0, side, 0),
          new double[][] {
            {.5, .5},
            {.5 + .5 * Math.cos(a), .5 + .5 * Math.sin(a)},
            {.5 + .5 * Math.cos(b), .5 + .5 * Math.sin(b)},
            {.5 + .5 * Math.cos(b), .5 + .5 * Math.sin(b)}
          });
    }
  }

  private static void triangle(EquipmentObjMesh mesh, String material, P a, P b, P c, P outward) {
    mesh.quad(
        material, new P[] {a, b, c, c}, outward, new double[][] {{.5, .5}, {0, 1}, {1, 1}, {1, 1}});
  }

  private static void box(
      EquipmentObjMesh mesh,
      String material,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2) {
    P a = new P(x1, y1, z1),
        b = new P(x2, y1, z1),
        c = new P(x2, y2, z1),
        d = new P(x1, y2, z1),
        e = new P(x1, y1, z2),
        f = new P(x2, y1, z2),
        g = new P(x2, y2, z2),
        h = new P(x1, y2, z2);
    P[][] faces = {
      {a, b, c, d}, {e, f, g, h}, {a, e, h, d}, {b, f, g, c}, {a, b, f, e}, {d, c, g, h}
    };
    P[] normals = {
      new P(0, 0, -1),
      new P(0, 0, 1),
      new P(-1, 0, 0),
      new P(1, 0, 0),
      new P(0, -1, 0),
      new P(0, 1, 0)
    };
    for (int index = 0; index < faces.length; index++)
      mesh.quad(
          material, faces[index], normals[index], new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
  }
}
