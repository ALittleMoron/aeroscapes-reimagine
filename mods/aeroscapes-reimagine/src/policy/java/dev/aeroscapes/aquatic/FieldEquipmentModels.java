package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** Held forms for field supplies. Item state and non-hand presentation remain provider-owned. */
final class FieldEquipmentModels {
  static final List<String> ROOTS =
      List.of(
          "legendarysurvivaloverhaul:canteen",
          "legendarysurvivaloverhaul:large_canteen",
          "legendarysurvivaloverhaul:medkit",
          "quark:seed_pouch",
          "quark:bottled_cloud",
          "tide:fishing_journal",
          "betterarcheology:soul_totem",
          "betterarcheology:torrent_totem",
          "galosphere:mining_pouch",
          "galosphere:bottle_of_spectre",
          "minecraft:bucket",
          "minecraft:water_bucket",
          "minecraft:lava_bucket",
          "brewinandchewin:tankard",
          "brewinandchewin:mead",
          "brewinandchewin:kombucha",
          "comforts:sleeping_bag_red",
          "spawn:fly_bottle",
          "hybrid_aquatic:bell_buoy",
          "hybrid_aquatic:message_in_a_bottle");

  private static final String DIRECTORY = "models/item/equipment/field/";
  private static final double[][] UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
  private static final double[][] TRIANGLE_UV = {{.5, .5}, {0, 0}, {1, 0}, {1, 0}};

