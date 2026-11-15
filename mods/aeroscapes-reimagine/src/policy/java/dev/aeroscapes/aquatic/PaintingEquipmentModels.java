package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Physical art supplies; native canvas renderers remain responsible for live artwork. */
final class PaintingEquipmentModels {
  static final String NAMESPACE = "xercapaint";
  static final List<String> CANVASES =
      List.of(
          "item_canvas",
          "item_canvas_large",
          "item_canvas_long",
          "item_canvas_tall",
          "item_canvas_glass",
          "item_canvas_glass_large",
          "item_canvas_glass_long",
          "item_canvas_glass_tall");
  private static final String DIRECTORY = "xercapaint:models/item/aeroscapes_painting/";
  private static final String TEXTURE = "xercapaint:item/aeroscapes_painting/materials";

  private PaintingEquipmentModels() {}

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Path directory = assets.resolve("xercapaint/models/item/aeroscapes_painting");
    Map<String, JsonObject> held = new LinkedHashMap<>();
    BufferedImage nativeEmpty = NativeEquipmentModels.image("xercapaint:item/item_palette");
    Map<Integer, String> paints = new LinkedHashMap<>();
    List<BufferedImage> sprites = new ArrayList<>();
    for (int state = 0; state <= 16; state++) {
      String item = "item_palette" + (state == 0 ? "" : state);
      BufferedImage sprite = NativeEquipmentModels.image("xercapaint:item/" + item);
      sprites.add(sprite);
      for (int y = 0; y < sprite.getHeight(); y++)
        for (int x = 0; x < sprite.getWidth(); x++)
          if (sprite.getRGB(x, y) != nativeEmpty.getRGB(x, y))
            paints.computeIfAbsent(sprite.getRGB(x, y), ignored -> "paint_" + paints.size());
    }
    StringBuilder library = new StringBuilder();
    List<String> materials = new ArrayList<>(List.of("palette_face", "palette_edge", "easel"));
    materials.addAll(paints.values());
    for (String material : materials)
      library
          .append("newmtl ")
          .append(material)
          .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd ")
          .append(TEXTURE)
          .append("\n\n");
    EquipmentModels.text(output, directory.resolve("equipment.mtl"), library.toString());
    output.put(
        assets.resolve("xercapaint/textures/item/aeroscapes_painting/materials.png"),
        EquipmentModels.png(materials(materials, paints)));
    for (int state = 0; state <= 16; state++) {
      String item = "item_palette" + (state == 0 ? "" : state);
      EquipmentObjMesh mesh = mesh(materials);
      palette(mesh, sprites.get(state), nativeEmpty, paints);
      EquipmentModels.text(output, directory.resolve(item + ".obj"), mesh.obj());
      held.put("xercapaint:" + item, obj(item, paletteDisplay()));
    }
    EquipmentObjMesh easel = mesh(materials);
    easel(easel);
    EquipmentModels.text(output, directory.resolve("item_easel.obj"), easel.obj());
    held.put("xercapaint:item_easel", obj("item_easel", easelDisplay()));
    for (String canvas : CANVASES) {
      JsonObject nativeRenderer = new JsonObject();
      nativeRenderer.addProperty("parent", "minecraft:builtin/entity");
      nativeRenderer.add("display", canvasDisplay());
      held.put("xercapaint:" + canvas, nativeRenderer);
    }
    HeldItemModels.add(assets, output, held);
  }

  private static EquipmentObjMesh mesh(List<String> materials) {
    EquipmentObjMesh mesh = new EquipmentObjMesh();
    for (int index = 0; index < materials.size(); index++) {
      int x = index % 5 * 64, y = index / 5 * 64;
      mesh.region(materials.get(index), x / 320.0, y / 256.0, (x + 64) / 320.0, (y + 64) / 256.0);
      mesh.projectedField(materials.get(index));
    }
    return mesh;
  }

  private static void palette(
      EquipmentObjMesh mesh,
      BufferedImage sprite,
      BufferedImage nativeEmpty,
      Map<Integer, String> paints) {
    mesh.group("wooden_palette_with_open_thumb_hole_and_grip_notch");
    double[][] outline = {
      {15, 7},
      {15, 4.1},
      {13, 2},
      {10.5, 1},
      {8, 1},
      {5.5, 1.2},
      {3, 2},
      {1.5, 4.3},
      {1, 7},
      {1, 9.9},
      {2, 13},
      {6.4, 10.9},
      {8, 12.5},
      {11, 12},
      {13.2, 11},
      {14.5, 9.7}
    };
    P[] outer = new P[outline.length], inner = new P[outline.length];
    for (int index = 0; index < outline.length; index++) {
      double angle = index * Math.PI / 8;
      outer[index] = board(outline[index][0], outline[index][1], 8);
      inner[index] = new P(8 + .7 * Math.cos(angle), 8 + Math.sin(angle), 8);
    }
    for (int index = 0; index < outer.length; index++) {
      int next = (index + 1) % outer.length;
      for (int side : new int[] {-1, 1}) {
        P shift = new P(0, 0, side * .14);
        surface(
            mesh,
            "palette_face",
            new P[] {
              outer[index].add(shift),
              outer[next].add(shift),
              inner[next].add(shift),
              inner[index].add(shift)
            },
            new P(0, 0, side));
      }
      edge(mesh, outer[index], outer[next], false);
      edge(mesh, inner[index], inner[next], true);
    }
    mesh.group("native_cumulative_paint_state");
    for (int y = 0; y < sprite.getHeight(); y++)
      for (int x = 0; x < sprite.getWidth(); x++) {
        int color = sprite.getRGB(x, y);
        if (color == nativeEmpty.getRGB(x, y)) continue;
        P center = board(x + .5, y + .5, 8.175);
        mesh.box(
            paints.get(color),
            center.x() - .33,
            center.y() - .33,
            8.14,
            center.x() + .33,
            center.y() + .33,
            8.21);
      }
  }

  private static P board(double x, double imageY, double z) {
    return new P(x, 15 - imageY, z);
  }

  private static void edge(EquipmentObjMesh mesh, P a, P b, boolean inward) {
    P normal = new P(b.y() - a.y(), a.x() - b.x(), 0).scale(inward ? -1 : 1);
    surface(
        mesh,
        "palette_edge",
        new P[] {
          a.add(new P(0, 0, -.14)),
          b.add(new P(0, 0, -.14)),
          b.add(new P(0, 0, .14)),
          a.add(new P(0, 0, .14))
        },
        normal);
  }

  private static void surface(EquipmentObjMesh mesh, String material, P[] p, P normal) {
    mesh.quad(material, new P[] {p[0], p[1], p[2], p[2]}, normal, null);
    mesh.quad(material, new P[] {p[0], p[2], p[3], p[3]}, normal, null);
  }

  private static void easel(EquipmentObjMesh mesh) {
    mesh.group("native_easel_central_support");
    easelPart(mesh, -5.5, -31.5, -.8, 1, 26, 1, 5, 24, -5, -.2618, 0);
    mesh.group("native_easel_canvas_crossbars");
    easelPart(mesh, -4.5, -.5, -.5, 9, 1, 1, 0, 16.1, -4, -.2618, 0);
    easelPart(mesh, -3, -14, -.5, 6, 1, 1, 0, 16.75, -4, -.2618, 0);
    mesh.group("native_easel_tripod_legs");
    easelPart(mesh, -.4, -21, -1, .8, 21, 1, 0, 24, 6, .2618, 0);
    easelPart(mesh, 0, -29, -1, 1, 29, 1, 5, 24, -5, -.2618, -.1309);
    easelPart(mesh, -1, -29, -1, 1, 29, 1, -5, 24, -5, -.2618, .1309);
  }

  private static void easelPart(
      EquipmentObjMesh mesh,
      double x,
      double y,
      double z,
      double w,
      double h,
      double d,
      double ox,
      double oy,
      double oz,
      double rx,
      double rz) {
    var transform = new Matrix4d().translation(ox, oy, oz).rotateXYZ(rx, 0, rz);
    P[] corners = new P[8];
    for (int corner = 0; corner < 8; corner++) {
      var p =
          transform.transformPosition(
              new Vector3d(
                  x + ((corner & 1) == 0 ? 0 : w),
                  y + ((corner & 2) == 0 ? 0 : h),
                  z + ((corner & 4) == 0 ? 0 : d)));
      corners[corner] = new P(8 + p.x, 24 - p.y, 8 + p.z);
    }
    int[][] faces = {
      {0, 1, 3, 2}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 5, 7, 3}, {2, 3, 7, 6}, {0, 1, 5, 4}
    };
    P center = new P(0, 0, 0);
    for (P p : corners) center = center.add(p.scale(1.0 / 8));
    for (int[] face : faces) {
      P[] p = {corners[face[0]], corners[face[1]], corners[face[2]], corners[face[3]]};
      P normal = p[0].add(p[1]).add(p[2]).add(p[3]).scale(.25).add(center.scale(-1));
      mesh.quad("easel", p, normal, null);
    }
  }

  private static JsonObject obj(String item, JsonObject display) {
    JsonObject model = new JsonObject();
    model.addProperty("loader", "neoforge:obj");
    model.addProperty("model", DIRECTORY + item + ".obj");
    model.addProperty("automatic_culling", false);
    model.addProperty("flip_v", false);
    model.addProperty("ambientocclusion", false);
    JsonObject textures = new JsonObject();
    textures.addProperty("particle", TEXTURE);
    model.add("textures", textures);
    model.add("display", display);
    return model;
  }

  private static JsonObject paletteDisplay() {
    JsonObject display = transforms(25, -15, 0, .52);
    HeldItemModels.anchorHeld(display, new Vector3f(6.4f, 4.1f, 8), new Vector3f(0, .3f, 0));
    return display;
  }

  private static JsonObject easelDisplay() {
    JsonObject display = transforms(-12, -15, 0, .7);
    HeldItemModels.anchorHeld(display, new Vector3f(8, 13, 10.1f), new Vector3f(0, 0, 0));
    return display;
  }

  private static JsonObject canvasDisplay() {
    return transforms(0, 0, 0, 1);
  }

  private static JsonObject transforms(double x, double y, double z, double scale) {
    JsonObject display = new JsonObject();
    for (String hand :
        List.of(
            "firstperson_righthand",
            "firstperson_lefthand",
            "thirdperson_righthand",
            "thirdperson_lefthand"))
      display.add(
          hand,
          JsonParser.parseString(
                  "{\"rotation\":["
                      + x
                      + ","
                      + y
                      + ","
                      + z
                      + "],\"scale\":["
                      + scale
                      + ","
                      + scale
                      + ","
                      + scale
                      + "]}")
              .getAsJsonObject());
    return display;
  }

  private static BufferedImage materials(List<String> materials, Map<Integer, String> paints) {
    BufferedImage image = new BufferedImage(320, 256, BufferedImage.TYPE_INT_ARGB);
    for (int index = 0; index < materials.size(); index++) {
      String name = materials.get(index);
      int base =
          switch (name) {
            case "palette_face" -> 0xFFE5A767;
            case "palette_edge" -> 0xFF8F3C20;
            case "easel" -> 0xFFC8BB80;
            default ->
                paints.entrySet().stream()
                    .filter(e -> e.getValue().equals(name))
                    .findFirst()
                    .orElseThrow()
                    .getKey();
          };
      for (int y = 0; y < 64; y++)
        for (int x = 0; x < 64; x++) {
          int variation = index < 3 ? ((x / 3 * 7 + y / 4 * 11) % 5 - 2) * 3 : 0;
          image.setRGB(
              index % 5 * 64 + x, index / 5 * 64 + y, EquipmentModels.shaded(base, variation));
        }
    }
    return image;
  }
}
