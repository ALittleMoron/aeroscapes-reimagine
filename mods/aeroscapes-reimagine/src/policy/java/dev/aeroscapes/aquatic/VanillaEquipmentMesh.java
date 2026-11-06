package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Authored instrument shells and swept rope, loaded by NeoForge's existing OBJ loader. */
final class VanillaEquipmentMesh {
  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/vanilla_equipment/";
  private static final String TEXTURES = "aeroscapes_reimagine:item/vanilla_equipment/";
  private static final Map<String, Integer> COLORS =
      Map.ofEntries(
          Map.entry("dial", 0x262C2D), Map.entry("marks", 0xB6B3A1),
          Map.entry("red", 0xBB3533), Map.entry("cyan", 0x23B3B8),
          Map.entry("needle", 0xA9AFAD), Map.entry("day", 0x4265AA),
          Map.entry("night", 0x1C2339), Map.entry("sun", 0xF2CA49),
          Map.entry("moon", 0xC3C6C1), Map.entry("engraving", 0x765524));

  private VanillaEquipmentMesh() {}

  static boolean supports(String id) {
    return id.matches("minecraft:(clock(?:_[0-9]{2})?|(?:recovery_)?compass(?:_[0-9]{2})?|lead)");
  }

  static JsonObject model(String id, JsonObject original) {
    JsonObject result = original.deepCopy();
    result.addProperty("loader", "neoforge:obj");
    result.addProperty("model", DIRECTORY + id.substring(id.indexOf(':') + 1) + ".obj");
    result.addProperty("automatic_culling", false);
    result.addProperty("emissive_ambient", false);
    result.addProperty("flip_v", false);
    result.addProperty("ambientocclusion", false);
    result.addProperty("render_type", "minecraft:cutout");
    result.getAsJsonObject("textures").addProperty("particle", "#layer0");
    JsonObject display = result.getAsJsonObject("display");
    for (String hand : List.of("firstperson", "thirdperson"))
      if (!display.has(hand + "_lefthand"))
        display.add(hand + "_lefthand", display.get(hand + "_righthand").deepCopy());
    return result;
  }

