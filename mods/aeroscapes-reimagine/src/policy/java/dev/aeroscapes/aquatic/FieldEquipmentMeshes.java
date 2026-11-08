package dev.aeroscapes.aquatic;

import static dev.aeroscapes.aquatic.EquipmentObjMesh.octagonalRing;

import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Held forms with material fields taken from opaque regions of their pinned native sprites. */
final class FieldEquipmentMeshes {
  private FieldEquipmentMeshes() {}

  static boolean supports(String id) {
    return isClaw(id)
        || switch (id) {
          case "betterarcheology:bomb",
              "wetland_whimsy:dagger",
              "wetland_whimsy:sludge_charge",
              "crittersandcompanions:grappling_hook" ->
              true;
          default -> false;
        };
  }

  private static boolean isClaw(String id) {
    return id.startsWith("spawn:coastal_crab_claw");
  }

  static void build(EquipmentObjMesh mesh, String id, JsonObject model, StringBuilder materials)
      throws IOException {
    String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
    boolean empty = id.endsWith("_empty");
    if (isClaw(id))
      texture = empty ? "spawn:item/coastal_crab_claw_empty" : "spawn:item/coastal_crab_claw";
    Map<String, int[]> fields =
        switch (id) {
          case "betterarcheology:bomb" ->
              Map.of(
                  "iron", new int[] {4, 5, 11, 11},
                  "socket", new int[] {10, 3, 12, 4},
                  "fuse", new int[] {11, 0, 14, 1});
          case "wetland_whimsy:dagger" ->
              Map.of(
                  "bone", new int[] {8, 6, 11, 9},
                  "guard", new int[] {4, 9, 7, 10},
                  "blade", new int[] {8, 6, 11, 8},
                  "edge", new int[] {8, 6, 10, 8},
                  "pommel", new int[] {1, 13, 3, 15});
          case "wetland_whimsy:sludge_charge" -> Map.of("mud", new int[] {6, 4, 10, 13});
          case "crittersandcompanions:grappling_hook" ->
              Map.of("silk", new int[] {2, 9, 6, 10}, "iron", new int[] {10, 4, 13, 5});
          default ->
              Map.of(
                  "shell", new int[] {3, 2, 7, 3},
                  "rim", new int[] {8, 4, 10, 5},
                  "center", new int[] {8, 6, 11, 8});
        };
    for (var field :
        fields.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).toList()) {
      String name = field.getKey();
      String fieldTexture = name.equals("bone") ? "wetland_whimsy:item/filthy_bone" : texture;
      var icon = NativeEquipmentModels.image(fieldTexture);
      int[] uv = field.getValue();
      for (int y = uv[1]; y < uv[3]; y++)
        for (int x = uv[0]; x < uv[2]; x++)
          if (icon.getRGB(x, y) >>> 24 != 255)
            throw new IOException("Transparent " + id + " " + name + " sample at " + x + "," + y);
      String key = id.replace(':', '_') + "_" + name;
      mesh.material(name, key);
      mesh.region(
          key, (uv[0] + .04) / 16, (uv[1] + .04) / 16, (uv[2] - .04) / 16, (uv[3] - .04) / 16);
      materials
          .append("newmtl ")
          .append(key)
          .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd ")
          .append(fieldTexture)
          .append("\n\n");
    }
    switch (id) {
      case "betterarcheology:bomb" -> bomb(mesh);
      case "wetland_whimsy:dagger" -> dagger(mesh);
      case "wetland_whimsy:sludge_charge" -> sludge(mesh);
      case "crittersandcompanions:grappling_hook" -> hook(mesh);
      default -> claw(mesh, id, materials);
    }
  }

  private static void bomb(EquipmentObjMesh mesh) {
    mesh.group("cast_iron_shell");
    mesh.shell(
        "iron",
        new P[][] {
          octagonalRing(8, 4.1, 8, 1.55, 1.48),
          octagonalRing(8, 5.1, 8, 2.75, 2.7),
          octagonalRing(8, 7.6, 8, 3.4, 3.3),
          octagonalRing(8, 9.7, 8, 2.8, 2.76),
          octagonalRing(8, 10.85, 8, 1.52, 1.48)
        });
    mesh.group("fuse_socket");
    mesh.beam("socket", new P(8, 10.65, 8), new P(8, 11.55, 8), .82, 8);
    mesh.group("fuse");
    mesh.tube(
        "fuse",
        List.of(
            new P(8, 11.4, 8),
            new P(8.25, 12.5, 8),
            new P(8.95, 13.16, 8.13),
            new P(9.58, 12.95, 8.32)),
        .25,
        6,
        1.45);
  }

  private static void dagger(EquipmentObjMesh mesh) {
    mesh.group("bone_grip");
    mesh.shell(
        "bone",
        new P[][] {
          octagonalRing(8, 1.3, 8, .78, .78),
          octagonalRing(7.93, 2.28, 8.07, 1.02, .84),
          octagonalRing(8.04, 5.63, 7.97, .74, .72),
          octagonalRing(8, 7.55, 8, .9, .86)
        });
    mesh.box("pommel", 7.05, 1, 7.1, 8.95, 1.6, 8.9);
    mesh.box("guard", 6.54, 7.3, 7.05, 9.46, 8.15, 8.95);
    mesh.group("rusted_blade");
    List<P> outline =
        List.of(
            new P(6.92, 7.88, 8),
            new P(9.12, 7.88, 8),
            new P(9.06, 13.4, 8),
            new P(8.25, 16.15, 8),
            new P(6.97, 14.2, 8));
    for (int side : new int[] {-1, 1}) {
      P base = new P(8.04, 7.88, 8 + side * .5);
      P ridge = new P(8.11, 13.57, 8 + side * .36);
      P outward = new P(0, 0, side);
      bladeFace(mesh, "blade", outward, outline.get(0), outline.get(4), ridge, base);
      bladeFace(mesh, "edge", outward, base, ridge, outline.get(2), outline.get(1));
      bladeFace(mesh, "blade", outward, outline.get(4), outline.get(3), ridge, ridge);
      bladeFace(mesh, "edge", outward, ridge, outline.get(3), outline.get(2), outline.get(2));
    }
    P a = outline.get(0), b = outline.get(1);
    mesh.quad(
        "blade",
        new P[] {a, new P(8.04, 7.88, 7.5), b, new P(8.04, 7.88, 8.5)},
        new P(0, -1, 0),
        faceUv());
  }

  private static void bladeFace(
      EquipmentObjMesh mesh, String material, P outward, P a, P b, P c, P d) {
    mesh.quad(
        material,
        new P[] {a, b, c, c},
        outward,
        new double[][] {bladeUv(a), bladeUv(b), bladeUv(c), bladeUv(c)});
    if (!c.equals(d))
      mesh.quad(
          material,
          new P[] {a, c, d, d},
          outward,
          new double[][] {bladeUv(a), bladeUv(c), bladeUv(d), bladeUv(d)});
  }

  private static double[] bladeUv(P point) {
    return new double[] {(point.x() - 6.92) / 2.2, (16.15 - point.y()) / 8.27};
  }

  private static void sludge(EquipmentObjMesh mesh) {
    mesh.group("wet_clump");
    P[] lower = octagonalRing(8.36, 5.15, 8.3, 1.48, 1.18);
    lower[0] = lower[0].add(new P(.34, -.85, -.12));
    lower[5] = lower[5].add(new P(-.22, -.46, -.2));
    P[] upper = octagonalRing(7.38, 11.16, 7.8, 1.45, 1.23);
    upper[3] = upper[3].add(new P(-.42, .38, .18));
    P[] belly = octagonalRing(8, 8.25, 8, 3.2, 2.53);
    belly[2] = belly[2].add(new P(.49, -.44, .28));
    belly[6] = belly[6].add(new P(-.3, .66, -.21));
    mesh.shell(
        "mud",
        new P[][] {
          lower,
          octagonalRing(8.2, 6.11, 8.16, 2.72, 2.12),
          belly,
          octagonalRing(7.7, 10.18, 8, 2.63, 1.98),
          upper
        });
    mesh.shell(
        "mud",
        new P[][] {
          octagonalRing(6.63, 5.12, 8.74, .71, .56),
          octagonalRing(6.22, 6.48, 8.47, 1.39, 1.22),
          octagonalRing(6.67, 8.24, 8.56, 1.12, 1.07),
          octagonalRing(7.16, 9.03, 8.37, .63, .67)
        });
  }

  private static void claw(EquipmentObjMesh mesh, String id, StringBuilder materials) {
    boolean empty = id.endsWith("_empty");
    int charge = id.endsWith("shooting2") ? 2 : id.endsWith("shooting1") ? 1 : 0;
    mesh.group("palm");
    mesh.shell(
        "shell",
        new P[][] {
          clawRing(3.35, 6.8, 1.05, 1.35),
          clawRing(4.75, 6.9, 2.43, 2.05),
          clawRing(6.75, 7.0, 2.66, 2.2),
          clawRing(8.65, 7.12, 2.24, 1.59)
        });
    mesh.group("colored_shell");
    mesh.shell(
        "rim",
        new P[][] {
          clawPatch(4.9, 6.84, 9.11, 1.32, 1.62),
          clawPatch(4.92, 6.84, 9.74, 1.58, 1.89),
          clawPatch(4.95, 6.82, 10.24, 1.27, 1.59)
        });
    mesh.shell(
        "center",
        new P[][] {
          clawPatch(4.91, 6.79, 10.18, .92, 1.15),
          clawPatch(4.87, 6.77, 10.64, 1.03, 1.25),
          clawPatch(4.82, 6.73, 10.89, .76, .98)
        });
    mesh.group("fixed_finger");
    mesh.shell(
        "shell",
        new P[][] {
          clawRing(7.62, 5.69, 1.21, 1.23),
          clawRing(9.39, 4.95, .98, 1.12),
          clawRing(11.45, 5.08, .64, .78),
          clawRing(12.92, 6.08, .14, .23)
        });
    mesh.group("movable_finger");
    double lift = empty ? -.65 : .45 * charge;
    mesh.shell(
        "shell",
        new P[][] {
          clawRing(7.56, 8.61, 1.24, 1.25),
          clawRing(9.4, 9.01 + lift * .55, 1.04, 1.18),
          clawRing(11.64, 8.67 + lift, .64, .79),
          clawRing(13.17, 7.47 + lift, .13, .2)
        });
    if (id.contains("_shooting")) {
      String key = id.replace(':', '_') + "_bubble";
      materials
          .append("newmtl ")
          .append(key)
          .append("\nKa 0 0 0\nKd 1 1 1\n")
          .append("map_Kd spawn:entity/coastal_crab/bubble\n\n");
      mesh.region(key, .25, .5, .5, 1);
      mesh.group("water_bubble");
      double radius = .65 + charge * .45, center = 7.02 + charge * .2;
      mesh.box(
          key,
          11.55 - radius,
          center - radius,
          8 - radius,
          11.55 + radius,
          center + radius,
          8 + radius);
    }
  }

  private static P[] clawRing(double x, double y, double height, double depth) {
    P[] ring = octagonalRing(y, x, 8, height, depth);
    for (int side = 0; side < ring.length; side++) {
      P point = ring[side];
      ring[side] = new P(point.y(), point.x(), point.z());
    }
    return ring;
  }

  private static P[] clawPatch(double x, double y, double z, double width, double height) {
    P[] ring = octagonalRing(x, z, y, width, height);
    for (int side = 0; side < ring.length; side++) {
      P point = ring[side];
      ring[side] = new P(point.x(), point.z(), point.y());
    }
    return ring;
  }

  private static void hook(EquipmentObjMesh mesh) {
    mesh.group("silk_coil");
    List<P> cord = new ArrayList<>();
    for (int step = 0; step <= 18; step++) {
      double angle = step * Math.PI / 4.5;
      cord.add(
          new P(
              7 + 2.28 * Math.cos(angle) - .44 * Math.sin(2 * angle),
              6.8 + (3.13 - step * .026) * Math.sin(angle) - .36 * Math.cos(2 * angle),
              7.05 + step * .1 + .26 * Math.sin(angle)));
    }
    cord.addAll(
        List.of(
            new P(10.01, 6.75, 9.11),
            new P(10.66, 6.76, 9.19),
            new P(10.91, 6.87, 8.67),
            new P(10.3, 6.9, 8.25),
            new P(9.87, 6.86, 8.52),
            new P(9.58, 6.53, 8.94)));
    mesh.tube("silk", cord, .32, 6, 2.2);
    mesh.group("hook");
    mesh.beam("iron", new P(10.35, 6.5, 8.8), new P(10.35, 10.75, 8.8), .43, 6);
    for (int side : new int[] {-1, 1}) {
      mesh.tube(
          "iron",
          List.of(
              new P(10.35, 10.6, 8.8),
              new P(10.45 + side * 1.3, 10.45, 8.8 + side * .43),
              new P(10.45 + side * 2.05, 9.2, 8.8 + side * .8),
              new P(10.45 + side * 1.84, 8.2, 8.8 + side * .93)),
          .38,
          5,
          2.1);
    }
  }

  private static double[][] faceUv() {
    return new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
  }
}
