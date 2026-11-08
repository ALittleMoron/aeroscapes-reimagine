package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.joml.Vector3f;

/** The reviewed nets, articulated celestial bow and pointed workshop blades. */
final class ReviewedEquipmentModels {
  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/reviewed_equipment/";

  private ReviewedEquipmentModels() {}

  static boolean supports(String id) {
    return id.equals("naturalist:capture_net")
        || id.startsWith("spawn:casting_net")
        || id.startsWith("tide:starlight_bow")
        || FieldEquipmentMeshes.supports(id)
        || id.equals("quark:pickarang")
        || id.equals("quark:flamerang")
        || id.startsWith("supplementaries:bomb")
        || id.equals("quark:trowel");
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
    JsonObject display =
        id.startsWith("tide:starlight_bow")
            ? bowDisplay()
            : EquipmentSculpture.transforms(id, style, original);
    if (id.equals("spawn:casting_net") || id.equals("spawn:casting_net_full")) {
      JsonObject firstPerson =
          JsonParser.parseString(
                  """
          {
            "firstperson_righthand": {"rotation": [60,150,30], "scale": [0.67,0.67,0.67]},
            "firstperson_lefthand": {"rotation": [60,150,30], "scale": [0.67,0.67,0.67]}
          }
          """)
              .getAsJsonObject();
      // The gathered knot stays within the palm while the pouch extends forward into view.
      HeldItemModels.anchorHeld(
          firstPerson, new Vector3f(5.6f, 8.35f, 8), new Vector3f(0, 1.5f, 0));
      for (var hand : firstPerson.entrySet()) display.add(hand.getKey(), hand.getValue());
    }
    result.add("display", display);
    return result;
  }