  static void addResources(Path assets, Map<Path, byte[]> output, Map<String, JsonObject> models)
      throws IOException {
    Path directory = assets.resolve("aeroscapes_reimagine/models/item/vanilla_equipment");
    for (var entry : models.entrySet()) {
      if (!supports(entry.getKey())) continue;
      String name = entry.getKey().substring("minecraft:".length());
      EquipmentObjMesh mesh = new EquipmentObjMesh();
      if (name.equals("lead")) lead(mesh);
      else
        instrument(
            mesh, name, entry.getValue().getAsJsonObject("textures").get("layer0").getAsString());
      output.put(directory.resolve(name + ".obj"), mesh.obj().getBytes(StandardCharsets.UTF_8));
    }
    StringBuilder materials = new StringBuilder();
    List<String> names = new ArrayList<>(List.of("gold", "silver", "recovery", "leather"));
    names.addAll(COLORS.keySet().stream().sorted().toList());
    for (String name : names)
      materials
          .append("newmtl ")
          .append(name)
          .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd ")
          .append(TEXTURES)
          .append(name)
          .append("\n\n");
    output.put(
        directory.resolve("equipment.mtl"),
        (materials.toString().stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8));
    for (var entry : COLORS.entrySet()) {
      BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < 32; y++)
        for (int x = 0; x < 32; x++) {
          int shade = Math.floorMod(x * 11 + y * 7, 61) == 0 ? -1 : 0;
          if (entry.getKey().equals("night") && Math.floorMod(x * 17 + y * 31, 131) == 0) shade = 4;
          image.setRGB(x, y, EquipmentModels.shaded(entry.getValue(), shade));
        }
      output.put(
          assets.resolve(
              "aeroscapes_reimagine/textures/item/vanilla_equipment/" + entry.getKey() + ".png"),
          EquipmentModels.png(image));
    }
    BufferedImage rope = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 16; y++)
      for (int x = 0; x < 16; x++) {
        int strand = Math.floorMod(x + y / 2, 8);
        int color = strand < 2 ? 0x80603E : strand < 5 ? 0xA17B50 : 0xB08A5B;
        rope.setRGB(x, y, 0xFF000000 | color);
      }
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/vanilla_equipment/leather.png"),
        EquipmentModels.png(rope));
  }

  private static void instrument(EquipmentObjMesh mesh, String name, String nativeTexture) {
    boolean clock = name.startsWith("clock"), recovery = name.startsWith("recovery");
    String metal = clock ? "gold" : recovery ? "recovery" : "silver";
    caseShell(mesh, metal);
    double angle = clock ? clockAngle(nativeTexture) : pointerAngle(nativeTexture);
    for (int side : new int[] {-1, 1}) {
      if (clock) clockFace(mesh, angle, side);
      else compassFace(mesh, angle, recovery, side);
    }
    if (clock) {
      List<P> crown = List.of(new P(12.65, 8, 8), new P(13.35, 8, 8));
      mesh.tube("gold", crown, .48, 8, 1);
    }
  }

  static void compass(EquipmentObjMesh mesh, String nativeTexture) {
    instrument(mesh, "compass", nativeTexture);
  }

  static void caseShell(EquipmentObjMesh mesh, String metal) {
    double[][] profile = {
      {4.12, 7.61},
      {4.34, 7.40},
      {4.63, 7.45},
      {4.83, 7.65},
      {4.83, 8.35},
      {4.63, 8.55},
      {4.34, 8.60},
      {4.12, 8.39}
    };
    for (int edge = 0; edge < profile.length; edge++) {
      double[] a = profile[edge], b = profile[(edge + 1) % profile.length];
      for (int segment = 0; segment < 16; segment++) {
        double first = 2 * Math.PI * segment / 16, second = first + 2 * Math.PI / 16;
        mesh.quad(
            metal,
            new P[] {
              circle(a[0], first, a[1]),
              circle(a[0], second, a[1]),
              circle(b[0], second, b[1]),
              circle(b[0], first, b[1])
            },
            new P(
                (b[1] - a[1]) * Math.cos((first + second) / 2),
                (b[1] - a[1]) * Math.sin((first + second) / 2),
                a[0] - b[0]),
            new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
      }
    }
  }

  private static double pointerAngle(String texture) {
    int frame = Integer.parseInt(texture.substring(texture.lastIndexOf('_') + 1));
    // Native frame00 points down, then 08 left, 16 up and 24 right for both compasses.
    return -Math.PI / 2 - frame * Math.PI / 16;
  }

  private static double clockAngle(String texture) {
    int frame = Integer.parseInt(texture.substring(texture.lastIndexOf('_') + 1));
    return Math.PI / 2 - frame * Math.PI / 32;
  }

  private static void compassFace(EquipmentObjMesh mesh, double angle, boolean recovery, int side) {
    disc(mesh, "dial", 8, 8, 4.12, 8 + side * .32, side);
    for (int tick = 0; tick < 16; tick++) {
      double turn = tick * Math.PI / 8,
          length = tick % 4 == 0 ? .65 : .26,
          width = tick % 4 == 0 ? .11 : .07;
      P direction = new P(Math.cos(turn), Math.sin(turn), 0),
          across = new P(-Math.sin(turn), Math.cos(turn), 0);
      P start =
          new P(
              8 + direction.x() * (3.82 - length),
              8 + direction.y() * (3.82 - length),
              8 + side * .34);
      P end = new P(8 + direction.x() * 3.82, 8 + direction.y() * 3.82, start.z());
      mesh.quad(
          recovery && tick % 4 == 0 ? "cyan" : "marks",
          new P[] {
            start.add(across.scale(-width)),
            end.add(across.scale(-width)),
            end.add(across.scale(width)),
            start.add(across.scale(width))
          },
          new P(0, 0, side),
          null);
    }
    needle(mesh, recovery ? "cyan" : "red", angle, 2.85, side);
    needle(mesh, "needle", angle + Math.PI, 2.25, side);
    disc(mesh, "needle", 8, 8, .31, 8 + side * .68, side);
  }

  private static void needle(
      EquipmentObjMesh mesh, String material, double angle, double length, int side) {
    P direction = new P(Math.cos(angle), Math.sin(angle), 0),
        across = new P(-Math.sin(angle), Math.cos(angle), 0);
    P center = new P(8, 8, 8 + side * .38), tip = center.add(direction.scale(length));
    P left = center.add(across.scale(.43)), right = center.add(across.scale(-.43));
    P ridge = new P(8, 8, 8 + side * .64);
    mesh.quad(material, new P[] {left, tip, ridge, ridge}, new P(0, 0, side), null);
    mesh.quad(material, new P[] {ridge, tip, right, right}, new P(0, 0, side), null);
  }

  private static void clockFace(EquipmentObjMesh mesh, double angle, int side) {
    for (int half = 0; half < 2; half++)
      for (int segment = 0; segment < 16; segment++) {
        double first = angle - Math.PI / 2 + half * Math.PI + segment * Math.PI / 16,
            second = first + Math.PI / 16;
        mesh.quad(
            half == 0 ? "day" : "night",
            new P[] {
              new P(8, 8, 8 + side * .32),
              circle(4.12, first, 8 + side * .32),
              circle(4.12, second, 8 + side * .32),
              circle(4.12, second, 8 + side * .32)
            },
            new P(0, 0, side),
            null);
      }
    for (int body = 0; body < 2; body++) {
      double turn = angle + body * Math.PI,
          x = 8 + 2.55 * Math.cos(turn),
          y = 8 + 2.55 * Math.sin(turn);
      disc(mesh, body == 0 ? "sun" : "moon", x, y, body == 0 ? .58 : .5, 8 + side * .45, side);
      if (body == 0)
        for (int ray = 0; ray < 8; ray++) {
          double direction = ray * Math.PI / 4;
          P tip = new P(x + Math.cos(direction) * .9, y + Math.sin(direction) * .9, 8 + side * .45);
          P a =
              new P(
                  x + Math.cos(direction - .18) * .64,
                  y + Math.sin(direction - .18) * .64,
                  tip.z());
          P b =
              new P(
                  x + Math.cos(direction + .18) * .64,
                  y + Math.sin(direction + .18) * .64,
                  tip.z());
          mesh.quad("sun", new P[] {a, tip, b, b}, new P(0, 0, side), null);
        }
    }
    for (int segment = 0; segment < 16; segment++) {
      double first = Math.PI + segment * Math.PI / 16, second = first + Math.PI / 16;
      mesh.quad(
          "gold",
          new P[] {
            new P(8, 8, 8 + side * .56),
            circle(4.12, first, 8 + side * .56),
            circle(4.12, second, 8 + side * .56),
            circle(4.12, second, 8 + side * .56)
          },
          new P(0, 0, side),
          null);
    }
    for (int line = 0; line < 3; line++) {
      double half = 2.3 - line * .45, y = 7.45 - line * .65, z = 8 + side * .58;
      mesh.quad(
          "engraving",
          new P[] {
            new P(8 - half, y, z),
            new P(8 + half, y, z),
            new P(8 + half, y + .09, z),
            new P(8 - half, y + .09, z)
          },
          new P(0, 0, side),
          null);
    }
  }

  private static void disc(
      EquipmentObjMesh mesh,
      String material,
      double x,
      double y,
      double radius,
      double z,
      int side) {
    for (int segment = 0; segment < 16; segment++) {
      double a = segment * Math.PI / 8, b = a + Math.PI / 8;
      P first = new P(x + radius * Math.cos(a), y + radius * Math.sin(a), z),
          second = new P(x + radius * Math.cos(b), y + radius * Math.sin(b), z);
      mesh.quad(material, new P[] {new P(x, y, z), first, second, second}, new P(0, 0, side), null);
    }
  }

  private static P circle(double radius, double angle, double z) {
    return new P(8 + radius * Math.cos(angle), 8 + radius * Math.sin(angle), z);
  }

  private static void lead(EquipmentObjMesh mesh) {
    List<P> coil = new ArrayList<>();
    for (int step = 0; step <= 144; step++) {
      double angle = -step * Math.PI / 24;
      coil.add(new P(7.6 + 3.1 * Math.cos(angle), 9.3 + 3.3 * Math.sin(angle), 6.6 + step / 48.0));
    }
    coil.addAll(
        List.of(
            new P(10.7, 7.8, 9.65),
            new P(10.4, 5.4, 9.9),
            new P(9.7, 3.4, 9.6),
            new P(8.2, 2.5, 9.0),
            new P(6.5, 2.8, 8.6),
            new P(5.8, 3.6, 8.5)));
    mesh.tube("leather", coil, .40, 6, 2.6);
    mesh.tube(
        "leather",
        List.of(
            new P(10.1, 9, 6),
            new P(11, 9.2, 6.2),
            new P(11.3, 8.4, 8),
            new P(11, 7.9, 10.2),
            new P(10.1, 8, 10.3),
            new P(9.8, 8.5, 8),
            new P(10.1, 9, 6)),
        .22,
        6,
        2.6);
  }
}
