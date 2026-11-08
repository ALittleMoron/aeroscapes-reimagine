package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Physical workshop supplies; native predicates and non-hand models remain provider-owned. */
final class CreateEquipmentModels {
  private static final List<String> MATERIALS =
      List.of(
          "body", "edge", "paper", "metal", "dark", "feather", "shaft", "rope", "label", "backing",
          "grit", "honey");
  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/create_equipment/";

  private CreateEquipmentModels() {}

  static boolean supports(String id) {
    return id.startsWith("create:clipboard")
        || List.of(
                "create:sand_paper",
                "create:red_sand_paper",
                "create:schematic_and_quill",
                "create:super_glue",
                "simulated:honey_glue",
                "simulated:rope_coupling",
                "create_aeronautics_throwable_rope_connector:throwable_rope_connector")
            .contains(id);
  }

  static JsonObject model(String id, JsonObject original) {
    JsonObject model = original.deepCopy();
    model.addProperty("loader", "neoforge:obj");
    model.addProperty("model", DIRECTORY + name(id) + ".obj");
    model.addProperty("automatic_culling", false);
    model.addProperty("emissive_ambient", false);
    model.addProperty("flip_v", false);
    model.addProperty("ambientocclusion", false);
    model.addProperty("render_type", "minecraft:cutout");
    model.getAsJsonObject("textures").addProperty("particle", "#layer0");
    JsonObject display =
        JsonParser.parseString(
                """
        {
          "firstperson_righthand":{"rotation":[-25,0,0],"translation":[0,3,1],"scale":[0.55,0.55,0.55]},
          "firstperson_lefthand":{"rotation":[-25,0,0],"translation":[0,3,1],"scale":[0.55,0.55,0.55]},
          "thirdperson_righthand":{"rotation":[0,0,0],"translation":[0,3,1],"scale":[0.55,0.55,0.55]},
          "thirdperson_lefthand":{"rotation":[0,0,0],"translation":[0,3,1],"scale":[0.55,0.55,0.55]}
        }
        """)
            .getAsJsonObject();
    if (id.endsWith("glue")) {
      for (var hand : display.entrySet()) {
        JsonObject transform = hand.getValue().getAsJsonObject();
        transform.add("rotation", JsonParser.parseString("[-25,0,0]"));
        transform.add("translation", JsonParser.parseString("[0,3.5,0]"));
        transform.add("scale", JsonParser.parseString("[0.65,0.65,0.65]"));
      }
    }
    if (id.endsWith("sand_paper") || id.equals("simulated:rope_coupling"))
      for (var hand : display.entrySet())
        hand.getValue().getAsJsonObject().add("translation", JsonParser.parseString("[0,2,1]"));
    if (id.endsWith("throwable_rope_connector")) {
      JsonObject rope = model.deepCopy();
      rope.remove("display");
      JsonObject connector = new JsonObject();
      connector.addProperty("parent", "simulated:block/rope_connector/item");
      JsonObject children = new JsonObject();
      children.add("connector", connector);
      children.add("rope", rope);
      model.remove("model");
      for (String flag : List.of("automatic_culling", "emissive_ambient", "flip_v", "render_type"))
        model.remove(flag);
      model.addProperty("loader", "neoforge:composite");
      model.add("children", children);
      model.add("item_render_order", JsonParser.parseString("[\"connector\",\"rope\"]"));
      display = ToolModels.ropeConnectorDisplay();
    }
    model.add("display", display);
    return model;
  }