  private FieldEquipmentModels() {}

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Map<String, JsonObject> models = new LinkedHashMap<>();
    for (String root : ROOTS) addState(assets, output, models, root, root);
    HeldItemModels.add(assets, output, models);
    JsonObject profiles = new JsonObject();
    for (String root : ROOTS) {
      float lift =
          switch (root) {
            case "minecraft:bucket", "minecraft:water_bucket", "minecraft:lava_bucket" -> .52f;
            case "legendarysurvivaloverhaul:medkit" -> .30f;
            default -> .21f;
          };
      JsonObject arm = new JsonObject();
      arm.addProperty("scale", 1);
      arm.addProperty("offY", -lift);
      JsonObject sides = new JsonObject();
      sides.add("right", arm);
      sides.add("left", arm.deepCopy());
      JsonObject item = new JsonObject();
      item.add("armTransforms", sides);
      profiles.add(root, item);
    }
    JsonObject compat = new JsonObject();
    compat.add("itemSpecific", profiles);
    EquipmentModels.text(
        output,
        assets.resolve("minecraft/punchy/compat/aeroscapes_field_equipment.json"),
        compat.toString() + "\n");
  }

  private static void addState(
      Path assets, Map<Path, byte[]> output, Map<String, JsonObject> models, String root, String id)
      throws IOException {
    if (models.containsKey(id)) return;
    JsonObject nativeModel = HeldItemModels.nativeModel(id);
    String texture =
        NativeEquipmentModels.inherited(id).getAsJsonObject("textures").get("layer0").getAsString();
    var item = new FieldMesh(texture);
    Pose pose =
        switch (root) {
          case "legendarysurvivaloverhaul:canteen", "legendarysurvivaloverhaul:large_canteen" ->
              canteen(item, root.contains("large"), id.endsWith("_purified"));
          case "legendarysurvivaloverhaul:medkit" -> medkit(item);
          case "quark:seed_pouch" -> pouch(item, true, !id.equals(root));
          case "galosphere:mining_pouch" -> pouch(item, false, !id.equals(root));
          case "tide:fishing_journal" -> journal(item);
          case "betterarcheology:soul_totem" -> soulTotem(item);
          case "betterarcheology:torrent_totem" -> torrentTotem(item);
          case "minecraft:bucket", "minecraft:water_bucket", "minecraft:lava_bucket" ->
              bucket(item, root);
          case "brewinandchewin:tankard", "brewinandchewin:mead", "brewinandchewin:kombucha" ->
              tankard(item, root);
          case "comforts:sleeping_bag_red" -> bedroll(item);
          case "hybrid_aquatic:bell_buoy" -> buoy(item);
          default -> bottle(item, root);
        };
    String[] key = id.split(":", 2);
    String prefix = key[0] + ":" + DIRECTORY + key[1] + "/";
    Path directory = assets.resolve(key[0]).resolve(DIRECTORY).resolve(key[1]);
    EquipmentModels.text(output, directory.resolve("body.obj"), item.body.obj());
    EquipmentModels.text(
        output,
        directory.resolve("equipment.mtl"),
        item.materials.toString().stripTrailing() + "\n");
    JsonObject model = obj(prefix + "body.obj", texture, "minecraft:cutout");
    if (item.glass != null) {
      EquipmentModels.text(output, directory.resolve("glass.obj"), item.glass.obj());
      JsonObject children = new JsonObject();
      children.add("body", model);
      children.add("glass", obj(prefix + "glass.obj", texture, "minecraft:translucent"));
      model = new JsonObject();
      model.addProperty("loader", "neoforge:composite");
      model.add("children", children);
      JsonArray order = new JsonArray();
      order.add("body");
      order.add("glass");
      model.add("item_render_order", order);
      model.add("textures", children.getAsJsonObject("body").get("textures").deepCopy());
    }
    model.add("display", display(pose, root));
    models.put(id, model);
    if (nativeModel.has("overrides")) {
      for (var variant : nativeModel.getAsJsonArray("overrides")) {
        String target = variant.getAsJsonObject().get("model").getAsString();
        if (!target.contains(":")) target = "minecraft:" + target;
        addState(assets, output, models, root, target.replace(":item/", ":"));
      }
    }
  }

  private static JsonObject obj(String model, String texture, String renderType) {
    JsonObject json = new JsonObject();
    json.addProperty("loader", "neoforge:obj");
    json.addProperty("model", model);
    json.addProperty("automatic_culling", false);
    json.addProperty("emissive_ambient", false);
    json.addProperty("flip_v", false);
    json.addProperty("ambientocclusion", false);
    json.addProperty("render_type", renderType);
    JsonObject textures = new JsonObject();
    textures.addProperty("particle", texture);
    json.add("textures", textures);
    return json;
  }

  private record Pose(float x, float y, float z, float tilt, float yaw, float scale) {}

  private static JsonObject display(Pose pose, String root) {
    JsonObject transforms = new JsonObject();
    JsonObject journalRight = new JsonObject();
    boolean journal = root.equals("tide:fishing_journal");
    for (String perspective : List.of("firstperson", "thirdperson")) {
      for (String hand : List.of("righthand", "lefthand")) {
        boolean right = hand.equals("righthand");
        JsonObject transform = new JsonObject();
        // The cup must extend inward from its one-sided handle in either hand.
        float yaw = root.startsWith("brewinandchewin:") && !right ? 135 : pose.yaw;
        transform.add("rotation", vector(pose.tilt, yaw, right && !journal ? 5 : -5));
        transform.add("scale", vector(pose.scale, pose.scale, pose.scale));
        String context = perspective + "_" + hand;
        transforms.add(context, transform);
        if (journal && right) journalRight.add(context, transform);
      }
    }
    HeldItemModels.anchorHeld(
        transforms, new Vector3f(pose.x, pose.y, pose.z), new Vector3f(0, -1.2f, 1.2f));
    if (journal) {
      // Mirror the edge held by the fingers while retaining the left-hand placement.
      HeldItemModels.anchorHeld(
          journalRight, new Vector3f(10.15f, pose.y, pose.z), new Vector3f(0, -1.2f, 1.2f));
    }
    return transforms;
  }

  private static JsonArray vector(float x, float y, float z) {
    JsonArray vector = new JsonArray();
    vector.add(x);
    vector.add(y);
    vector.add(z);
    return vector;
  }

  private static final class FieldMesh {
    final EquipmentObjMesh body = new EquipmentObjMesh();
    final StringBuilder materials = new StringBuilder();
    final BufferedImage sprite;
    final String texture;
    EquipmentObjMesh glass;

    FieldMesh(String texture) throws IOException {
      this.texture = texture;
      sprite = NativeEquipmentModels.image(texture);
    }

    void paint(String material, int color) {
      paint(body, material, color, 1);
    }

    void paint(EquipmentObjMesh mesh, String material, int color, double alpha) {
      int closest = Integer.MAX_VALUE;
      int column = -1;
      int row = -1;
      for (int y = 0; y < sprite.getHeight(); y++) {
        for (int x = 0; x < sprite.getWidth(); x++) {
          int pixel = sprite.getRGB(x, y);
          if (pixel >>> 24 != 255) continue;
          int distance = colorDistance(pixel, color);
          if (distance < closest) {
            closest = distance;
            column = x;
            row = y;
          }
        }
      }
      if (column < 0) throw new IllegalArgumentException("No opaque paint in " + texture);
      mesh.region(
          material,
          (column + .15) / sprite.getWidth(),
          (row + .15) / sprite.getHeight(),
          (column + .85) / sprite.getWidth(),
          (row + .85) / sprite.getHeight());
      material(material, texture, alpha);
    }

    void material(String material, String texture, double alpha) {
      materials
          .append("newmtl ")
          .append(material)
          .append("\nKa 0 0 0\nKd 1 1 1 ")
          .append(alpha)
          .append("\nmap_Kd ")
          .append(texture)
          .append("\n\n");
    }

    private static int colorDistance(int pixel, int color) {
      int red = (pixel >> 16 & 255) - (color >> 16 & 255);
      int green = (pixel >> 8 & 255) - (color >> 8 & 255);
      int blue = (pixel & 255) - (color & 255);
      return red * red + green * green + blue * blue;
    }
  }

  private static P[] ring(double x, double y, double z, double width, double depth) {
    return EquipmentObjMesh.octagonalRing(x, y, z, width, depth);
  }

  private static void wall(
      EquipmentObjMesh mesh, String material, P[] lower, P[] upper, boolean inner) {
    P middle = lower[0].add(lower[4]).scale(.5);
    for (int side = 0; side < 8; side++) {
      int next = (side + 1) % 8;
      P outward = lower[side].add(lower[next]).scale(.5).add(middle.scale(-1));
      mesh.quad(
          material,
          new P[] {lower[side], lower[next], upper[next], upper[side]},
          outward.scale(inner ? -1 : 1),
          UV);
    }
  }

  private static void rim(EquipmentObjMesh mesh, String material, P[] outer, P[] inner) {
    for (int side = 0; side < 8; side++) {
      int next = (side + 1) % 8;
      mesh.quad(
          material,
          new P[] {outer[side], outer[next], inner[next], inner[side]},
          new P(0, 1, 0),
          UV);
    }
  }

  private static void disk(EquipmentObjMesh mesh, String material, P[] edge, int direction) {
    P center = edge[0].add(edge[4]).scale(.5);
    for (int side = 0; side < 8; side++) {
      P next = edge[(side + 1) % 8];
      mesh.quad(
          material, new P[] {center, edge[side], next, next}, new P(0, direction, 0), TRIANGLE_UV);
    }
  }

  private static Pose canteen(FieldMesh item, boolean large, boolean purified) {
    item.paint("leather", large ? 0x437c25 : 0x8b482c);
    item.paint("edge", large ? 0x274619 : 0x462719);
    item.paint("binding", 0x3c2f2f);
    item.paint("cap", purified ? 0x319bd1 : 0x6b6b6b);
    var mesh = item.body;
    double width = large ? 5.7 : 4.5;
    double depth = large ? 3.15 : 2.5;
    double shoulder = large ? 12.4 : 11.2;
    P[] foot = ring(7.8, 3, 8, width * .55, depth * .54);
    P[] belly = ring(7.8, 6, 8, width, depth);
    P[] band = ring(7.8, 7, 8, width * 1.01, depth * 1.04);
    P[] topBand = ring(7.85, 8.25, 8, width * .99, depth * 1.05);
    P[] top = ring(8, shoulder, 8, width * .63, depth * .69);
    P[] mouth = ring(8, shoulder + .84, 8, 1.35, 1.17);
    disk(mesh, "edge", foot, -1);
    wall(mesh, "edge", foot, belly, false);
    wall(mesh, "leather", belly, band, false);
    wall(mesh, "binding", band, topBand, false);
    wall(mesh, "leather", topBand, top, false);
    wall(mesh, "leather", top, mouth, false);
    for (int side : new int[] {-1, 1}) {
      double[] heights = {3.18, 6.03, 8.18, shoulder - .18};
      double[] depths = {.55, .996, 1.041, .695};
      for (int segment = 0; segment < heights.length - 1; segment++) {
        P bottom = new P(7.6, heights[segment], 8 + side * (depth * depths[segment] + .10));
        P upper = new P(7.6, heights[segment + 1], 8 + side * (depth * depths[segment + 1] + .10));
        mesh.quad(
            "binding",
            new P[] {
              bottom.add(new P(-.5, 0, 0)), bottom.add(new P(.5, 0, 0)),
              upper.add(new P(.5, 0, 0)), upper.add(new P(-.5, 0, 0))
            },
            new P(0, 0, side),
            UV);
      }
    }
    P[] capBottom = ring(8, shoulder + .72, 8, 1.48, 1.31);
    P[] capTop = ring(8, shoulder + 1.70, 8, 1.48, 1.31);
    wall(mesh, "cap", capBottom, mouth, true);
    wall(mesh, "cap", capBottom, capTop, false);
    disk(mesh, "cap", capTop, 1);
    return new Pose(7.4f, 5.4f, 8.7f, -14, -29, large ? .74f : .67f);
  }

  private static Pose pouch(FieldMesh item, boolean seed, boolean filled) {
    item.paint("body", seed ? 0xc18d4d : 0x3b3b43);
    item.paint("fold", seed ? 0x7d4f33 : 0x25252f);
    item.paint("binding", seed ? 0x50631e : 0x956f45);
    item.paint("lip", seed ? 0xe2a55a : 0x5b6061);
    var mesh = item.body;
    double width = filled ? 4.6 : 4.35;
    double depth = filled ? 3.35 : 2.7;
    double top = filled ? 11.6 : 9.1;
    P[] bottom = ring(7.9, 3.15, 8, width * .59, depth * .63);
    P[] fold = clothRing(7.95, 4.16, width * .84, depth * .89, .36);
    P[] belly = clothRing(8, filled ? 6.55 : 5.85, width, depth, .52);
    double neck = filled ? .46 : .94;
    P[] tieBase = ring(8.25, top - 1.42, 8, width * (neck + .05), depth * (neck + .05));
    P[] throat = ring(8.25, top - .95, 8, width * neck, depth * neck);
    P[] lip = clothRing(8.25, top, width * (filled ? .59 : 1), depth * (filled ? .57 : .98), .74);
    P[] opening =
        clothRing(8.25, top - .04, width * (filled ? .48 : .88), depth * (filled ? .45 : .86), .74);
    disk(mesh, "fold", bottom, -1);
    drape(mesh, "fold", bottom, fold, false);
    drape(mesh, "body", fold, belly, false);
    drape(mesh, "body", belly, tieBase, false);
    wall(mesh, "binding", tieBase, throat, false);
    drape(mesh, "lip", throat, lip, false);
    P[] lining = ring(8.25, filled ? top - 1.15 : 4.5, 8, width * .46, depth * .42);
    drape(mesh, "fold", lining, opening, true);
    for (int side = 0; side < 8; side++) {
      int next = (side + 1) % 8;
      clothPanel(mesh, "lip", lip[side], lip[next], opening[next], opening[side], new P(0, 1, 0));
    }
    disk(mesh, "fold", lining, 1);
    P knot = new P(8.25 - width * neck, top - 1.18, 8);
    mesh.beam("binding", knot.add(new P(.23, 0, 0)), knot.add(new P(-.45, .12, .12)), .36, 4);
    mesh.beam("binding", knot, knot.add(new P(-.86, -.89, .32)), .21, 4);
    mesh.beam("binding", knot.add(new P(-.2, .11, .05)), knot.add(new P(-.93, .55, -.25)), .20, 4);
    return new Pose(7.7f, 5.1f, 8.8f, 14, -26, seed ? .65f : .69f);
  }

  private static P[] clothRing(double x, double y, double width, double depth, double height) {
    P[] points = ring(x, y, 8, width, depth);
    double[] gathers = {-.16, .19, -.55, .45, -.34, .54, -.38, .25};
    for (int i = 0; i < points.length; i++) {
      P p = points[i];
      double compression = 1 + gathers[i] * .24;
      points[i] =
          new P(
              x + (p.x() - x) * compression,
              y + height * gathers[i],
              8 + (p.z() - 8) * compression);
    }
    return points;
  }

  private static void drape(EquipmentObjMesh mesh, String material, P[] a, P[] b, boolean inner) {
    for (int side = 0; side < 8; side++) {
      int next = (side + 1) % 8;
      P edge = a[next].add(a[side].scale(-1));
      P outward = new P(edge.z(), 0, -edge.x()).scale(inner ? -1 : 1);
      clothPanel(mesh, material, a[side], a[next], b[next], b[side], outward);
    }
  }

  private static void clothPanel(
      EquipmentObjMesh mesh, String material, P a, P b, P c, P d, P outward) {
    mesh.quad(
        material, new P[] {a, b, c, c}, outward, new double[][] {{0, 0}, {1, 0}, {1, 1}, {1, 1}});
    mesh.quad(
        material, new P[] {a, c, d, d}, outward, new double[][] {{0, 0}, {1, 1}, {0, 1}, {0, 1}});
  }

  private static Pose journal(FieldMesh item) {
    item.paint("cloth", 0x253a5e);
    item.paint("paper", 0xe7d5b3);
    item.paint("stamp", 0x4f8fba);
    item.paint("stamp_dark", 0x3c5e8b);
    item.paint("wire", 0xcf573c);
    var mesh = item.body;
    mesh.box("paper", 3.78, 3.45, 6.87, 11.58, 13.41, 9.65);
    mesh.box("cloth", 3.5, 3.15, 9.58, 11.85, 13.8, 10.17);
    mesh.box("cloth", 3.5, 3.16, 6.49, 11.85, 13.79, 6.94);
    for (double x : new double[] {4.42, 6.59, 8.76, 10.93}) {
      mesh.tube(
          "wire",
          List.of(
              new P(x, 12.92, 9.78),
              new P(x, 13.88, 10.20),
              new P(x, 14.56, 9.74),
              new P(x, 14.85, 8.38),
              new P(x, 14.54, 6.91),
              new P(x, 13.87, 6.46),
              new P(x, 12.96, 6.74)),
          .24,
          4,
          3);
    }
    fishMark(mesh, "stamp", 7.34, 10.54, 1.02);
    fishMark(mesh, "stamp_dark", 9.27, 8.92, .72);
    fishMark(mesh, "stamp", 6.03, 7.44, .64);
    return new Pose(5.2f, 5.15f, 8.8f, -18, -19, .68f);
  }

  private static void fishMark(
      EquipmentObjMesh mesh, String material, double x, double y, double size) {
    mesh.plate(
        material,
        material,
        List.of(
            fishPoint(x, y, size, -1.48, .08),
            fishPoint(x, y, size, -.94, .53),
            fishPoint(x, y, size, -.35, .58),
            fishPoint(x, y, size, .06, .89),
            fishPoint(x, y, size, .22, .52),
            fishPoint(x, y, size, .90, .20),
            fishPoint(x, y, size, 1.57, .68),
            fishPoint(x, y, size, 1.38, .02),
            fishPoint(x, y, size, 1.57, -.62),
            fishPoint(x, y, size, .88, -.19),
            fishPoint(x, y, size, .14, -.52),
            fishPoint(x, y, size, -.93, -.40)),
        .034);
  }

  private static P fishPoint(double x, double y, double size, double along, double across) {
    return new P(x + along * size, y + across * size, 10.17);
  }

  private static Pose medkit(FieldMesh item) {
    item.paint("shell", 0xcecece);
    item.paint("lid", 0xfbfbfb);
    item.paint("rim", 0x464646);
    item.paint("red", 0xd13131);
    var mesh = item.body;
    P[] foot = ring(8, 3.8, 8, 4.8, 2.2);
    P[] sides = ring(8, 4.4, 8, 5.5, 2.6);
    P[] seam = ring(8, 8.25, 8, 5.5, 2.6);
    P[] lid = ring(8, 8.85, 8, 5.47, 2.59);
    P[] shoulders = ring(8, 10.15, 8, 4.75, 2.25);
    disk(mesh, "rim", foot, -1);
    wall(mesh, "rim", foot, sides, false);
    wall(mesh, "shell", sides, seam, false);
    wall(mesh, "rim", seam, lid, false);
    wall(mesh, "lid", lid, shoulders, false);
    disk(mesh, "lid", shoulders, 1);
    mesh.box("rim", 5.4, 10.03, 7.35, 6.18, 12.08, 8.65);
    mesh.box("rim", 9.82, 10.02, 7.35, 10.6, 12.12, 8.65);
    mesh.box("rim", 5.55, 11.75, 7.28, 10.46, 12.58, 8.72);
    // The octagonal case has a sloping front face at the cross.
    mesh.box("lid", 6.3, 5.5, 9.55, 9.7, 8.1, 10.53);
    mesh.box("red", 7.67, 5.82, 10.51, 8.33, 7.78, 10.63);
    mesh.box("red", 6.97, 6.5, 10.52, 9.02, 7.09, 10.64);
    return new Pose(8, 12.15f, 8, 21, -18, .7f);
  }

  private static Pose soulTotem(FieldMesh item) {
    item.paint("gold", 0xeadb84);
    item.paint("shadow", 0xc58742);
    item.paint("face", 0xf8eea5);
    item.paint("eye", 0x01a7ac);
    item.paint("mouth", 0xa05b23);
    var mesh = item.body;
    spirit(mesh, 5.72, 3.14, 8.06, 1, 8.53);
    spirit(mesh, 10.62, 6.48, 7.83, .79, 7.50);
    return new Pose(6.6f, 5.1f, 8, -9, -24, .69f);
  }

  private static void spirit(
      EquipmentObjMesh mesh, double x, double y, double z, double size, double tailX) {
    P[] tip = spiritRing(tailX, y, z - .09, .28 * size, .28 * size);
    P[] tail = spiritRing(x + (tailX - x) * .43, y + 1.18 * size, z, 1.04 * size, .79 * size);
    P[] chin = spiritRing(x - .13 * size, y + 2.72 * size, z, 1.37 * size, 1.14 * size);
    P[] cheeks = spiritRing(x, y + 3.54 * size, z, 2.05 * size, 1.55 * size);
    P[] brow = spiritRing(x, y + 5.36 * size, z, 1.91 * size, 1.55 * size);
    P[] crown = spiritRing(x + .11 * size, y + 6.13 * size, z, 1.17 * size, 1.14 * size);
    disk(mesh, "shadow", tip, -1);
    wall(mesh, "shadow", tip, tail, false);
    wall(mesh, "gold", tail, chin, false);
    wall(mesh, "gold", chin, cheeks, false);
    wall(mesh, "face", cheeks, brow, false);
    wall(mesh, "gold", brow, crown, false);
    disk(mesh, "face", crown, 1);
    double front = z + 1.55 * size;
    for (int side : new int[] {-1, 1}) {
      double eye = x + side * .70 * size;
      mesh.box(
          "eye",
          eye - .24 * size,
          y + 4.62 * size,
          front - .06,
          eye + .24 * size,
          y + 5.09 * size,
          front + .035);
    }
    mesh.box(
        "mouth",
        x - .34 * size,
        y + 3.79 * size,
        front - .048,
        x + .34 * size,
        y + 4.25 * size,
        front + .03);
  }

  private static P[] spiritRing(double x, double y, double z, double width, double depth) {
    return new P[] {
      new P(x + width, y, z + .42 * depth),
      new P(x + .64 * width, y, z + depth),
      new P(x - .64 * width, y, z + depth),
      new P(x - width, y, z + .42 * depth),
      new P(x - width, y, z - .42 * depth),
      new P(x - .64 * width, y, z - depth),
      new P(x + .64 * width, y, z - depth),
      new P(x + width, y, z - .42 * depth)
    };
  }

  private static Pose torrentTotem(FieldMesh item) {
    item.paint("gold", 0xeadb84);
    item.paint("edge", 0xa05b23);
    item.paint("fin", 0xd1a75d);
    item.paint("eye", 0x1dc2d1);
    item.paint("eye_shadow", 0x1f96b1);
    var mesh = item.body;
    mesh.shell(
        "gold",
        new P[][] {
          torrentSection(2.76, 9.91, 9, .35, .60),
          torrentSection(4.30, 10.13, 12, .61, 1.00),
          torrentSection(5.93, 10.79, 3, 1.50, 1.78),
          torrentSection(7.64, 10.91, -13, 1.78, 2.05),
          torrentSection(9.39, 10.10, -45, 1.77, 1.94),
          torrentSection(10.69, 8.37, -72, 1.44, 1.48),
          torrentSection(10.87, 6.34, -100, 1.06, 1.00),
          torrentSection(9.67, 4.80, -142, .64, .72),
          torrentSection(8.00, 4.46, -169, .42, .52)
        });
    mesh.plate(
        "fin",
        "edge",
        List.of(
            new P(7.82, 11.83, 7.87),
            new P(9.37, 13.39, 7.87),
            new P(11.04, 13.03, 7.87),
            new P(12.67, 12.14, 7.87),
            new P(10.89, 12.07, 7.87),
            new P(10.21, 10.83, 7.87)),
        1.03);
    mesh.plate(
        "fin",
        "edge",
        List.of(
            new P(8.92, 5.17, 8.08),
            new P(7.45, 5.31, 8.08),
            new P(5.94, 4.79, 8.08),
            new P(7.36, 4.14, 8.08),
            new P(7.00, 3.04, 8.08),
            new P(8.67, 3.48, 8.08),
            new P(9.55, 4.49, 8.08)),
        1.02);
    for (int side : new int[] {-1, 1}) {
      mesh.plate(
          "fin",
          "edge",
          List.of(
              new P(8.04, 9.29, 8 + side * 1.50),
              new P(8.76, 8.39, 8 + side * 2.66),
              new P(9.83, 7.19, 8 + side * 2.45),
              new P(9.40, 9.03, 8 + side * 1.34)),
          .58);
      double eye = 8 + side * 1.73;
      mesh.box("eye_shadow", 5.58, 10.28, eye - .19, 6.26, 10.93, eye + .19);
      double tint = eye + side * .17;
      mesh.box("eye", 5.62, 10.45, tint - .038, 6.04, 10.91, tint + .038);
    }
    return new Pose(9.34f, 5.73f, 8, -7, -25, .71f);
  }

  private static P[] torrentSection(
      double x, double y, double direction, double radius, double depth) {
    double tangent = Math.toRadians(direction);
    P[] section = new P[8];
    for (int side = 0; side < section.length; side++) {
      double angle = side * Math.PI / 4;
      double height = Math.cos(angle) * radius;
      section[side] =
          new P(
              x - Math.sin(tangent) * height,
              y + Math.cos(tangent) * height,
              8 + Math.sin(angle) * depth);
    }
    return section;
  }

  private static void vessel(
      EquipmentObjMesh mesh,
      double x,
      double bottom,
      double top,
      double radius,
      double wallWidth,
      String outer,
      String inner,
      String edge,
      String fill) {
    double floor = bottom + .62;
    P[] foot = ring(x, bottom, 8, radius * .67, radius * .67);
    P[] lower = ring(x, bottom + .48, 8, radius * .76, radius * .76);
    P[] shoulder = ring(x, top - .64, 8, radius, radius);
    P[] lip = ring(x, top, 8, radius, radius);
    P[] opening = ring(x, top, 8, radius - wallWidth, radius - wallWidth);
    P[] inside = ring(x, floor, 8, radius * .76 - wallWidth, radius * .76 - wallWidth);
    disk(mesh, edge, foot, -1);
    wall(mesh, edge, foot, lower, false);
    wall(mesh, outer, lower, shoulder, false);
    wall(mesh, edge, shoulder, lip, false);
    rim(mesh, edge, lip, opening);
    wall(mesh, inner, inside, opening, true);
    disk(mesh, inner, inside, 1);
    if (fill != null) {
      double height = top - .77;
      double fillRadius = radius - wallWidth - .24 * radius * (top - height) / (top - floor);
      disk(mesh, fill, ring(x, height, 8, fillRadius, fillRadius), 1);
    }
  }

  private static Pose bucket(FieldMesh item, String id) {
    item.paint("steel", 0xa8a8a8);
    item.paint("inside", 0x545454);
    item.paint("rim", 0x727272);
    item.paint("handle", 0x969696);
    String fill = null;
    if (!id.equals("minecraft:bucket")) {
      item.paint("liquid", id.equals("minecraft:water_bucket") ? 0x345fda : 0xe38c3f);
      item.paint("ripple", id.equals("minecraft:water_bucket") ? 0x5a82f3 : 0xe4d25c);
      fill = "liquid";
    }
    var mesh = item.body;
    vessel(mesh, 8, 1.15, 9.8, 5.7, .65, "steel", "inside", "rim", fill);
    mesh.beam("rim", new P(1.7, 8.6, 8), new P(2.55, 8.6, 8), .7, 6);
    mesh.beam("rim", new P(13.45, 8.6, 8), new P(14.3, 8.6, 8), .7, 6);
    mesh.tube(
        "handle",
        List.of(
            new P(1.85, 8.6, 8),
            new P(2.3, 11.55, 8),
            new P(4.75, 13.8, 8),
            new P(8, 14.25, 8),
            new P(11.25, 13.8, 8),
            new P(13.7, 11.55, 8),
            new P(14.15, 8.6, 8)),
        .34,
        4,
        4);
    if (fill != null) {
      mesh.box("ripple", 5.45, 9.033, 6.23, 8.6, 9.045, 6.84);
      mesh.box("ripple", 8.22, 9.032, 9.32, 10.6, 9.046, 9.85);
    }
    return new Pose(8, 14.25f, 8, 18, -16, .77f);
  }

  private static Pose tankard(FieldMesh item, String id) {
    item.paint("wood", 0x805e36);
    item.paint("inside", 0x473725);
    item.paint("edge", 0x9b6f3b);
    item.paint("band", 0xa8a8a8);
    item.paint("seam", 0x664f2f);
    String fill = null;
    if (!id.equals("brewinandchewin:tankard")) {
      item.paint("liquid", id.equals("brewinandchewin:mead") ? 0xff9116 : 0x574c27);
      item.paint("foam", id.equals("brewinandchewin:mead") ? 0xffd32d : 0x929238);
      fill = "liquid";
    }
    var mesh = item.body;
    double x = 6.95, bottom = 3.1, top = 11.25, radius = 4.05;
    vessel(mesh, x, bottom, top, radius, .62, "wood", "inside", "edge", fill);
    for (double band : new double[] {4.65, 8.55}) {
      double lower = radius * (.76 + .24 * (band - bottom - .48) / (top - .64 - bottom - .48));
      wall(
          mesh,
          "band",
          ring(x, band, 8, lower + .055, lower + .055),
          ring(x, band + .55, 8, lower + .14, lower + .14),
          false);
    }
    mesh.beam("seam", new P(10.48, 9.4, 8), new P(13.28, 9.42, 8), .65, 4);
    mesh.beam("edge", new P(13.14, 9.42, 8), new P(13.16, 5.82, 8), .65, 4);
    mesh.beam("seam", new P(13.3, 5.82, 8), new P(10, 5.75, 8), .65, 4);
    if (fill != null) {
      mesh.box("foam", 4.8, 10.484, 7.16, 6.92, 10.51, 7.73);
      mesh.box("foam", 7.88, 10.486, 8.61, 8.8, 10.52, 9.32);
    }
    return new Pose(13.04f, 7.45f, 8, 17, -30, .74f);
  }

  private static Pose bedroll(FieldMesh item) {
    item.paint("cloth", 0xac2922);
    item.paint("fold", 0x691112);
    item.paint("end", 0xbf3b33);
    item.paint("strap", 0xbda36c);
    item.paint("buckle", 0x695433);
    var mesh = item.body;
    mesh.shell(
        "cloth",
        new P[][] {
          ring(8, 2.3, 8, 3.15, 3.45), ring(8, 2.9, 8, 3.58, 3.62),
          ring(8, 13.12, 8, 3.58, 3.62), ring(8, 13.8, 8, 3.23, 3.23)
        });
    disk(mesh, "end", ring(8, 13.82, 8, 2.72, 2.72), 1);
    disk(mesh, "fold", ring(8, 13.84, 8, 2.15, 2.14), 1);
    disk(mesh, "end", ring(8.17, 13.86, 8, 1.55, 1.58), 1);
    disk(mesh, "cloth", ring(8.28, 13.88, 7.91, .76, .72), 1);
    for (double y : new double[] {5.08, 10.52}) {
      wall(mesh, "strap", ring(8, y - .58, 8, 3.64, 3.69), ring(8, y + .58, 8, 3.64, 3.69), false);
      mesh.box("buckle", 7.61, y - .48, 11.49, 8.43, y + .48, 11.83);
    }
    return new Pose(8, 8.3f, 10.9f, 9, -20, .83f);
  }

  private static Pose buoy(FieldMesh item) {
    item.paint("float", 0x924644);
    item.paint("seam", 0x613332);
    item.paint("frame", 0x484f64);
    item.paint("bell", 0xfed639);
    item.paint("inside", 0xfeb31b);
    var mesh = item.body;
    mesh.shell(
        "float",
        new P[][] {
          ring(8, 1.55, 8, 3.15, 3.15), ring(8, 2.6, 8, 4.15, 4.15),
          ring(8, 6.2, 8, 4.25, 4.25), ring(8, 7.1, 8, 3.45, 3.45)
        });
    wall(mesh, "seam", ring(8, 3.3, 8, 4.22, 4.22), ring(8, 3.72, 8, 4.27, 4.27), false);
    for (int side : new int[] {-1, 1}) {
      double x = 8 + side * 3.25;
      mesh.box("frame", x - .52, 6.53, 7.39, x + .52, 14.76, 8.61);
    }
    mesh.box("frame", 4.42, 14.15, 7.38, 11.55, 15.05, 8.63);
    mesh.beam("frame", new P(8, 14.25, 8), new P(8, 13.17, 8), .55, 6);
    P[] lip = ring(8, 9, 8, 2.45, 2.25);
    P[] top = ring(8, 13.35, 8, .88, .84);
    P[] mouth = ring(8, 9.02, 8, 2, 1.84);
    P[] throat = ring(8, 12.8, 8, .66, .59);
    wall(mesh, "bell", lip, ring(8, 9.57, 8, 1.89, 1.67), false);
    wall(mesh, "bell", ring(8, 9.57, 8, 1.89, 1.67), top, false);
    disk(mesh, "bell", top, 1);
    wall(mesh, "inside", mouth, throat, true);
    for (int side = 0; side < 8; side++) {
      int next = (side + 1) % 8;
      mesh.quad(
          "bell", new P[] {lip[side], lip[next], mouth[next], mouth[side]}, new P(0, -1, 0), UV);
    }
    disk(mesh, "inside", throat, -1);
    mesh.beam("inside", new P(8, 12.86, 8), new P(8, 9.19, 8), .37, 6);
    mesh.beam("bell", new P(8, 9.74, 8), new P(8, 8.62, 8), .58, 8);
    return new Pose(7.6f, 5.4f, 8.7f, -7, -20, .73f);
  }

  private static Pose bottle(FieldMesh item, String id) {
    boolean message = id.equals("hybrid_aquatic:message_in_a_bottle");
    boolean fly = id.equals("spawn:fly_bottle");
    boolean cloud = id.equals("quark:bottled_cloud");
    item.paint("cork", fly ? 0x424b30 : 0xd46d49);
    if (message || fly || cloud) {
      item.paint("content", message ? 0xd1d0b9 : fly ? 0x212121 : 0xe2f8ff);
      item.paint("detail", message ? 0xa7a58d : 0xffffff);
    }
    item.glass = new EquipmentObjMesh();
    item.paint(item.glass, "glass", fly ? 0x97956a : 0xb3cfec, .24);
    item.paint(item.glass, "edge", fly ? 0x75765b : 0x5d8fc2, .48);
    item.paint(item.glass, "lip", fly ? 0xada47f : 0xd4e5f7, .53);
    var glass = item.glass;
    double width = message ? 2.95 : fly ? 3.55 : 3.85;
    double neck = message ? 12.45 : fly ? 11.65 : 10.9;
    double belly = message ? 10.6 : fly ? 7.1 : 7.8;
    P[] foot = ring(8, 2.25, 8, width * .69, width * .59);
    P[] lower = ring(8, cloud ? 4.25 : 3.22, 8, width, width * .85);
    P[] shoulder =
        ring(8, belly, 8, width * (fly || cloud ? .8 : .95), width * (fly || cloud ? .68 : .81));
    P[] mouth = ring(8.35, neck, 8, 1.27, 1.18);
    P[] lip = ring(8.35, neck + .57, 8, 1.49, 1.42);
    P[] opening = ring(8.35, neck + .57, 8, .99, .91);
    disk(glass, "edge", foot, -1);
    wall(glass, "edge", foot, lower, false);
    wall(glass, "glass", lower, shoulder, false);
    wall(glass, "glass", shoulder, mouth, false);
    wall(glass, "lip", mouth, lip, false);
    rim(glass, "lip", lip, opening);
    wall(glass, "edge", ring(8.35, neck - .35, 8, .97, .89), opening, true);

    var mesh = item.body;
    mesh.shell(
        "cork",
        new P[][] {ring(8.35, neck - .24, 8, .89, .86), ring(8.35, neck + 1.32, 8, 1.14, 1.08)});
    if (message) {
      P bottom = new P(7.65, 3.55, 8.15);
      P top = new P(8.55, 9.4, 7.9);
      mesh.beam("content", bottom, top, 1.03, 6);
      mesh.beam("detail", new P(8.03, 6, 8.03), new P(8.13, 6.62, 8.02), 1.1, 6);
    } else if (fly) {
      fly(mesh, 7.15, 6.64, 9.06);
      fly(mesh, 8.9, 8.42, 7.65);
    } else if (cloud) {
      puff(mesh, "content", 6.67, 4.05, 7.9, 1.55);
      puff(mesh, "detail", 8.09, 4.46, 8.34, 1.78);
      puff(mesh, "content", 9.33, 4.1, 7.68, 1.27);
    } else {
      for (var entry :
          GalosphereEquipmentModels.regions("galosphere:spectre_flare").entrySet().stream()
              .filter(region -> region.getKey().startsWith("spectre_"))
              .sorted(Map.Entry.comparingByKey())
              .toList()) {
        double[] crop = entry.getValue();
        mesh.region(
            entry.getKey(),
            (crop[0] + .2) / 16,
            (crop[1] + .2) / 16,
            (crop[2] - .2) / 16,
            (crop[3] - .2) / 16);
        item.material(
            entry.getKey(),
            GalosphereEquipmentModels.materialTexture(
                "galosphere:spectre_flare", entry.getKey(), item.texture),
            1);
      }
      GalosphereEquipmentModels.bottledSpectre(mesh, new P(8, 5.82, 8.16), .98);
    }
    return new Pose(7.85f, 4.5f, 8.5f, message ? -24 : -10, -22, message ? .74f : .67f);
  }

  private static void puff(
      EquipmentObjMesh mesh, String material, double x, double y, double z, double radius) {
    mesh.shell(
        material,
        new P[][] {
          ring(x, y - .8, z, radius * .66, radius * .56),
          ring(x, y, z, radius, radius * .86),
          ring(x, y + .86, z, radius * .59, radius * .53)
        });
  }

  private static void fly(EquipmentObjMesh mesh, double x, double y, double z) {
    mesh.box("content", x - .32, y - .49, z - .43, x + .32, y + .4, z + .43);
    for (int side : new int[] {-1, 1}) {
      mesh.plate(
          "detail",
          "detail",
          List.of(
              new P(x + side * .22, y + .05, z), new P(x + side * .57, y + .63, z),
              new P(x + side * 1.18, y + .53, z), new P(x + side * 1.12, y + .14, z)),
          .11);
    }
  }
}
