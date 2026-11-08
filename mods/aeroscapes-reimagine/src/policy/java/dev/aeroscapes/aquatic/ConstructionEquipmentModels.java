package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** LittleTiles corrections; native preview layers stay in their original renderer. */
final class ConstructionEquipmentModels {
  private static final String DIRECTORY =
      "aeroscapes_reimagine:models/item/construction_equipment/";
  static final List<String> ITEMS = List.of("bag", "measure_tape", "saw");

  private ConstructionEquipmentModels() {}

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Path directory = assets.resolve("aeroscapes_reimagine/models/item/construction_equipment");
    StringBuilder materials = new StringBuilder();
    for (String item : ITEMS) {
      EquipmentObjMesh mesh = new EquipmentObjMesh();
      for (var entry :
          regions(item).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        String key = item + "_" + entry.getKey();
        mesh.material(entry.getKey(), key);
        double[] uv = entry.getValue();
        mesh.region(
            key, (uv[0] + .15) / 16, (uv[1] + .15) / 16, (uv[2] - .15) / 16, (uv[3] - .15) / 16);
        materials
            .append("newmtl ")
            .append(key)
            .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd littletiles:item/")
            .append(item)
            .append("\n\n");
      }
      if (item.equals("bag")) bag(mesh);
      else if (item.equals("measure_tape")) tape(mesh);
      else saw(mesh);
      output.put(directory.resolve(item + ".obj"), mesh.obj().getBytes(StandardCharsets.UTF_8));
      JsonObject model = new JsonObject();
      model.addProperty("loader", "neoforge:obj");
      model.addProperty("model", DIRECTORY + item + ".obj");
      model.addProperty("automatic_culling", false);
      model.addProperty("flip_v", false);
      model.addProperty("ambientocclusion", false);
      model.addProperty("render_type", "minecraft:cutout");
      JsonObject textures = new JsonObject();
      textures.addProperty("layer0", "littletiles:item/" + item);
      textures.addProperty("particle", "#layer0");
      model.add("textures", textures);
      model.add("display", display(item));
      EquipmentModels.text(
          output,
          assets.resolve("littletiles/models/item/equipment/held/" + item + ".json"),
          model.toString());
    }
    output.put(
        directory.resolve("equipment.mtl"),
        (materials.toString().stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8));
    ConstructionIconModels.addResources(assets, output);
    for (String item : List.of("paint_brush", "wrench"))
      EquipmentModels.text(
          output,
          assets.resolve("littletiles/models/item/equipment/held/" + item + ".json"),
          handTool(item).toString());
  }

  private static JsonObject display(String item) {
    JsonObject display =
        JsonParser.parseString(
                """
        {
          "thirdperson_righthand":{"rotation":[0,90,0],"scale":[0.75,0.75,0.75]},
          "thirdperson_lefthand":{"rotation":[0,90,0],"scale":[0.75,0.75,0.75]},
          "firstperson_righthand":{"rotation":[0,90,0],"scale":[0.65,0.65,0.65]},
          "firstperson_lefthand":{"rotation":[0,90,0],"scale":[0.65,0.65,0.65]}
        }
        """)
            .getAsJsonObject();
    if (item.equals("saw"))
      for (String context : List.of("firstperson_lefthand", "thirdperson_lefthand"))
        display.getAsJsonObject(context).add("rotation", vector(0, -90, 0));
    if (item.equals("measure_tape"))
      for (var entry : display.entrySet()) {
        entry.getValue().getAsJsonObject().add("scale", JsonParser.parseString("[0.45,0.45,0.45]"));
        entry
            .getValue()
            .getAsJsonObject()
            .add("rotation", vector(0, entry.getKey().endsWith("lefthand") ? -90 : 90, 0));
      }
    Vector3f grip =
        item.equals("bag")
            ? new Vector3f(8, 12, 8)
            : item.equals("saw") ? new Vector3f(3.6f, 4, 8) : new Vector3f(8, 1.5f, 8);
    HeldItemModels.anchorHeld(
        display, grip, new Vector3f(0, -2, item.equals("measure_tape") ? .4f : 2));
    if (item.equals("bag")) {
      JsonObject firstPerson = new JsonObject();
      for (var entry : display.entrySet())
        if (entry.getKey().startsWith("firstperson_")) {
          entry.getValue().getAsJsonObject().add("rotation", vector(-30, 0, 0));
          firstPerson.add(entry.getKey(), entry.getValue());
        }
      HeldItemModels.anchorHeld(firstPerson, new Vector3f(8, 3, 8));
    }
    return display;
  }

  private static JsonObject handTool(String item) {
    JsonObject model = new JsonObject();
    model.addProperty("parent", "minecraft:block/block");
    model.addProperty("ambientocclusion", false);
    JsonObject textures = new JsonObject();
    textures.addProperty("layer0", "littletiles:item/" + item);
    textures.addProperty("particle", "#layer0");
    model.add("textures", textures);
    JsonObject display =
        JsonParser.parseString(
                """
        {
          "thirdperson_righthand":{"rotation":[0,90,0],"translation":[0,2,0],"scale":[0.75,0.75,0.75]},
          "thirdperson_lefthand":{"rotation":[0,-90,0],"translation":[0,2,0],"scale":[0.75,0.75,0.75]},
          "firstperson_righthand":{"rotation":[0,-70,10],"translation":[1.3,2,0],"scale":[0.65,0.65,0.65]},
          "firstperson_lefthand":{"rotation":[0,70,-10],"translation":[1.3,2,0],"scale":[0.65,0.65,0.65]}
        }
        """)
            .getAsJsonObject();
    model.add("display", display);
    var parts = new com.google.gson.JsonArray();
    model.add("elements", parts);
    if (item.equals("paint_brush")) {
      textures.addProperty("layer1", "littletiles:item/paint_brush_overlay");
      box(parts, "grip", new double[] {7, 1.5, 7, 9, 8.5, 9}, new double[] {5, 10, 7, 12}, false);
      box(
          parts,
          "ferrule",
          new double[] {6.1, 8.5, 6.9, 9.9, 10.5, 9.1},
          new double[] {9, 9, 11, 11},
          false);
      JsonObject bristles =
          box(
              parts,
              "bristles",
              new double[] {5, 10.5, 7.3, 11, 15, 8.7},
              new double[] {9, 2, 12, 6},
              true);
      for (var face : bristles.getAsJsonObject("faces").entrySet())
        face.getValue().getAsJsonObject().addProperty("texture", "#layer1");
      JsonObject firstPerson = new JsonObject();
      for (var entry : display.entrySet())
        if (entry.getKey().startsWith("firstperson_")) {
          var transform = entry.getValue().getAsJsonObject();
          transform.add("scale", JsonParser.parseString("[0.75,0.75,0.75]"));
          transform.add("rotation", JsonParser.parseString("[0,90,0]"));
          firstPerson.add(entry.getKey(), transform);
        }
      HeldItemModels.anchorHeld(firstPerson, new Vector3f(8, 3.5f, 8));
    } else {
      box(parts, "grip", new double[] {7, 1, 7, 9, 9.5, 9}, new double[] {5, 10, 7, 12}, false);
      metalBox(parts, "neck", new double[] {7, 9.5, 7.3, 9, 12, 8.7});
      metalBox(parts, "head_base", new double[] {5, 11.5, 7, 11, 13, 9});
      metalBox(parts, "left_jaw", new double[] {5, 13, 7, 6.7, 15.3, 9});
      metalBox(parts, "right_jaw", new double[] {9.3, 13, 7, 11, 15.3, 9});
    }
    return model;
  }

  private static JsonObject box(
      com.google.gson.JsonArray parts,
      String name,
      double[] bounds,
      double[] region,
      boolean tinted) {
    JsonObject part = new JsonObject();
    part.addProperty("name", name);
    part.add("from", vector(bounds[0], bounds[1], bounds[2]));
    part.add("to", vector(bounds[3], bounds[4], bounds[5]));
    JsonObject faces = new JsonObject();
    for (String side : List.of("north", "south", "east", "west", "up", "down")) {
      JsonObject face = new JsonObject();
      face.add("uv", vector(region[0] + .15, region[1] + .15, region[2] - .15, region[3] - .15));
      face.addProperty("texture", "#layer0");
      if (tinted) face.addProperty("tintindex", 1);
      faces.add(side, face);
    }
    part.add("faces", faces);
    parts.add(part);
    return part;
  }

  private static void metalBox(com.google.gson.JsonArray parts, String name, double[] bounds) {
    JsonObject part = box(parts, name, bounds, new double[] {10, 5, 11, 6}, false);
    for (var entry : part.getAsJsonObject("faces").entrySet()) {
      double[] region =
          switch (entry.getKey()) {
            case "north", "south" -> new double[] {10, 5, 11, 6};
            case "up" -> new double[] {9, 5, 10, 6};
            default -> new double[] {10, 4, 11, 5};
          };
      entry
          .getValue()
          .getAsJsonObject()
          .add("uv", vector(region[0] + .15, region[1] + .15, region[2] - .15, region[3] - .15));
    }
  }

  private static com.google.gson.JsonArray vector(double... values) {
    var vector = new com.google.gson.JsonArray();
    for (double value : values) vector.add(value);
    return vector;
  }

  static Map<String, double[]> regions(String item) {
    return switch (item) {
      case "bag" ->
          Map.of(
              "body",
              new double[] {8, 10, 11, 13},
              "dark",
              new double[] {11, 12, 14, 14},
              "cord",
              new double[] {9, 3, 10, 4},
              "badge",
              new double[] {5, 10, 6, 11});
      case "measure_tape" ->
          Map.of(
              "body",
              new double[] {5, 7, 9, 11},
              "rim",
              new double[] {2, 7, 3, 10},
              "dark",
              new double[] {1, 7, 2, 10},
              "button",
              new double[] {10, 4, 11, 5},
              "tape",
              new double[] {13, 9, 14, 10});
      case "saw" ->
          Map.of("body", new double[] {3, 8, 5, 10}, "steel", new double[] {12, 5, 14, 7});
      default -> throw new IllegalArgumentException(item);
    };
  }

  private static void bag(EquipmentObjMesh mesh) {
    double[][] sections = {{2, 2.8}, {4, 4.2}, {8, 4}, {10.5, 2.4}, {12, 1.8}, {14, 2.6}};
    P[][] rings = new P[sections.length][8];
    for (int ring = 0; ring < rings.length; ring++)
      for (int point = 0; point < 8; point++) {
        double angle = Math.PI / 8 + point * Math.PI / 4;
        double fold = ring == 3 || ring == 5 ? (point % 2 == 0 ? .85 : 1.1) : 1;
        rings[ring][point] =
            new P(
                8 + Math.cos(angle) * sections[ring][1] * fold,
                sections[ring][0],
                8 + Math.sin(angle) * sections[ring][1] * fold);
      }
    mesh.group("pouch_folds");
    for (int ring = 0; ring < rings.length - 1; ring++)
      for (int point = 0; point < 8; point++) {
        int next = (point + 1) % 8;
        P outward = rings[ring][point].add(rings[ring][next]).scale(.5).add(new P(-8, 0, -8));
        P a = rings[ring][point],
            b = rings[ring][next],
            c = rings[ring + 1][next],
            d = rings[ring + 1][point];
        String material = point % 3 == 0 ? "dark" : "body";
        mesh.quad(
            material,
            new P[] {a, b, c, c},
            outward,
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {1, 0}});
        mesh.quad(
            material,
            new P[] {a, c, d, d},
            outward,
            new double[][] {{0, 1}, {1, 0}, {0, 0}, {0, 0}});
      }
    for (int point = 0; point < 8; point++) {
      int next = (point + 1) % 8;
      mesh.quad(
          "dark",
          new P[] {new P(8, 2, 8), rings[0][point], rings[0][next], rings[0][next]},
          new P(0, -1, 0),
          new double[][] {{.5, .5}, {0, 0}, {1, 0}, {1, 0}});
      mesh.quad(
          "dark",
          new P[] {rings[5][point], rings[5][next], new P(8, 12.8, 8), new P(8, 12.8, 8)},
          new P(0, 1, 0),
          new double[][] {{0, 0}, {1, 0}, {.5, .5}, {.5, .5}});
    }
    mesh.group("drawstring");
    List<P> cord = new java.util.ArrayList<>();
    for (int point = 0; point <= 8; point++) {
      double angle = point * Math.PI / 4;
      cord.add(new P(8 + Math.cos(angle) * 1.95, 12, 8 + Math.sin(angle) * 1.95));
    }
    mesh.tube("cord", cord, .22, 4, 3);
    mesh.tube(
        "cord",
        List.of(new P(9.95, 12, 8), new P(11, 10.5, 8.4), new P(10.7, 9.5, 8.7)),
        .22,
        4,
        3);
    mesh.group("native_fastener");
    mesh.box("badge", 7.3, 6, 11.7, 8.4, 7.1, 12.05);
  }

  private static void tape(EquipmentObjMesh mesh) {
    mesh.group("case");
    mesh.box("body", 4.5, 4, 6, 11.5, 11, 10);
    mesh.group("perimeter_seals");
    for (double z : new double[] {5.8, 10}) {
      mesh.box("dark", 4.5, 4, z, 4.75, 11, z + .2);
      mesh.box("dark", 11.25, 4, z, 11.5, 11, z + .2);
      mesh.box("dark", 4.75, 4, z, 11.25, 4.25, z + .2);
      mesh.box("dark", 4.75, 10.75, z, 11.25, 11, z + .2);
    }
    mesh.box("rim", 4.75, 4.25, 5.75, 11.25, 10.75, 6);
    mesh.box("rim", 4.75, 4.25, 10, 11.25, 10.75, 10.25);
    mesh.box("body", 5, 4.5, 5.7, 11, 10.5, 5.75);
    mesh.box("body", 5, 4.5, 10.25, 11, 10.5, 10.3);
    mesh.box("button", 9, 11, 7, 10.2, 12, 9);
    mesh.group("extended_tape");
    mesh.box("tape", 11.4, 5.15, 7.3, 16, 5.3, 8.7);
    mesh.box("dark", 15.5, 4.3, 7.25, 16.2, 5.7, 8.75);
  }

  private static void saw(EquipmentObjMesh mesh) {
    mesh.group("closed_grip");
    mesh.box("body", 3, 0, 7, 4.2, 8, 9);
    mesh.box("body", 4.2, 6.8, 7, 8, 8, 9);
    mesh.box("body", 4.2, 0, 7, 8, 1.2, 9);
    mesh.box("body", 6.8, 1.2, 7, 8, 6.8, 9);
    mesh.group("frame");
    mesh.box("body", 7.2, 8, 7.3, 8.4, 11.5, 8.7);
    mesh.box("body", 8.4, 10.3, 7.3, 23, 11.5, 8.7);
    mesh.box("body", 21.8, 6.8, 7.3, 23, 10.3, 8.7);
    mesh.group("blade");
    mesh.box("steel", 8, 6.9, 7.7, 22.2, 7.6, 8.3);
    for (int tooth = 0; tooth < 11; tooth++) {
      double x = 8.5 + tooth * 1.2;
      mesh.plate(
          "steel",
          "steel",
          List.of(new P(x, 6.9, 8), new P(x + .7, 6.9, 8), new P(x + .35, 6.4, 8)),
          .6);
    }
  }
}