  static void addResources(Path assets, Map<Path, byte[]> output, Map<String, JsonObject> models)
      throws IOException {
    StringBuilder materials = new StringBuilder();
    for (String id : models.keySet()) {
      if (!supports(id)) continue;
      EquipmentObjMesh mesh = new EquipmentObjMesh();
      for (int index = 0; index < MATERIALS.size(); index++) {
        String material = MATERIALS.get(index), key = name(id) + "_" + material;
        mesh.material(material, key);
        int x = index % 4 * 16, y = index / 4 * 16;
        mesh.region(key, (x + .25) / 64, (y + .25) / 64, (x + 15.75) / 64, (y + 15.75) / 64);
        materials
            .append("newmtl ")
            .append(key)
            .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd aeroscapes_reimagine:item/create_equipment/")
            .append(name(id))
            .append("\n\n");
      }
      if (id.startsWith("create:clipboard")) clipboard(mesh, id);
      else if (id.endsWith("sand_paper")) sandpaper(mesh);
      else if (id.endsWith("schematic_and_quill")) schematic(mesh);
      else if (id.endsWith("glue")) glue(mesh, id);
      else if (id.endsWith("rope_coupling")) coupling(mesh);
      else throwable(mesh);
      output.put(
          assets.resolve("aeroscapes_reimagine/models/item/create_equipment/" + name(id) + ".obj"),
          mesh.obj().getBytes(StandardCharsets.UTF_8));
      output.put(
          assets.resolve(
              "aeroscapes_reimagine/textures/item/create_equipment/" + name(id) + ".png"),
          EquipmentModels.png(texture(id)));
    }
    output.put(
        assets.resolve("aeroscapes_reimagine/models/item/create_equipment/equipment.mtl"),
        (materials.toString().stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8));
  }

  private static String name(String id) {
    return id.replace(':', '_');
  }

  private static BufferedImage texture(String id) throws IOException {
    String source =
        NativeEquipmentModels.inherited(id).getAsJsonObject("textures").get("layer0").getAsString();
    BufferedImage nativeSprite = NativeEquipmentModels.image(source);
    BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    for (int index = 0; index < MATERIALS.size(); index++) {
      String material = MATERIALS.get(index);
      int color = nearest(nativeSprite, color(id, material));
      int accent =
          nearest(
              nativeSprite,
              material.equals("paper")
                  ? id.endsWith("schematic_and_quill") ? 0xBDC7F1 : 0xA17D38
                  : shade(color(id, material)));
      for (int y = 0; y < 16; y++)
        for (int x = 0; x < 16; x++) {
          boolean mark =
              switch (material) {
                case "grit" -> (x * 37 + y * 61 + x * y * 13) % 29 < 5;
                case "body" -> id.contains("clipboard") && x % 7 == 2 && y > 3 && y < 12;
                case "rope", "backing" -> x % 7 == 2 && y > 3 && y < 12;
                default -> false;
              };
          if (material.equals("paper")) {
            if (id.endsWith("schematic_and_quill")) mark = x % 5 == 1 || y % 5 == 1;
            else if (!id.endsWith("clipboard_0"))
              mark = y >= 4 && y <= 12 && y % 3 == 1 && x >= 3 && x < 10 + y % 4;
          }
          image.setRGB(index % 4 * 16 + x, index / 4 * 16 + y, mark ? accent : color);
        }
    }
    return image;
  }

  private static int color(String id, String material) {
    return switch (material) {
      case "body" ->
          id.contains("clipboard")
              ? 0x795324
              : id.endsWith("schematic_and_quill")
                  ? 0x6E81C0
                  : id.endsWith("super_glue")
                      ? 0x4D8524
                      : id.endsWith("honey_glue") ? 0xC56E30 : 0x34383A;
      case "edge" -> id.endsWith("super_glue") ? 0x25492D : 0x4B3B29;
      case "paper" -> id.endsWith("schematic_and_quill") ? 0x94A9E7 : 0xECCD93;
      case "metal" -> 0xA0A6A6;
      case "dark" -> 0x303638;
      case "feather", "label" -> id.endsWith("super_glue") ? 0xA4D947 : 0xE6E6DF;
      case "shaft" -> 0x3F392D;
      case "rope" -> id.endsWith("rope_coupling") ? 0x8A5B28 : 0xE2A744;
      case "backing" -> id.endsWith("red_sand_paper") ? 0xA1623E : 0xBAAC83;
      case "grit" -> id.endsWith("red_sand_paper") ? 0xD88D55 : 0xE4D8AB;
      case "honey" -> 0xF5C34B;
      default -> throw new IllegalArgumentException(material);
    };
  }

