package dev.aeroscapes.aquatic;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

final class ExpeditionScrollModels {
  private static final String MOD_ID = "aeroscapes_reimagine";
  private static final String MOD = MOD_ID + ":";
  private static final String DIRECTORY = "models/item/expedition";
  private static final double[][] FACE_UV = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};

  private ExpeditionScrollModels() {}

  static void add(Path assets, Map<Path, byte[]> output) throws IOException {
    write(assets.resolve(MOD_ID), output, "deep_breath_scroll", false);
    write(assets.resolve(MOD_ID), output, "distraction_throw_scroll", true);
  }

  private static void write(Path root, Map<Path, byte[]> output, String item, boolean distraction)
      throws IOException {
    var texture = "item/expedition/" + item;
    var folder = root.resolve(DIRECTORY);
    var mesh = new EquipmentObjMesh();
    for (String material : List.of("paper", "cut", "recess", "binding"))
      mesh.projectedField(material);

    double bottomY = distraction ? 4.76 : 4.82, topY = distraction ? 11.23 : 11.14;
    double bottomRadius = distraction ? .72 : .92, topRadius = distraction ? .82 : 1.02;
    P[] bottom = section(bottomY, bottomRadius, distraction),
        lower = section(7.50, .76, distraction),
        upper = section(8.25, .75, distraction),
        top = section(topY, topRadius, distraction);
    mesh.group("parchment");
    join(mesh, "paper", bottom, lower);
    join(mesh, "paper", upper, top);
    rolledEnd(mesh, bottom, bottomY, 1, bottomRadius, distraction);
    rolledEnd(mesh, top, topY, -1, topRadius, distraction);
    lip(mesh, distraction);

    mesh.group("binding");
    var lowerTie = section(7.50, .86, distraction);
    var upperTie = section(8.25, .87, distraction);
    join(mesh, "binding", lowerTie, upperTie);
    cap(mesh, "binding", lowerTie, lower, -1);
    cap(mesh, "binding", upperTie, upper, 1);
    seal(mesh, distraction);
    if (distraction) bindingTail(mesh);

    var mtl = new StringBuilder();
    for (String material : List.of("paper", "cut", "recess", "binding", "seal", "seal_edge")) {
      mtl.append("newmtl ")
          .append(material)
          .append("\nKa 0 0 0\nKd 1 1 1 1\nmap_Kd ")
          .append(MOD)
          .append(material.equals("seal_edge") ? texture + "/binding" : texture + "/" + material)
          .append("\n\n");
    }
    EquipmentModels.text(output, folder.resolve(item).resolve("body.obj"), mesh.obj());
    EquipmentModels.text(
        output,
        folder.resolve(item).resolve("equipment.mtl"),
        mtl.toString().stripTrailing() + "\n");

    var model = new JsonObject();
    model.addProperty("loader", "neoforge:obj");
    model.addProperty("model", MOD + DIRECTORY + "/" + item + "/body.obj");
    model.addProperty("automatic_culling", false);
    model.addProperty("flip_v", false);
    model.addProperty("ambientocclusion", false);
    model.addProperty("render_type", "minecraft:cutout");
    model.add("textures", layer("particle", MOD + "item/" + item));
    model.add("display", display(distraction));
    json(output, folder.resolve(item + ".json"), model);

    var base = new JsonObject();
    base.addProperty("parent", "minecraft:item/generated");
    base.add("textures", layer("layer0", MOD + "item/" + item));
    var wrapper = new JsonObject();
    wrapper.addProperty("loader", "neoforge:separate_transforms");
    wrapper.add("base", base);
    var hands = new JsonObject();
    for (String hand : IronHeldMesh.HANDS)
      hands.add(hand, layer("parent", MOD + "item/expedition/" + item));
    wrapper.add("perspectives", hands);
    json(output, root.resolve("models/item/" + item + ".json"), wrapper);

    var textures = root.resolve("textures").resolve(texture);
    output.put(textures.resolve("paper.png"), EquipmentModels.png(field(0xcbb18a, 27)));
    output.put(textures.resolve("cut.png"), EquipmentModels.png(field(0xa08059, 69)));
    output.put(textures.resolve("recess.png"), EquipmentModels.png(field(0x705440, 11)));
    output.put(
        textures.resolve("binding.png"),
        EquipmentModels.png(field(distraction ? 0x4d535d : 0x376764, 50)));
    output.put(textures.resolve("seal.png"), EquipmentModels.png(sealSprite(distraction)));
    output.put(
        root.resolve("textures/item/" + item + ".png"), EquipmentModels.png(icon(distraction)));
  }

  private static P[] section(double y, double radius, boolean distraction) {
    P[] result = new P[distraction ? 8 : 10];
    for (int side = 0; side < result.length; side++) {
      double angle = (side + 0.5) * 2 * Math.PI / result.length;
      double bulge = distraction ? 1 : 1 + .065 * Math.cos(2 * angle + y * .26);
      result[side] =
          new P(
              8 + radius * bulge * Math.cos(angle), y, 8 + radius * bulge * .89 * Math.sin(angle));
    }
    return result;
  }

  private static void join(EquipmentObjMesh mesh, String material, P[] lower, P[] higher) {
    for (int side = 0; side < lower.length; side++) {
      int next = (side + 1) % lower.length;
      var outside = lower[side].add(lower[next]).scale(.5).add(new P(-8, -lower[side].y(), -8));
      mesh.quad(
          material,
          new P[] {lower[side], lower[next], higher[next], higher[side]},
          outside,
          FACE_UV);
    }
  }

  private static void cap(
      EquipmentObjMesh mesh, String material, P[] outside, P[] inside, int face) {
    for (int side = 0; side < outside.length; side++) {
      int next = (side + 1) % outside.length;
      mesh.quad(
          material,
          new P[] {outside[side], outside[next], inside[next], inside[side]},
          new P(0, face, 0),
          FACE_UV);
    }
  }

  private static void rolledEnd(
      EquipmentObjMesh mesh, P[] end, double y, int inward, double radius, boolean distraction) {
    P[] outerCurl = section(y + .045 * inward, radius * .74, distraction);
    P[] innerCurl = section(y + .18 * inward, radius * .52, distraction);
    P[] recess = section(y + .49 * inward, radius * .33, distraction);
    cap(mesh, "cut", end, outerCurl, -inward);
    cap(mesh, "paper", outerCurl, innerCurl, -inward);
    cap(mesh, "recess", innerCurl, recess, -inward);
    P center = new P(8, y + .51 * inward, 8);
    for (int side = 0; side < recess.length; side++) {
      int next = (side + 1) % recess.length;
      mesh.quad(
          "recess",
          new P[] {recess[side], recess[next], center, center},
          new P(0, -inward, 0),
          FACE_UV);
    }
  }

  private static void lip(EquipmentObjMesh mesh, boolean distraction) {
    mesh.group("overlapping_paper");
    if (distraction) {
      mesh.plate(
          "paper",
          "cut",
          List.of(
              new P(8.30, 8.23, 8.53),
              new P(8.64, 8.25, 8.70),
              new P(8.90, 10.92, 8.87),
              new P(8.29, 10.88, 8.65)),
          .08);
      return;
    }
    List<P> edge =
        List.of(
            new P(7.73, 8.28, 8.67),
            new P(7.86, 9.92, 8.77),
            new P(7.83, 10.92, 8.81),
            new P(7.11, 10.65, 8.95),
            new P(7.31, 9.92, 8.86),
            new P(7.65, 8.28, 8.70));
    mesh.plate("paper", "cut", edge, .09);
  }

  private static void seal(EquipmentObjMesh mesh, boolean distraction) {
    if (distraction) {
      mesh.group("ochre_strike_seal");
      mesh.plate(
          "seal",
          "seal_edge",
          List.of(
              new P(8.08, 7.83, 8.77),
              new P(8.69, 8.23, 8.77),
              new P(8.21, 8.87, 8.77),
              new P(7.68, 8.29, 8.77)),
          .24);
      return;
    }
    mesh.group("teal_wax_wave_seal");
    List<P> points = new ArrayList<>();
    for (int index = 0; index < 8; index++) {
      double angle = (index + .5) * Math.PI / 4;
      points.add(new P(8.17 + .53 * Math.cos(angle), 8.38 + .50 * Math.sin(angle), 8.79));
    }
    mesh.plate("seal", "seal_edge", points, .24);
  }

  private static void bindingTail(EquipmentObjMesh mesh) {
    mesh.group("binding_tail");
    mesh.plate(
        "binding",
        "binding",
        List.of(
            new P(8.41, 7.85, 8.60),
            new P(8.73, 7.87, 8.60),
            new P(8.92, 6.72, 8.67),
            new P(8.74, 6.89, 8.67),
            new P(8.61, 6.75, 8.67)),
        .07);
  }

  private static JsonObject display(boolean distraction) {
    var result = new JsonObject();
    for (String hand : IronHeldMesh.HANDS) {
      boolean first = hand.startsWith("firstperson");
      boolean left = hand.endsWith("lefthand");
      var transform = new JsonObject();
      transform.add(
          "rotation",
          IronHeldMesh.vector(
              first ? -18 : 28, first ? -19 : -28, first ? (distraction ? -26 : -17) : -29));
      double scale = first ? .70 : .62;
      transform.add("scale", IronHeldMesh.vector(scale, scale, scale));
      var perspective = new JsonObject();
      perspective.add(hand, transform);
      HeldItemModels.anchorHeld(
          perspective,
          new Vector3f(left ? 7.76f : 8.24f, 7.10f, 8.16f),
          new Vector3f(0, first ? 3.5f : .75f, 0));
      result.add(hand, transform);
    }
    return result;
  }

  private static BufferedImage field(int base, int seed) {
    var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        int fiber = Math.floorMod((x / 2 * 67) ^ (y * 19) ^ seed, 13) - 6;
        image.setRGB(x, y, EquipmentModels.shaded(0xff000000 | base, fiber));
      }
    }
    return image;
  }

  private static BufferedImage sealSprite(boolean distraction) {
    var image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 12; y++) {
      for (int x = 0; x < 12; x++) {
        int tone;
        if (distraction) {
          tone = y < 2 || x < 2 ? 0x815024 : 0xa96d37;
          if ((x - 7) * (x - 7) + (y - 4) * (y - 4) <= 4 || x >= 3 && x <= 6 && y == 10 - x)
            tone = 0xeae3d7;
        } else {
          tone = y < 2 || x < 2 ? 0x286a68 : 0x3b8683;
          if (y == 7 - (int) Math.round(2 * Math.sin(x * .68)) && x >= 2 && x < 10) tone = 0xbddccb;
        }
        image.setRGB(x, y, 0xff000000 | tone);
      }
    }
    return image;
  }

  private static BufferedImage icon(boolean distraction) {
    var pixels =
        distraction
            ? List.of(
                "................",
                "...ddd..........",
                "..dopddd........",
                "..dpdcppd.......",
                "...dppphd.......",
                "....dppphd......",
                ".....dpsssd.....",
                ".....dsaaod.....",
                ".....dawaaod....",
                ".....dsaoaopd...",
                "......dsspphd...",
                ".......dpphpcd..",
                "........dpocdd..",
                ".........dddd...",
                "................",
                "................")
            : List.of(
                "................",
                "..........ddd...",
                "........ddpocd..",
                ".......dpphpod..",
                "......dphpocd...",
                ".....dphppcd....",
                "....dphpttd.....",
                "...dphptwtcd....",
                "..dphppttwtd....",
                "..dppccwtctd....",
                "..dphptttcd.....",
                "..dophppcd......",
                "...dccpcd.......",
                "....ddd.........",
                "................",
                "................");
    var palette =
        distraction
            ? Map.of(
                'd', 0x584333, 'o', 0x92653d, 'c', 0xb89568, 'p', 0xd4b687, 'h', 0xf1d4a4, 's',
                0x4d535d, 'a', 0xb3773f, 'w', 0xf3e6d3)
            : Map.of(
                'd', 0x584333, 'o', 0x9d7955, 'c', 0xb6986d, 'p', 0xd9bd8c, 'h', 0xf2d9ab, 't',
                0x37827d, 'w', 0xb8ded1);
    var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 16; y++) {
      for (int x = 0; x < 16; x++) {
        Integer color = palette.get(pixels.get(y).charAt(x));
        if (color != null) image.setRGB(x, y, 0xff000000 | color);
      }
    }
    return image;
  }

  private static JsonObject layer(String key, String value) {
    var object = new JsonObject();
    object.addProperty(key, value);
    return object;
  }

  private static void json(Map<Path, byte[]> output, Path path, JsonObject object) {
    EquipmentModels.text(
        output, path, new GsonBuilder().setPrettyPrinting().create().toJson(object) + "\n");
  }
}
