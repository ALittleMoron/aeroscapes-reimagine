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
        || id.startsWith("galosphere:monstrometer")
        || id.startsWith("sophisticatedcore:ender_linker")
        || List.of(
                "chipped:watering_can",
                "toms_storage:paint_kit",
                "toms_storage:inventory_configurator",
                "supplementaries:wrench",
                "sophisticatedstorage:storage_tool",
                "create:super_glue",
                "simulated:honey_glue",
                "galosphere:echo_bell",
                "galosphere:glow_flare",
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
    model.add("display", display);
    return model;
  }

  static void addResources(Path assets, Map<Path, byte[]> output, Map<String, JsonObject> models)
      throws IOException {
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
      else if (id.contains("monstrometer")) dial(mesh);
      else if (id.equals("chipped:watering_can")) wateringCan(mesh);
      else if (id.equals("toms_storage:paint_kit")) paintKit(mesh);
      else if (id.endsWith("glue")) glue(mesh, id);
      else if (id.equals("galosphere:echo_bell")) bell(mesh);
      else if (id.equals("galosphere:glow_flare")) flare(mesh);
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
    if (id.startsWith("naturescompass:"))
      return Map.of(
          "body",
          new double[] {5, 2, 11, 4},
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
          "dial",
          new double[] {3, 5, 7, 7},
          "tip",
          new double[] {8, 5, 9, 8},
          "tail",
          new double[] {8, 8, 9, 9},
          "marks",
          new double[] {14, 3, 15, 4});
    if (id.contains("monstrometer")) id = "galosphere:monstrometer";
    if (id.startsWith("sophisticatedcore:ender_linker")) id = "sophisticatedcore:ender_linker";
    return switch (id) {
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
              "native",
              new double[] {0, 0, 16, 16});
      case "simulated:honey_glue" ->
          Map.of(
              "body",
              new double[] {1, 8, 5, 11},
              "grip",
              new double[] {5, 6, 7, 8},
              "edge",
              new double[] {12, 3, 15, 6},
              "collar",
              new double[] {9, 7, 11, 9},
              "native",
              new double[] {0, 0, 16, 16});
      case "create:super_glue" ->
          Map.of(
              "body",
              new double[] {10, 3, 13, 6},
              "grip",
              new double[] {5, 7, 10, 10},
              "edge",
              new double[] {2, 10, 5, 13},
              "collar",
              new double[] {5, 7, 10, 10},
              "native",
              new double[] {0, 0, 16, 16});
      case "galosphere:echo_bell" ->
          Map.of(
              "body",
              new double[] {6, 4, 9, 8},
              "grip",
              new double[] {1, 12, 4, 15},
              "edge",
              new double[] {4, 9, 6, 11},
              "cavity",
              new double[] {8, 2, 11, 4},
              "native",
              new double[] {0, 0, 16, 16});
      case "galosphere:glow_flare" ->
          Map.of(
              "body",
              new double[] {6, 7, 9, 13},
              "grip",
              new double[] {6, 7, 9, 13},
              "edge",
              new double[] {5, 3, 10, 6},
              "native",
              new double[] {0, 0, 16, 16});
      case "galosphere:rope_dart" ->
          Map.of(
              "body",
              new double[] {10, 2, 14, 5},
              "grip",
              new double[] {7, 13, 10, 14},
              "edge",
              new double[] {10, 2, 14, 5},
              "native",
              new double[] {0, 0, 16, 16});
      case "galosphere:monstrometer" ->
          Map.of(
              "body",
              new double[] {5, 2, 11, 3},
              "grip",
              new double[] {5, 12, 11, 15},
              "edge",
              new double[] {5, 2, 11, 3},
              "pin",
              new double[] {0, 1, 2, 3},
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
              new double[] {5, 5, 11, 12},
              "grip",
              new double[] {11, 9, 14, 11},
              "edge",
              new double[] {3, 6, 6, 10},
              "native",
              new double[] {0, 0, 16, 16});
      default -> throw new IllegalArgumentException("Missing utility skin: " + id);
    };
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
    mesh.group("pad");
    mesh.plate(
        "body",
        "edge",
        List.of(
            new P(4.4, 6.2, 8),
            new P(5.5, 5.3, 8),
            new P(10.6, 5.3, 8),
            new P(11.6, 6.2, 8),
            new P(11.6, 9.8, 8),
            new P(10.6, 10.7, 8),
            new P(5.5, 10.7, 8),
            new P(4.4, 9.8, 8)),
        3);
  }

  private static void wateringCan(EquipmentObjMesh mesh) {
    mesh.group("body");
    shell(mesh, "body", 8, 8, 3.2, 2, 9, .45);
    mesh.group("handle");
    handle(
        mesh,
        "body",
        List.of(
            new P(5.05, 8.6, 8), new P(5.05, 12.3, 8), new P(10.95, 12.3, 8), new P(10.95, 8.6, 8)),
        .35);
    handle(
        mesh,
        "body",
        List.of(new P(10.8, 3.3, 8), new P(14.3, 4.5, 8), new P(14.3, 8, 8), new P(10.8, 9, 8)),
        .38);
    mesh.group("spout");
    mesh.beam("body", new P(5.5, 4, 8), new P(1.5, 10, 8), .65, 6);
    mesh.beam("edge", new P(1.5, 10, 8), new P(.9, 10.8, 8), .95, 8);
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

  private static void glue(EquipmentObjMesh mesh, String id) {
    boolean tube = id.equals("create:super_glue");
    double[][] profile =
        tube
            ? new double[][] {
              {2.8, .55, 1.5},
              {2.8, .65, 2.3},
              {2.5, 1.2, 3.4},
              {2.35, 1.5, 4.5},
              {2.2, 1.4, 7.6},
              {1.1, .85, 9.3},
              {1.1, .85, 10.1},
              {.45, .35, 12.7}
            }
            : new double[][] {
              {2.3, 1.7, 2},
              {2.8, 2, 3.2},
              {2.8, 2, 4.3},
              {2.7, 1.95, 7.3},
              {2.3, 1.7, 8.6},
              {1.25, .9, 9.2},
              {1.3, .95, 10},
              {.65, .45, 12.2}
            };
    List<String> materials =
        tube
            ? List.of("body", "body", "body", "grip", "body", "collar", "edge")
            : List.of("body", "body", "grip", "body", "body", "collar", "edge");
    loft(mesh, profile, materials, false);
    ellipseCap(mesh, "body", profile[0], -1);
    ellipseCap(mesh, "edge", profile[profile.length - 1], 1);
  }

  private static void bell(EquipmentObjMesh mesh) {
    mesh.group("handle");
    mesh.beam("grip", new P(8, 1.5, 8), new P(8, 7.8, 8), .8, 6);
    mesh.beam("edge", new P(8, 7.8, 8), new P(8, 8.5, 8), 1.0, 8);
    mesh.group("bell_shell");
    double[][] profile = {
      {.85, .85, 8.5},
      {1.9, 1.9, 9.2},
      {2.55, 2.55, 10},
      {2.7, 2.7, 11.3},
      {3.15, 3.15, 12.5},
      {4.1, 4.1, 13.4},
      {4.3, 4.3, 14}
    };
    loft(mesh, profile, java.util.Collections.nCopies(profile.length - 1, "body"), false);
    double[][] inner = new double[profile.length][3];
    for (int index = 0; index < profile.length; index++)
      inner[index] =
          new double[] {profile[index][0] - .35, profile[index][1] - .35, profile[index][2]};
    loft(mesh, inner, java.util.Collections.nCopies(profile.length - 1, "cavity"), true);
    annulus(mesh, "body", 8, 8, 4.3, 3.95, 14, 1);
    mesh.group("clapper");
    mesh.beam("cavity", new P(8, 8.5, 8), new P(8, 12.2, 8), .15, 4);
    frustum(mesh, "body", 8, 8, .35, .75, 12.2, 12.7, true, false);
    frustum(mesh, "body", 8, 8, .75, .35, 12.7, 13.2, false, true);
  }

  private static void flare(EquipmentObjMesh mesh) {
    double[] base = {.8, .8, 1}, top = {.8, .8, 10};
    loft(mesh, new double[][] {base, top}, List.of("body"), false);
    ellipseCap(mesh, "body", base, -1);
    ellipseCap(mesh, "body", top, 1);
    mesh.group("flame");
    frustum(mesh, "edge", 8, 8, .9, 1.8, 10, 11.8, true, false);
    frustum(mesh, "edge", 8, 8, 1.8, .25, 11.8, 15.3, false, true);
  }

  private static void dart(EquipmentObjMesh mesh) {
    mesh.group("cord");
    handle(
        mesh,
        "grip",
        List.of(
            new P(7.7, 10, 8),
            new P(7, 6, 8),
            new P(4, 4, 8),
            new P(6, 2.5, 8),
            new P(10, 3, 8),
            new P(11.5, 5, 8),
            new P(6, 5, 8)),
        .23);
    mesh.group("point");
    frustum(mesh, "body", 8, 8, .55, 1.3, 9.2, 11.5, true, false);
    frustum(mesh, "edge", 8, 8, 1.3, .04, 11.5, 15.2, false, true);
  }

  private static void dial(EquipmentObjMesh mesh) {
    VanillaEquipmentMesh.caseShell(mesh, "body");
    mesh.group("native_face");
    for (int side : new int[] {-1, 1})
      for (int segment = 0; segment < 16; segment++) {
        double a = segment * Math.PI / 8, b = (segment + 1) * Math.PI / 8;
        P center = new P(8, 8, 8 + side * .33),
            first = new P(8 + 4.12 * Math.cos(a), 8 + 4.12 * Math.sin(a), center.z()),
            second = new P(8 + 4.12 * Math.cos(b), 8 + 4.12 * Math.sin(b), center.z());
        mesh.quad(
            "native",
            new P[] {center, first, second, second},
            new P(0, 0, side),
            new double[][] {dialUv(center), dialUv(first), dialUv(second), dialUv(second)});
      }
    for (int x : new int[] {-1, 1})
      for (int y : new int[] {-1, 1})
        mesh.beam(
            "pin", new P(8 + x * 3.4, 8 + y * 3.4, 8), new P(8 + x * 4.7, 8 + y * 4.7, 8), .28, 4);
  }

  private static double[] dialUv(P point) {
    return new double[] {
      (8 + (point.x() - 8) * 3 / 4.12) / 16, (6.5 - (point.y() - 8) * 2.5 / 4.12) / 16
    };
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

  private static void loft(
      EquipmentObjMesh mesh, double[][] profile, List<String> materials, boolean inside) {
    double low = profile[0][2], high = profile[profile.length - 1][2];
    for (int row = 1; row < profile.length; row++)
      for (int sector = 0; sector < 8; sector++) {
        double a = sector * Math.PI / 4, b = (sector + 1) * Math.PI / 4;
        double[] first = profile[row - 1], second = profile[row];
        P[] points = {
          new P(8 + first[0] * Math.cos(a), first[2], 8 + first[1] * Math.sin(a)),
          new P(8 + first[0] * Math.cos(b), first[2], 8 + first[1] * Math.sin(b)),
          new P(8 + second[0] * Math.cos(b), second[2], 8 + second[1] * Math.sin(b)),
          new P(8 + second[0] * Math.cos(a), second[2], 8 + second[1] * Math.sin(a))
        };
        patch(
            mesh,
            materials.get(row - 1),
            points,
            new P(Math.cos((a + b) / 2), 0, Math.sin((a + b) / 2)).scale(inside ? -1 : 1),
            new double[][] {
              {sector / 8.0, (high - first[2]) / (high - low)},
              {(sector + 1) / 8.0, (high - first[2]) / (high - low)},
              {(sector + 1) / 8.0, (high - second[2]) / (high - low)},
              {sector / 8.0, (high - second[2]) / (high - low)}
            });
      }
  }

  private static void ellipseCap(
      EquipmentObjMesh mesh, String material, double[] profile, int side) {
    for (int sector = 0; sector < 8; sector++) {
      double a = sector * Math.PI / 4, b = (sector + 1) * Math.PI / 4;
      mesh.quad(
          material,
          new P[] {
            new P(8, profile[2], 8),
            new P(8 + profile[0] * Math.cos(a), profile[2], 8 + profile[1] * Math.sin(a)),
            new P(8 + profile[0] * Math.cos(b), profile[2], 8 + profile[1] * Math.sin(b)),
            new P(8 + profile[0] * Math.cos(b), profile[2], 8 + profile[1] * Math.sin(b))
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
    P top = new P(8, 15, 8), bottom = new P(8, 1.8, 8);
    for (int sector = 0; sector < 6; sector++) {
      double a = sector * Math.PI / 3, b = (sector + 1) * Math.PI / 3;
      P first = new P(8 + 4.1 * Math.cos(a), 9.8, 8 + 3 * Math.sin(a)),
          second = new P(8 + 4.1 * Math.cos(b), 9.8, 8 + 3 * Math.sin(b));
      P outward = new P(Math.cos((a + b) / 2), 0, Math.sin((a + b) / 2));
      triangle(mesh, "edge", top, first, second, outward);
      triangle(mesh, "body", bottom, second, first, outward);
    }
  }

  private static void leaves(EquipmentObjMesh mesh) {
    for (int side : new int[] {-1, 1})
      for (int row : new int[] {-1, 1})
        for (int face : new int[] {-1, 1})
          triangle(
              mesh,
              "leaf",
              new P(8 + side * 4.2, 8 + row * 2, 8.5),
              new P(8 + side * 5.7, 8 + row * 3.3, 8.5),
              new P(8 + side * 4.5, 8 + row * 4.1, 8.5),
              new P(0, 0, face));
  }

  private static void handle(
      EquipmentObjMesh mesh, String material, List<P> points, double radius) {
    for (int index = 1; index < points.size(); index++)
      mesh.beam(material, points.get(index - 1), points.get(index), radius, 4);
  }

  private static void shell(
      EquipmentObjMesh mesh,
      String material,
      double x,
      double z,
      double radius,
      double bottom,
      double top,
      double thickness) {
    wall(mesh, material, x, z, radius, radius, bottom, top, false);
    wall(
        mesh,
        material,
        x,
        z,
        radius - thickness,
        radius - thickness,
        bottom + thickness,
        top,
        true);
    mesh.group("rim");
    annulus(mesh, material, x, z, radius, radius - thickness, top, 1);
    mesh.group("base");
    cap(mesh, material, x, z, radius, bottom, -1);
    cap(mesh, material, x, z, radius - thickness, bottom + thickness, 1);
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

  private static void annulus(
      EquipmentObjMesh mesh,
      String material,
      double x,
      double z,
      double outer,
      double inner,
      double y,
      int side) {
    for (int index = 0; index < 8; index++) {
      double a = index * Math.PI / 4, b = (index + 1) * Math.PI / 4;
      mesh.quad(
          material,
          new P[] {
            new P(x + outer * Math.cos(a), y, z + outer * Math.sin(a)),
            new P(x + outer * Math.cos(b), y, z + outer * Math.sin(b)),
            new P(x + inner * Math.cos(b), y, z + inner * Math.sin(b)),
            new P(x + inner * Math.cos(a), y, z + inner * Math.sin(a))
          },
          new P(0, side, 0),
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
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