  private static int shade(int color) {
    return ((color >> 16 & 255) * 7 / 8 << 16)
        | ((color >> 8 & 255) * 7 / 8 << 8)
        | (color & 255) * 7 / 8;
  }

  private static int nearest(BufferedImage source, int target) {
    int result = 0, distance = Integer.MAX_VALUE;
    for (int y = 0; y < source.getHeight(); y++)
      for (int x = 0; x < source.getWidth(); x++) {
        int color = source.getRGB(x, y);
        if ((color >>> 24) != 255) continue;
        int red = (color >> 16 & 255) - (target >> 16 & 255),
            green = (color >> 8 & 255) - (target >> 8 & 255),
            blue = (color & 255) - (target & 255);
        int current = red * red + green * green + blue * blue;
        if (current < distance) {
          distance = current;
          result = color;
        }
      }
    if (distance == Integer.MAX_VALUE) throw new IllegalArgumentException("Empty native sprite");
    return result;
  }

  private static void clipboard(EquipmentObjMesh mesh, String id) {
    mesh.group("wood_board");
    box(mesh, "body", 3, 1, 7.6, 13, 14.5, 8.25);
    mesh.group("page");
    box(mesh, "paper", 3.6, 1.7, 8.25, 12.4, 13.3, 8.4);
    mesh.group("spring_clip");
    box(mesh, "dark", 6.3, 13, 8.4, 9.7, 14.8, 8.65);
    box(mesh, "metal", 6, 12.9, 8.65, 10, 13.5, 8.95);
    for (double x : new double[] {6, 9.4}) box(mesh, "metal", x, 13.5, 8.65, x + .6, 15, 8.95);
    box(mesh, "metal", 6.6, 14.4, 8.65, 9.4, 15, 8.95);
    if (id.endsWith("clipboard_2")) quill(mesh);
  }

  private static void schematic(EquipmentObjMesh mesh) {
    mesh.group("blueprint");
    sheet(mesh, "paper", "body", 3, 2.3, 12.5, 12.5, 8, 8.15);
    sheet(mesh, "paper", "body", 3, 12.5, 12.5, 14, 8.15, 8.8);
    sheet(mesh, "paper", "body", 3, 1, 12.5, 2.3, 8.55, 8);
    schematicQuill(mesh);
  }

  private static void sandpaper(EquipmentObjMesh mesh) {
    mesh.group("abrasive_sheet");
    sheet(mesh, "grit", "backing", 3.5, 3, 12.5, 10.5, 8, 8);
    sheet(mesh, "grit", "backing", 3.5, 10.5, 12.5, 12, 8, 8.5);
    sheet(mesh, "grit", "backing", 3.5, 12, 12.5, 13, 8.5, 9.2);
  }

  private static void sheet(
      EquipmentObjMesh mesh,
      String front,
      String back,
      double x,
      double y,
      double right,
      double top,
      double bottomZ,
      double topZ) {
    P a = new P(x, y, bottomZ),
        b = new P(right, y, bottomZ),
        c = new P(right, top, topZ),
        d = new P(x, top, topZ);
    double[][] uv = {
      {0, (14 - y) / 13}, {1, (14 - y) / 13}, {1, (14 - top) / 13}, {0, (14 - top) / 13}
    };
    mesh.quad(front, new P[] {a, b, c, d}, new P(0, 0, 1), uv);
    P shift = new P(0, 0, -.12);
    mesh.quad(
        back,
        new P[] {a.add(shift), b.add(shift), c.add(shift), d.add(shift)},
        new P(0, 0, -1),
        uv);
    for (P[] edge : List.of(new P[] {a, b}, new P[] {b, c}, new P[] {c, d}, new P[] {d, a}))
      mesh.quad(
          back,
          new P[] {edge[0], edge[1], edge[1].add(shift), edge[0].add(shift)},
          new P(edge[1].y() - edge[0].y(), edge[0].x() - edge[1].x(), 0),
          uv);
  }