  static void addResources(Path assets, Map<Path, byte[]> output, Map<String, JsonObject> models)
      throws IOException {
    StringBuilder materials = new StringBuilder();
    Path directory = assets.resolve("aeroscapes_reimagine/models/item/reviewed_equipment");
    for (String id : models.keySet()) {
      if (!supports(id)) continue;
      EquipmentObjMesh mesh = new EquipmentObjMesh();
      if (FieldEquipmentMeshes.supports(id)) {
        FieldEquipmentMeshes.build(mesh, id, models.get(id), materials);
        output.put(
            directory.resolve(name(id) + ".obj"), mesh.obj().getBytes(StandardCharsets.UTF_8));
        continue;
      }
      String root =
          id.startsWith("tide:")
              ? "tide:starlight_bow"
              : id.startsWith("spawn:") ? "spawn:casting_net" : id.replace("_projectile", "");
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
      else if (id.startsWith("supplementaries:bomb")) bomb(mesh, id);
      else if (id.equals("quark:pickarang") || id.equals("quark:flamerang")) boomerang(mesh, id);
      else trowel(mesh, id);
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
    mesh.box(material(id, "grip"), 7.43, -2, 7.43, 8.57, 11.3, 8.57);
    mesh.group("socket");
    mesh.box(material(id, "body"), 7.23, 9.65, 7.25, 8.77, 11.12, 8.75);
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
    weave(mesh, cloth, true, false);
  }

  private static P pouch(double t, double angle) {
    double radius = 4.05 * Math.sqrt(Math.max(0, 1 - t * t));
    return new P(
        8 + radius * Math.cos(angle), 15 - .35 * t + radius * Math.sin(angle), 8 + 6.6 * t);
  }

  private static void castingNet(EquipmentObjMesh mesh, String id, String cloth) {
    boolean full = id.endsWith("_full");
    mesh.group("mesh");
    weave(mesh, cloth, false, full);
    mesh.group("weighted_hem");
    List<P> hem = new ArrayList<>();
    for (int sector = 0; sector <= 8; sector++) hem.add(fold(1, (sector % 8) * Math.PI / 4, full));
    mesh.tube(cloth, hem, .19, 4, 2);
    mesh.group("weights");
    for (int sector = 0; sector < 8; sector++) {
      P point = hem.get(sector);
      mesh.beam(
          material(id, "detail"),
          point.add(new P(0, -.42, 0)),
          point.add(new P(0, .12, 0)),
          .27,
          4);
    }
    mesh.group("cord");
    mesh.beam(cloth, new P(5.6, 7.8, 8), new P(5.6, 8.9, 8), .58, 8);
    mesh.tube(
        material(id, "body"),
        List.of(
            new P(5.6, 8.65, 8),
            new P(4.5, 9.75, 8.4),
            new P(3.35, 9.45, 8.8),
            new P(3.14, 8.14, 8.9),
            new P(3.65, 7.35, 8.57),
            new P(5.5, 8.02, 8)),
        .22,
        5,
        2);
    if (full) {
      mesh.group("contents");
      mesh.shell(
          material(id, "dark"),
          new P[][] {
            EquipmentObjMesh.octagonalRing(7.34, -1.2, 7.94, 1.02, .78),
            EquipmentObjMesh.octagonalRing(7.2, .18, 7.91, 2.1, 1.55),
            EquipmentObjMesh.octagonalRing(6.82, 2.94, 7.98, 2.17, 1.65),
            EquipmentObjMesh.octagonalRing(6.48, 4.56, 8.04, 1.14, .91)
          });
    }
  }

  private static P fold(double t, double angle, boolean full) {
    double radius = .46 + (full ? 2.8 : 1.5) * Math.sin(Math.PI * t * .91) + 1.05 * t;
    double crease = 1 + .12 * Math.cos(3 * angle);
    return new P(
        5.6 + 1.85 * t + radius * Math.cos(angle) * crease,
        8 - 10.45 * t + .55 * t * Math.sin(3 * angle),
        8 + radius * .75 * Math.sin(angle) * crease);
  }

  private static void weave(EquipmentObjMesh mesh, String material, boolean capture, boolean full) {
    int rows = capture ? 3 : 4;
    for (int row = 0; row < rows; row++)
      for (int sector = 0; sector < 8; sector++) {
        double phase = row % 2 == 0 ? 0 : .5, nextPhase = row % 2 == 0 ? .5 : 0;
        double t0 = row / (double) rows, t1 = (row + 1.0) / rows;
        P first =
            capture
                ? pouch(t0, (sector + phase) * Math.PI / 4)
                : fold(t0, (sector + phase) * Math.PI / 4, full);
        P previous = null;
        for (int neighbor : new int[] {sector, sector + (row % 2 == 0 ? -1 : 1)}) {
          P end =
              capture
                  ? pouch(t1, (neighbor + nextPhase) * Math.PI / 4)
                  : fold(t1, (neighbor + nextPhase) * Math.PI / 4, full);
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
    mesh.box(material(id, "grip"), 7.12, .65, 7.12, 8.88, 7.1, 8.88);
    mesh.box(material(id, "edge"), 7.04, 6.6, 7.04, 8.96, 7.3, 8.96);
    mesh.group("neck");
    P shoulder = new P(8, 8.2, 8.1);
    mesh.beam(material(id, "body"), new P(8, 7, 8), shoulder, .39, 4);
    mesh.beam(material(id, "body"), shoulder, new P(8, 10.55, 8.28), .36, 4);
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

  private static double[] bladeUv(P point) {
    return new double[] {(point.x() - 5.5) / 5, (17.5 - point.y()) / 8};
  }

  private static void boomerang(EquipmentObjMesh mesh, String id) {
    mesh.group("wing");
    // Outer edge, inner edge and half-thickness at each section from tip to tip.
    double[][] sections = {
      {2, 12.25, 2.6, 11.1, .27},
      {4.15, 13.85, 4.55, 11.05, .68},
      {7.4, 14, 7.5, 11, .81},
      {12.35, 13.65, 10.05, 10.55, 1},
      {13.65, 9.05, 10.82, 8.65, .83},
      {13.35, 5.25, 10.95, 5.2, .66},
      {12.6, 2.55, 11.05, 2.95, .29}
    };
    P[][] rings = new P[sections.length][];
    for (int index = 0; index < sections.length; index++) {
      double[] s = sections[index];
      P outer = new P(s[0] - 4.2, s[1] - .45, 8);
      P inner = new P(s[2] - 4.2, s[3] - .45, 8);
      P middle = outer.add(inner).scale(.5);
      rings[index] =
          new P[] {
            outer, middle.add(new P(0, 0, s[4])), inner, middle.add(new P(0, 0, -s[4] * .65))
          };
    }
    for (int index = 0; index < rings.length - 1; index++) {
      boolean tip = index < 2 || index >= 5;
      for (int side = 0; side < 4; side++) {
        int next = (side + 1) % 4;
        String surface = tip ? (side == 0 ? "edge" : "body") : "grip";
        P a = rings[index][side], b = rings[index][next];
        P c = rings[index + 1][next], d = rings[index + 1][side];
        P outward =
            a.add(b)
                .add(c)
                .add(d)
                .scale(.25)
                .add(
                    rings[index][0]
                        .add(rings[index][2])
                        .add(rings[index + 1][0])
                        .add(rings[index + 1][2])
                        .scale(-.25));
        triangle(mesh, material(id, surface), a, b, c, outward);
        triangle(mesh, material(id, surface), a, c, d, outward);
      }
    }
    mesh.quad(material(id, "body"), rings[0], new P(-1, 0, 0), SURFACE_UV);
    mesh.quad(material(id, "body"), rings[rings.length - 1], new P(0, -1, 0), SURFACE_UV);
  }

  private static final double[][] SURFACE_UV = {{.20, .55}, {.41, .55}, {.41, .91}, {.20, .91}};

  private static void triangle(EquipmentObjMesh mesh, String surface, P a, P b, P c, P outward) {
    mesh.quad(surface, new P[] {a, b, c, c}, outward, SURFACE_UV);
  }

  private static void bomb(EquipmentObjMesh mesh, String id) {
    P center = new P(8, 7, 8);
    double[] heights = {-4.15, -2.9, -1.15, 1.3, 3.1, 4.2};
    double[] radii = {1.35, 3.15, 4.35, 4.22, 3.02, 1.45};
    P[][] hull = new P[heights.length][8];
    for (int ring = 0; ring < hull.length; ring++)
      for (int side = 0; side < 8; side++) {
        double angle = (side + .5) * Math.PI / 4;
        hull[ring][side] =
            center.add(
                new P(radii[ring] * Math.cos(angle), heights[ring], radii[ring] * Math.sin(angle)));
      }
    mesh.group("shell");
    String metal = material(id, "body");
    for (int ring = 0; ring < hull.length - 1; ring++)
      for (int side = 0; side < 8; side++) {
        int next = (side + 1) % 8;
        P[] face = {hull[ring][side], hull[ring][next], hull[ring + 1][next], hull[ring + 1][side]};
        P middle = face[0].add(face[1]).add(face[2]).add(face[3]).scale(.25);
        P outward = middle.add(center.scale(-1));
        String surface =
            ring < 2
                ? "dark"
                : ring >= 3 && (side == 1 || side == 2 || side == 6) ? "edge" : "body";
        mesh.quad(material(id, surface), face, outward, SURFACE_UV);
        if (id.contains("spiky") && (ring == 1 || ring == 3) && side % 2 == (ring == 1 ? 0 : 1)) {
          P tip = middle.add(outward.scale(.43));
          for (int edge = 0; edge < 4; edge++) {
            P a = face[edge].scale(.44).add(middle.scale(.56)).add(outward.scale(-.11));
            P b = face[(edge + 1) % 4].scale(.44).add(middle.scale(.56)).add(outward.scale(-.11));
            triangle(mesh, metal, a, b, tip, outward);
          }
        }
      }
    for (int ring : new int[] {0, hull.length - 1}) {
      P cap = center.add(new P(0, heights[ring], 0));
      for (int side = 0; side < 8; side++)
        triangle(
            mesh,
            metal,
            cap,
            hull[ring][side],
            hull[ring][(side + 1) % 8],
            new P(0, ring == 0 ? -1 : 1, 0));
    }
    mesh.group("fuse");
    P socket = new P(8.73, 11.9, 8);
    P bend = new P(9.25, 13.05, 8);
    P end = new P(10.28, 13.6, 8);
    mesh.beam(material(id, "edge"), new P(8.15, 10.62, 8), socket, 1.06, 8);
    mesh.beam(material(id, "rope"), socket, bend, .29, 6);
    mesh.beam(material(id, "rope"), bend, end, .27, 6);
    if (id.endsWith("_projectile"))
      mesh.beam(material(id, "gold"), end, new P(10.65, 13.8, 8), .53, 6);
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
