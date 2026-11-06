package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import dev.aeroscapes.atmosphere.EquipmentArtwork;
import dev.aeroscapes.atmosphere.EquipmentStyle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The reviewed nets, articulated celestial bow and pointed workshop blades. */
final class ReviewedEquipmentModels {
  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/reviewed_equipment/";

  private ReviewedEquipmentModels() {}

  static boolean supports(String id) {
    return id.equals("naturalist:capture_net")
        || id.startsWith("spawn:casting_net")
        || id.startsWith("tide:starlight_bow")
        || id.equals("quark:trowel")
        || id.equals("chipped:saw");
  }

  static JsonObject model(String id, EquipmentStyle style, JsonObject original) {
    JsonObject result = original.deepCopy();
    result.addProperty("loader", "neoforge:obj");
    result.addProperty("model", DIRECTORY + name(id) + ".obj");
    result.addProperty("automatic_culling", false);
    result.addProperty("emissive_ambient", false);
    result.addProperty("flip_v", false);
    result.addProperty("ambientocclusion", false);
    result.addProperty("render_type", "minecraft:cutout");
    result.getAsJsonObject("textures").addProperty("particle", "#layer0");
    result.add(
        "display",
        id.startsWith("tide:starlight_bow")
            ? bowDisplay()
            : EquipmentSculpture.transforms(id, style, original));
    return result;
  }