  private static void quill(EquipmentObjMesh mesh) {
    mesh.group("quill");
    mesh.beam("shaft", new P(9.6, 2, 8.8), new P(6.2, 14, 9.15), .15, 4);
    mesh.plate(
        "feather",
        "metal",
        List.of(
            new P(7.6, 7.3, 9.1),
            new P(5.2, 10.8, 9.1),
            new P(4.6, 13.1, 9.1),
            new P(5.3, 15, 9.1),
            new P(6.7, 14.5, 9.1),
            new P(8, 12.5, 9.1),
            new P(8.1, 10, 9.1)),
        .22);
  }

  private static void schematicQuill(EquipmentObjMesh mesh) {
    mesh.group("quill");
    mesh.beam("shaft", featherPoint(0, 0), featherPoint(0, 4.9), .13, 4);
    mesh.plate(
        "feather",
        "metal",
        List.of(
            featherPoint(0, 4.8),
            featherPoint(-.8, 5.8),
            featherPoint(-.45, 6.2),
            featherPoint(-1.3, 6.9),
            featherPoint(-.65, 7.4),
            featherPoint(-1.55, 8.3),
            featherPoint(-.85, 8.8),
            featherPoint(-1.4, 9.7),
            featherPoint(-1, 11.2),
            featherPoint(0, 13.3)),
        .14);
    mesh.plate(
        "feather",
        "metal",
        List.of(
            featherPoint(0, 5.2),
            featherPoint(.65, 6.1),
            featherPoint(.35, 6.5),
            featherPoint(1.15, 7.1),
            featherPoint(.6, 7.6),
            featherPoint(1.55, 8.5),
            featherPoint(.85, 9),
            featherPoint(1.6, 9.9),
            featherPoint(1.15, 11.4),
            featherPoint(.55, 12.5),
            featherPoint(0, 13.3)),
        .14);
    mesh.beam(
        "metal",
        featherPoint(0, 4.6).add(new P(0, 0, .08)),
        featherPoint(0, 13.25).add(new P(0, 0, .08)),
        .095,
        4);
  }

  private static P featherPoint(double across, double along) {
    return new P(
        9.6 - .27 * along + across,
        2 + along + .27 * across,
        9.15 + .025 * along - .1 * Math.abs(across));
  }

  private static void glue(EquipmentObjMesh mesh, String id) {
    boolean honey = id.equals("simulated:honey_glue");
    mesh.group("crimped_tail");
    box(mesh, "edge", 5.35, 1.6, 7.55, 10.65, 2.3, 8.45);
    mesh.group("squeeze_tube");
    double[][] rings = {
      {2.3, 2.4, .38},
      {3.4, 2.65, 1.0},
      {4.3, 2.5, 1.25},
      {8.1, 2.25, 1.2},
      {9.6, 1.15, .8},
      {10.2, .85, .7},
      {11, .85, .7},
      {13, .42, .42}
    };
    for (int index = 1; index < rings.length; index++) {
      String material =
          index == 3
              ? "label"
              : honey && index == 7 ? "honey" : index >= 6 ? "metal" : index == 5 ? "edge" : "body";
      for (int side = 0; side < 8; side++) {
        double a = (side + .5) * Math.PI / 4, b = (side + 1.5) * Math.PI / 4;
        P[] points = {
          tubePoint(rings[index - 1], a),
          tubePoint(rings[index - 1], b),
          tubePoint(rings[index], b),
          tubePoint(rings[index], a)
        };
        P outward = new P(Math.cos((a + b) / 2), 0, Math.sin((a + b) / 2));
        mesh.quad(
            material,
            new P[] {points[0], points[1], points[2], points[2]},
            outward,
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {1, 0}});
        mesh.quad(
            material,
            new P[] {points[0], points[2], points[3], points[3]},
            outward,
            new double[][] {{0, 1}, {1, 0}, {0, 0}, {0, 0}});
      }
    }
    for (int end : new int[] {0, rings.length - 1})
      for (int side = 0; side < 8; side++) {
        double a = (side + .5) * Math.PI / 4, b = (side + 1.5) * Math.PI / 4;
        P center = new P(8, rings[end][0], 8),
            first = tubePoint(rings[end], a),
            second = tubePoint(rings[end], b);
        mesh.quad(
            end == 0 ? "body" : honey ? "honey" : "dark",
            new P[] {center, first, second, second},
            new P(0, end == 0 ? -1 : 1, 0),
            new double[][] {{.5, .5}, {0, 0}, {1, 0}, {1, 0}});
      }
    if (honey) {
      mesh.group("tail_loop");
      box(mesh, "metal", 6.8, 0, 7.65, 7.4, 1.6, 8.35);
      box(mesh, "metal", 8.6, 0, 7.65, 9.2, 1.6, 8.35);
      box(mesh, "metal", 7.4, 0, 7.65, 8.6, .6, 8.35);
    }
  }

  private static P tubePoint(double[] ring, double angle) {
    return new P(8 + ring[1] * Math.cos(angle), ring[0], 8 + ring[2] * Math.sin(angle));
  }

  private static void coupling(EquipmentObjMesh mesh) {
    mesh.group("continuous_rope");
    List<P> path = new ArrayList<>();
    for (int step = 0; step <= 72; step++) {
      double t = step * Math.PI / 12;
      path.add(
          new P(
              8 + 2.25 * Math.sin(2 * t),
              8 + 4.2 * Math.cos(t),
              7 + step / 72.0 * 1.8 + .5 * Math.sin(t)));
    }
    path.add(new P(9, 10.8, 9.2));
    path.add(new P(10.8, 6.4, 9));
    path.add(new P(11.3, 3, 8.8));
    mesh.tube("rope", path, .43, 6, 10);
    mesh.group("binding");
    mesh.tube(
        "rope",
        List.of(
            new P(6.5, 7.4, 7),
            new P(6.2, 7.4, 8.8),
            new P(7.7, 7.4, 9.5),
            new P(9.5, 7.4, 8.8),
            new P(9.7, 7.4, 7),
            new P(8, 7.4, 6.5),
            new P(6.5, 7.4, 7)),
        .35,
        6,
        10);
  }

  private static void throwable(EquipmentObjMesh mesh) {
    mesh.group("tied_rope");
    double[][] section = {
      {2.3, 5.7},
      {5.7, 5.7},
      {6.3, 6.3},
      {6.3, 9.7},
      {5.7, 10.3},
      {2.3, 10.3},
      {1.7, 9.7},
      {1.7, 6.3}
    };
    List<P> path = new ArrayList<>();
    for (int step = 0; step <= 16; step++) {
      double[] point = section[step % section.length];
      path.add(new P(7.15 + step * .1, point[0], point[1]));
    }
    path.add(new P(9, 1.5, 4.8));
    path.add(new P(9.4, .7, 2.5));
    path.add(new P(11.3, -.3, 2.2));
    path.add(new P(12.3, -.5, 2.5));
    mesh.tube("rope", path, .35, 6, 10);
  }

  private static void box(
      EquipmentObjMesh mesh,
      String material,
      double x,
      double y,
      double z,
      double right,
      double top,
      double back) {
    P a = new P(x, y, z),
        b = new P(right, y, z),
        c = new P(right, top, z),
        d = new P(x, top, z),
        e = new P(x, y, back),
        f = new P(right, y, back),
        g = new P(right, top, back),
        h = new P(x, top, back);
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
    for (int face = 0; face < faces.length; face++)
      mesh.quad(
          material, faces[face], normals[face], new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
  }
}