  static void addResources(Path assets, Map<Path, byte[]> output, Map<String, JsonObject> models)
      throws IOException {
    StringBuilder materials = new StringBuilder();
    Path directory = assets.resolve("aeroscapes_reimagine/models/item/reviewed_equipment");
    for (String id : models.keySet()) {
      if (!supports(id)) continue;
      EquipmentObjMesh mesh = new EquipmentObjMesh();
      String root =
          id.startsWith("tide:")
              ? "tide:starlight_bow"
              : id.startsWith("spawn:") ? "spawn:casting_net" : id;
      for (String material : EquipmentArtwork.MATERIALS) {
        String key = name(id) + "_" + material;
        int tile = EquipmentArtwork.MATERIALS.indexOf(material);
        mesh.region(
            key, (tile % 2) / 8.0, (tile / 2) / 8.0, (tile % 2 + 1) / 8.0, (tile / 2 + 1) / 8.0);
        materials
            .append("newmtl ")
            .append(key)
            .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd ")
            .append(root.replace(":", ":item/equipment/painted/"))
            .append("\n\n");
      }
      String cloth = name(id) + "_cloth";
      materials
          .append("newmtl ")
          .append(cloth)
          .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd aeroscapes_reimagine:item/reviewed_equipment/")
          .append(id.startsWith("naturalist:") ? "white_net" : "brown_net")
          .append("\n\n");
      if (id.startsWith("naturalist:")) captureNet(mesh, id, cloth);
      else if (id.startsWith("spawn:")) castingNet(mesh, id, cloth);
      else if (id.startsWith("tide:")) bow(mesh, id);
      else if (id.equals("quark:trowel")) trowel(mesh, id);
      else saw(mesh, id);
      output.put(directory.resolve(name(id) + ".obj"), mesh.obj().getBytes(StandardCharsets.UTF_8));
    }
    output.put(
        directory.resolve("equipment.mtl"),
        (materials.toString().stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8));
    for (boolean white : new boolean[] {false, true}) {
      BufferedImage texture = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < 4; y++)
        for (int x = 0; x < 4; x++) {
          int color = white ? (x == 0 ? 0xF7F7F7 : 0xC8D2D4) : (x == 0 ? 0xA37444 : 0x66412E);
          texture.setRGB(x, y, 0xFF000000 | color);
        }
      output.put(
          assets.resolve(
              "aeroscapes_reimagine/textures/item/reviewed_equipment/"
                  + (white ? "white_net" : "brown_net")
                  + ".png"),
          EquipmentModels.png(texture));
    }
  }

  private static String name(String id) {
    return id.replace(':', '_');
  }

  private static String material(String id, String part) {
    return name(id) + "_" + part;
  }

  private static void captureNet(EquipmentObjMesh mesh, String id, String cloth) {
    mesh.group("handle");
    mesh.beam(material(id, "grip"), new P(8, -2, 8), new P(8, 11.3, 8), .50, 8);
    mesh.group("rim");
    for (int sector = 0; sector < 8; sector++) {
      double a = sector * Math.PI / 4, b = (sector + 1) * Math.PI / 4;
      mesh.beam(
          material(id, "body"),
          new P(8 + 4.1 * Math.cos(a), 15 + 4.1 * Math.sin(a), 8),
          new P(8 + 4.1 * Math.cos(b), 15 + 4.1 * Math.sin(b), 8),
          .20,
          4);
    }
    mesh.group("mesh");
    weave(mesh, cloth, true);
  }

  private static P pouch(double t, double angle) {
    double radius = 4.05 * Math.sqrt(Math.max(0, 1 - t * t));
    return new P(
        8 + radius * Math.cos(angle), 15 - .35 * t + radius * Math.sin(angle), 8 + 6.6 * t);
  }

  private static void castingNet(EquipmentObjMesh mesh, String id, String cloth) {
    mesh.group("mesh");
    weave(mesh, cloth, false);
    mesh.group("weights");
    for (int sector = 0; sector < 8; sector++) {
      P point = fold(1, (sector + .5) * Math.PI / 4);
      mesh.beam(
          material(id, "detail"),
          point.add(new P(0, -.25, 0)),
          point.add(new P(0, .25, 0)),
          .23,
          4);
    }
    mesh.group("cord");
    mesh.beam(material(id, "body"), new P(8, 4.5, 8), new P(7.3, 2.8, 8), .18, 4);
    mesh.beam(material(id, "body"), new P(7.3, 2.8, 8), new P(8.5, 1.6, 8), .18, 4);
    if (id.endsWith("_full")) {
      mesh.group("contents");
      mesh.beam(material(id, "dark"), new P(8, 6, 8), new P(8, 12.2, 8), 1.6, 8);
    }
  }

  private static P fold(double t, double angle) {
    double radius = .45 + 3 * t, crease = 1 + .15 * Math.cos(4 * angle);
    return new P(
        8 + radius * Math.cos(angle) * crease,
        4.5 + 9 * t + .45 * t * Math.sin(3 * angle),
        8 + radius * .65 * Math.sin(angle) * crease);
  }

  private static void weave(EquipmentObjMesh mesh, String material, boolean capture) {
    for (int row = 0; row < 3; row++)
      for (int sector = 0; sector < 8; sector++) {
        double phase = row % 2 == 0 ? 0 : .5, nextPhase = row % 2 == 0 ? .5 : 0;
        double t0 = row / 3.0, t1 = (row + 1) / 3.0;
        P first =
            capture
                ? pouch(t0, (sector + phase) * Math.PI / 4)
                : fold(t0, (sector + phase) * Math.PI / 4);
        P previous = null;
        for (int neighbor : new int[] {sector, sector + (row % 2 == 0 ? -1 : 1)}) {
          P end =
              capture
                  ? pouch(t1, (neighbor + nextPhase) * Math.PI / 4)
                  : fold(t1, (neighbor + nextPhase) * Math.PI / 4);
          if (previous == null || end.vector().distance(previous.vector()) > .0001)
            mesh.beam(material, first, end, .16, 4);
          previous = end;
        }
      }
  }

  private static void bow(EquipmentObjMesh mesh, String id) {
    double draw = id.endsWith("_2") ? 1 : id.endsWith("_1") ? .65 : 0;
    mesh.group("grip");
    mesh.beam(material(id, "grip"), new P(8, 10.5, 6.4), new P(8, 15.5, 6.4), .85, 6);
    mesh.group("limbs");
    List<P> tips = new ArrayList<>();
    for (int side : new int[] {-1, 1}) {
      List<P> points =
          List.of(
              new P(8, 13 + side * 2.5, 6.4),
              new P(8, 13 + side * 5.5 * (1 - .22 * draw), 5.9 + draw * .8),
              new P(8, 13 + side * 8.5 * (1 - .25 * draw), 7.1 + draw * 1.2),
              new P(8, 13 + side * 11 * (1 - .28 * draw), 10.5 + draw * 1.3));
      for (int part = 1; part < points.size(); part++)
        mesh.beam(
            material(id, "body"), points.get(part - 1), points.get(part), .90 - part * .16, 6);
      tips.add(points.getLast());
    }
    P nock = new P(8, 13, 10.5 + 7 * draw);
    mesh.group("string");
    for (P tip : tips) mesh.beam(material(id, "paper"), tip, nock, .065, 4);
    mesh.group("star");
    for (int ray = 0; ray < 4; ray++) {
      double angle = ray * Math.PI / 2;
      P a = new P(8 + Math.cos(angle) * .85, 13 + Math.sin(angle) * .85, 5.45),
          b =
              new P(
                  8 + Math.cos(angle + Math.PI / 4) * .33,
                  13 + Math.sin(angle + Math.PI / 4) * .33,
                  5.45);
      mesh.quad(
          material(id, "paper"),
          new P[] {new P(8, 13, 5.42), a, b, b},
          new P(0, 0, -1),
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {1, 1}});
    }
    if (id.contains("_pulling_")) {
      mesh.group("arrow");
      P tip = nock.add(new P(0, 0, -15));
      mesh.beam(material(id, "grip"), nock, tip, .11, 4);
      mesh.beam(material(id, "detail"), tip, tip.add(new P(0, 0, -1.2)), .25, 4);
      mesh.beam(
          material(id, "paper"), nock.add(new P(0, 0, -.7)), nock.add(new P(0, 0, -2.2)), .30, 4);
    }
  }

  private static void trowel(EquipmentObjMesh mesh, String id) {
    mesh.group("grip");
    mesh.beam(material(id, "grip"), new P(8, 1, 8), new P(8, 6.5, 8), .85, 8);
    mesh.group("neck");
    mesh.beam(material(id, "body"), new P(8, 6.3, 8), new P(8, 10, 8.5), .3, 4);
    mesh.group("blade");
    List<P> outline =
        List.of(
            new P(8, 17.5, 8.5),
            new P(5.6, 12.6, 8.5),
            new P(6.3, 10.2, 8.5),
            new P(8, 9.5, 8.5),
            new P(9.7, 10.2, 8.5),
            new P(10.4, 12.6, 8.5));
    for (int index = 0; index < outline.size(); index++) {
      P a = outline.get(index), b = outline.get((index + 1) % outline.size());
      mesh.quad(
          material(id, "body"),
          new P[] {a, b, new P(8, 12.4, 9.3), new P(8, 12.4, 9.3)},
          new P(0, 0, 1),
          new double[][] {
            bladeUv(a), bladeUv(b), bladeUv(new P(8, 12.4, 9.3)), bladeUv(new P(8, 12.4, 9.3))
          });
      mesh.quad(
          material(id, "body"),
          new P[] {
            a.add(new P(0, 0, -.3)),
            b.add(new P(0, 0, -.3)),
            new P(8, 12.4, 8.2),
            new P(8, 12.4, 8.2)
          },
          new P(0, 0, -1),
          new double[][] {
            bladeUv(a), bladeUv(b), bladeUv(new P(8, 12.4, 8.2)), bladeUv(new P(8, 12.4, 8.2))
          });
      mesh.quad(
          material(id, "edge"),
          new P[] {a, b, b.add(new P(0, 0, -.3)), a.add(new P(0, 0, -.3))},
          new P((a.x() + b.x()) / 2 - 8, (a.y() + b.y()) / 2 - 12.4, 0),
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
    }
  }

  private static void saw(EquipmentObjMesh mesh, String id) {
    mesh.group("handle");
    for (int index = 0; index < 8; index++) {
      double a = index * Math.PI / 4, b = (index + 1) * Math.PI / 4;
      mesh.beam(
          material(id, "grip"),
          new P(8 + 2 * Math.cos(a), 4.3 + 2.3 * Math.sin(a), 8),
          new P(8 + 2 * Math.cos(b), 4.3 + 2.3 * Math.sin(b), 8),
          .50,
          4);
    }
    mesh.group("blade");
    extrude(
        mesh,
        material(id, "body"),
        List.of(new P(6.2, 6.4, 8), new P(10, 6.4, 8), new P(9.3, 16.5, 8), new P(6.2, 17, 8)),
        .3);
    mesh.group("teeth");
    for (int index = 0; index < 8; index++) {
      double y = 7.1 + index * 1.15;
      extrude(
          mesh,
          material(id, "edge"),
          List.of(new P(6.2, y, 8), new P(4.95, y + .7, 8), new P(6.2, y + 1.05, 8)),
          .3);
    }
  }

  private static double[] bladeUv(P point) {
    return new double[] {(point.x() - 5.5) / 5, (17.5 - point.y()) / 8};
  }

  private static void extrude(
      EquipmentObjMesh mesh, String material, List<P> points, double depth) {
    for (int side : new int[] {-1, 1})
      for (int index = 1; index < points.size() - 1; index++) {
        P a = points.getFirst().add(new P(0, 0, side * depth)),
            b = points.get(index).add(new P(0, 0, side * depth)),
            c = points.get(index + 1).add(new P(0, 0, side * depth));
        mesh.quad(
            material,
            new P[] {a, b, c, c},
            new P(0, 0, side),
            new double[][] {{0, 0}, {1, 0}, {1, 1}, {1, 1}});
      }
    P center =
        new P(
            points.stream().mapToDouble(P::x).average().orElseThrow(),
            points.stream().mapToDouble(P::y).average().orElseThrow(),
            8);
    for (int index = 0; index < points.size(); index++) {
      P a = points.get(index), b = points.get((index + 1) % points.size());
      mesh.quad(
          material,
          new P[] {
            a.add(new P(0, 0, depth)),
            b.add(new P(0, 0, depth)),
            b.add(new P(0, 0, -depth)),
            a.add(new P(0, 0, -depth))
          },
          a.add(b).scale(.5).add(center.scale(-1)),
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
    }
  }

  private static JsonObject bowDisplay() {
    JsonObject result = new JsonObject();
    result.add(
        "thirdperson_righthand",
        transform(new double[] {0, 0, 0}, new double[] {-2.25, -6.5, 1.5}, .9));
    result.add(
        "thirdperson_lefthand",
        transform(new double[] {0, 0, -180}, new double[] {-2.25, 2.5, 1.5}, .9));
    result.add(
        "firstperson_righthand",
        transform(new double[] {25, 14, 0}, new double[] {2.75, 2.5, -2}, .65));
    result.add(
        "firstperson_lefthand",
        transform(new double[] {25, 14, -180}, new double[] {3.75, 4.5, -1.5}, .65));
    return result;
  }

  private static JsonObject transform(double[] rotation, double[] translation, double scale) {
    JsonObject result = new JsonObject();
    result.add("rotation", vector(rotation));
    result.add("translation", vector(translation));
    result.add("scale", vector(scale, scale, scale));
    return result;
  }

  private static JsonArray vector(double... values) {
    JsonArray result = new JsonArray();
    for (double value : values) result.add(value);
    return result;
  }
}
