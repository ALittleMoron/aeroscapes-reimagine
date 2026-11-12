package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/**
 * Diesel workshop equipment; native skins, predicates and processing renderers remain in charge.
 */
final class DieselEquipmentModels {
  private static final String NAMESPACE = "createdieselgenerators:";
  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/diesel_equipment/";
  private static final List<String> MATERIALS =
      List.of("body", "edge", "wood", "metal", "dark", "face", "lid", "flame");
  static final List<String> SKINS =
      List.of(
          "sussy",
          "fire",
          "heavier",
          "willows",
          "soul",
          "creeper",
          "titanium",
          "arc",
          "glass",
          "diamond",
          "dragon",
          "obsidian");

  private DieselEquipmentModels() {}

  static List<String> items() {
    List<String> items =
        new ArrayList<>(
            List.of(
                "hammer",
                "wire_cutters",
                "wire_cutters_cut",
                "oil_scanner",
                "oil_scanner_none",
                "oil_scanner_medium",
                "oil_scanner_high",
                "track_layers_bag",
                "track_layers_bag_filled"));
    for (String prefix : List.of("lighter", "lighter/")) {
      if (prefix.equals("lighter")) {
        for (String state : List.of("", "_open", "_ignited")) items.add(prefix + state);
      } else {
        for (String skin : SKINS)
          for (String state : List.of("", "_open", "_ignited")) items.add(prefix + skin + state);
      }
    }
    return items;
  }

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Map<String, JsonObject> models = new LinkedHashMap<>();
    StringBuilder materials = new StringBuilder();
    for (String item : items()) {
      String name = item.replace('/', '_');
      EquipmentObjMesh mesh = new EquipmentObjMesh();
      for (int index = 0; index < MATERIALS.size(); index++) {
        String material = MATERIALS.get(index), key = name + "_" + material;
        mesh.material(material, key);
        int x = index % 4 * 16, y = index / 4 * 16;
        mesh.region(key, (x + .1) / 64, (y + .1) / 32, (x + 15.9) / 64, (y + 15.9) / 32);
        materials
            .append("newmtl ")
            .append(key)
            .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd aeroscapes_reimagine:item/diesel_equipment/")
            .append(name)
            .append("\n\n");
      }
      if (item.equals("hammer")) hammer(mesh);
      else if (item.startsWith("wire_cutters")) cutters(mesh, item.endsWith("_cut"));
      else if (item.startsWith("oil_scanner")) scanner(mesh);
      else if (item.startsWith("track_layers_bag")) bag(mesh, item.endsWith("_filled"));
      else lighter(mesh, item);
      output.put(
          assets.resolve("aeroscapes_reimagine/models/item/diesel_equipment/" + name + ".obj"),
          mesh.obj().getBytes(StandardCharsets.UTF_8));
      output.put(
          assets.resolve("aeroscapes_reimagine/textures/item/diesel_equipment/" + name + ".png"),
          EquipmentModels.png(texture(item)));
      animation(assets, output, item, name);
      JsonObject model = new JsonObject();
      model.addProperty("loader", "neoforge:obj");
      model.addProperty("model", DIRECTORY + name + ".obj");
      model.addProperty("automatic_culling", false);
      model.addProperty("flip_v", false);
      model.addProperty("ambientocclusion", false);
      model.addProperty(
          "render_type", item.contains("/glass") ? "minecraft:translucent" : "minecraft:cutout");
      JsonObject textures = new JsonObject();
      textures.addProperty("particle", "aeroscapes_reimagine:item/diesel_equipment/" + name);
      model.add("textures", textures);
      model.add("display", display(item));
      models.put(NAMESPACE + item, model);
    }
    output.put(
        assets.resolve("aeroscapes_reimagine/models/item/diesel_equipment/equipment.mtl"),
        (materials.toString().stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8));
    HeldItemModels.add(assets, output, models);
    JsonObject arm = new JsonObject();
    arm.addProperty("scale", 1);
    arm.addProperty("offY", -.66);
    JsonObject sides = new JsonObject();
    sides.add("right", arm);
    sides.add("left", arm.deepCopy());
    JsonObject item = new JsonObject();
    item.add("armTransforms", sides);
    JsonObject profiles = new JsonObject();
    profiles.add("createdieselgenerators:track_layers_bag", item);
    JsonObject compat = new JsonObject();
    compat.add("itemSpecific", profiles);
    EquipmentModels.text(
        output,
        assets.resolve("minecraft/punchy/compat/aeroscapes_diesel_equipment.json"),
        compat.toString() + "\n");
  }

  private static JsonObject display(String item) {
    boolean lighter = item.startsWith("lighter"), bag = item.startsWith("track_layers_bag");
    double scale = lighter ? .27 : bag ? .65 : item.equals("hammer") ? .8 : .65;
    JsonObject display = new JsonObject();
    for (String context :
        List.of(
            "firstperson_righthand",
            "firstperson_lefthand",
            "thirdperson_righthand",
            "thirdperson_lefthand")) {
      boolean left = context.endsWith("lefthand");
      JsonObject transform = new JsonObject();
      transform.add("rotation", vector(0, item.equals("hammer") ? left ? -90 : 90 : 0, 0));
      transform.add("scale", vector(scale, scale, scale));
      display.add(context, transform);
    }
    Vector3f grip =
        lighter
            ? new Vector3f(8, 2, 8)
            : bag
                ? new Vector3f(8, 14.55f, 8)
                : item.startsWith("wire_cutters")
                    ? new Vector3f(7, 4, 8)
                    : new Vector3f(8, 3.5f, 8);
    HeldItemModels.anchorHeld(display, grip, new Vector3f(0, bag ? -3.2f : -1, 1));
    return display;
  }

  private static com.google.gson.JsonArray vector(double x, double y, double z) {
    return JsonParser.parseString("[" + x + "," + y + "," + z + "]").getAsJsonArray();
  }

  private static BufferedImage texture(String item) throws IOException {
    JsonObject nativeModel = NativeEquipmentModels.inherited(NAMESPACE + item);
    String source = nativeModel.getAsJsonObject("textures").get("layer0").getAsString();
    BufferedImage sprite = NativeEquipmentModels.image(source);
    BufferedImage caseSprite =
        item.startsWith("lighter")
            ? NativeEquipmentModels.image(
                NativeEquipmentModels.inherited(
                        NAMESPACE + item.replace("_open", "").replace("_ignited", ""))
                    .getAsJsonObject("textures")
                    .get("layer0")
                    .getAsString())
            : sprite;
    int frames = sprite.getHeight() / 16;
    BufferedImage image = new BufferedImage(64, 32 * frames, BufferedImage.TYPE_INT_ARGB);
    for (int frame = 0; frame < frames; frame++) {
      for (int index = 0; index < MATERIALS.size(); index++) {
        String material = MATERIALS.get(index);
        int base = nearest(sprite, color(item, material));
        for (int y = 0; y < 16; y++)
          for (int x = 0; x < 16; x++) {
            int color = base;
            int shade = x < 3 ? 12 : x > 12 ? -13 : (x / 5 + y / 6) % 3 == 0 ? -6 : 0;
            color = nearest(sprite, EquipmentModels.shaded(base, shade));
            if (item.startsWith("lighter") && !material.equals("flame"))
              color = lighterColor(caseSprite, item, material, x, y);
            if (material.equals("face") && item.startsWith("oil_scanner")) {
              int sx = 4 + x * 8 / 16, sy = 5 + y * 4 / 16;
              int sample = sprite.getRGB(sx, sy + frame * 16);
              if ((sample >>> 24) == 255) color = sample;
            }
            if (material.equals("flame")) {
              // Keep the selected skin's flame palette instead of imposing the standard orange.
              int flameX = item.contains("heavier") ? 9 : 7;
              int sample = sprite.getRGB(flameX + x * 3 / 16, y * 4 / 16 + frame * 16);
              if ((sample >>> 24) == 255) color = sample;
            }
            image.setRGB(index % 4 * 16 + x, index / 4 * 16 + y + frame * 32, color);
          }
      }
    }
    return image;
  }

  private static int lighterColor(
      BufferedImage source, String item, String material, int x, int y) {
    String skin =
        item.contains("/")
            ? item.substring(item.indexOf('/') + 1).replace("_open", "").replace("_ignited", "")
            : "standard";
    int base =
        switch (skin) {
          case "sussy" -> 0xA81215;
          case "fire" -> 0xCC431A;
          case "heavier" -> 0x7B7E7C;
          case "willows" -> 0x8E1720;
          case "soul" -> 0x343537;
          case "creeper" -> 0x2C8C1E;
          case "titanium" -> 0xB4BCBC;
          case "arc" -> 0x243F91;
          case "glass" -> 0xA5CDEC;
          case "diamond" -> 0x24BBB8;
          case "dragon", "obsidian" -> 0x21152F;
          default -> 0xDBAA4D;
        };
    if (material.equals("metal")) return nearest(source, 0xD4CEAF);
    if (material.equals("wood")) return nearest(source, 0xDDB954);
    if (material.equals("dark")) return nearest(source, 0x302820);
    if (material.equals("edge")) return nearest(source, EquipmentModels.shaded(base, -30));
    int shade = x < 3 ? 15 : x > 12 ? -18 : (x / 6 + y / 6) % 3 == 0 ? -5 : 0;
    int target = EquipmentModels.shaded(base, shade);
    if (skin.equals("diamond"))
      target =
          EquipmentModels.shaded(base, x + y < 11 ? 55 : x - y > 5 ? -30 : x + y > 22 ? -13 : 15);
    if (material.equals("body")) {
      boolean motif =
          switch (skin) {
            case "sussy" -> x >= 4 && x <= 11 && y >= 3 && y <= 6;
            case "fire" -> y > 8 && x >= 3 && x <= 12 && y > 16 - (x % 5) * 2;
            case "willows" -> x < 3 || x > 12 || y == 2 || y == 13;
            case "soul" -> x >= 9 && x <= 11 || y >= 10 && y <= 12 && x >= 4;
            case "creeper" ->
                (x >= 4 && x <= 6 || x >= 9 && x <= 11) && y >= 4 && y <= 6
                    || x >= 7 && x <= 8 && y >= 7 && y <= 10
                    || x >= 5 && x <= 10 && y >= 9 && y <= 12;
            case "arc" -> y >= 10;
            case "dragon" -> x >= 5 && x <= 10 && y >= 5 && y <= 9;
            default -> false;
          };
      if (motif)
        target =
            switch (skin) {
              case "sussy", "soul" -> 0x1DB5C1;
              case "willows", "fire" -> 0xE7B752;
              case "arc", "dragon" -> 0x723C9B;
              case "glass" -> 0x493D34;
              default -> 0x122E17;
            };
    }
    int color = nearest(source, target);
    return skin.equals("glass") && (material.equals("body") || material.equals("lid"))
        ? color & 0xFFFFFF | 0x60000000
        : color;
  }

  private static void animation(Path assets, Map<Path, byte[]> output, String item, String name)
      throws IOException {
    String source =
        NativeEquipmentModels.inherited(NAMESPACE + item)
            .getAsJsonObject("textures")
            .get("layer0")
            .getAsString();
    String path = "/assets/" + source.replace(":", "/textures/") + ".png.mcmeta";
    try (var stream = DieselEquipmentModels.class.getResourceAsStream(path)) {
      if (stream == null) return;
      JsonObject metadata =
          JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
              .getAsJsonObject();
      metadata.getAsJsonObject("animation").addProperty("width", 64);
      metadata.getAsJsonObject("animation").addProperty("height", 32);
      EquipmentModels.text(
          output,
          assets.resolve(
              "aeroscapes_reimagine/textures/item/diesel_equipment/" + name + ".png.mcmeta"),
          metadata.toString() + "\n");
    }
  }

  private static int color(String item, String material) {
    return switch (material) {
      case "body" ->
          item.startsWith("track_layers_bag")
              ? 0xB89D89
              : item.startsWith("lighter") ? 0xB98A35 : 0x7D8583;
      case "edge" -> item.startsWith("track_layers_bag") ? 0x65554B : 0x4F5553;
      case "wood" -> 0x775D29;
      case "metal" -> 0xCFD7D5;
      case "dark" -> 0x383A36;
      case "face" -> 0x31382D;
      case "lid" -> 0xDDB954;
      case "flame" -> 0xF6B631;
      default -> throw new IllegalArgumentException(material);
    };
  }

  private static int nearest(BufferedImage image, int target) {
    int best = 0, distance = Integer.MAX_VALUE;
    for (int y = 0; y < Math.min(16, image.getHeight()); y++)
      for (int x = 0; x < image.getWidth(); x++) {
        int color = image.getRGB(x, y);
        if ((color >>> 24) != 255) continue;
        int red = (color >> 16 & 255) - (target >> 16 & 255);
        int green = (color >> 8 & 255) - (target >> 8 & 255);
        int blue = (color & 255) - (target & 255);
        int current = red * red + green * green + blue * blue;
        if (current < distance) {
          distance = current;
          best = color;
        }
      }
    if (distance == Integer.MAX_VALUE) throw new IllegalArgumentException("Empty native material");
    return best;
  }

  private static void hammer(EquipmentObjMesh mesh) {
    mesh.group("square_wood_handle");
    mesh.box("wood", 7, 0, 7, 9, 14.5, 9);
    mesh.group("head_socket");
    mesh.box("edge", 6.6, 12.7, 6.5, 9.4, 13.3, 9.5);
    mesh.box("body", 6.6, 13.3, 6.5, 9.4, 16.5, 9.5);
    mesh.box("metal", 6.9, 16.5, 6.8, 9.1, 16.8, 9.2);
    mesh.group("striking_head");
    mesh.box("body", 8.9, 13.7, 6.6, 11.8, 16, 9.4);
    mesh.box("metal", 11.6, 13.35, 6.3, 12.6, 16.35, 9.7);
    mesh.group("split_claw");
    for (double z : new double[] {6.6, 8.6})
      mesh.plate(
          "body",
          "metal",
          List.of(
              new P(7, 14, z),
              new P(4.8, 14.3, z),
              new P(2.8, 16.4, z),
              new P(3.5, 17, z),
              new P(5.6, 15.7, z),
              new P(7, 15.9, z)),
          .8);
  }

  private static void cutters(EquipmentObjMesh mesh, boolean cut) {
    mesh.group("crossed_shanks");
    for (int side : new int[] {-1, 1}) {
      double outer = cut ? 3.8 : 1.8;
      double x = 8 + side * outer;
      mesh.plate(
          "wood",
          "wood",
          List.of(
              new P(x - .7, 0, 8), new P(x + .7, 0, 8),
              new P(8 + side * 1.3 + .65, 7.6, 8), new P(8 + side * 1.3 - .65, 7.6, 8)),
          1.6);
      double spread = cut ? 1.4 : 0;
      mesh.plate(
          "body",
          "metal",
          List.of(
              new P(8 + side * 1.5, 7.5, 8), new P(8 + side * (2.3 + spread), 10.8, 8),
              new P(8 + side * (1.8 + spread), 12.8, 8), new P(8 + side * (.1 + spread), 13.6, 8),
              new P(8 + side * (.1 + spread), 12.5, 8), new P(8 + side * (1 + spread), 11.8, 8),
              new P(8 + side * (1 + spread), 10.7, 8), new P(8 - side * .7, 8.7, 8)),
          1.1);
    }
    mesh.group("pivot_rivet");
    mesh.box("edge", 6.9, 7.4, 6.9, 9.1, 9.7, 9.1);
    mesh.box("metal", 7.55, 8.05, 6.6, 8.45, 8.95, 9.4);
  }

  private static void scanner(EquipmentObjMesh mesh) {
    mesh.group("handle");
    mesh.box("edge", 7, 0, 7, 9, 6, 9);
    mesh.box("metal", 7.45, .8, 6.95, 8.55, 5, 7);
    mesh.box("metal", 7.45, .8, 9, 8.55, 5, 9.05);
    mesh.group("gauge_housing");
    mesh.box("edge", 2.6, 6, 6.5, 13.4, 10.5, 9.5);
    mesh.box("body", 2, 7, 6.9, 14, 9.5, 9.1);
    mesh.box("metal", 3, 6.5, 9.5, 13, 10, 9.9);
    mesh.box("face", 3.7, 7.1, 9.9, 12.3, 9.4, 10);
    mesh.group("upper_bow");
    mesh.box("body", 3.4, 10.5, 7.2, 4.4, 13.6, 8.8);
    mesh.box("body", 11.6, 10.5, 7.2, 12.6, 13.6, 8.8);
    mesh.box("body", 4.4, 12.6, 7.2, 11.6, 13.6, 8.8);
  }

  private static void bag(EquipmentObjMesh mesh, boolean filled) {
    mesh.group("leather_sack");
    double top = filled ? 13 : 10.5;
    P[][] rings = {
      EquipmentObjMesh.octagonalRing(8, 1, 8, 3.5, 2.6),
      EquipmentObjMesh.octagonalRing(8, 3, 8, 5, 3.7),
      EquipmentObjMesh.octagonalRing(8, 7, 8, 5.3, 3.8),
      EquipmentObjMesh.octagonalRing(8, top, 8, filled ? 2.8 : 3.8, filled ? 2 : 2.7)
    };
    // Open shell, with a visibly separate inner wall and recessed contents.
    for (int row = 1; row < rings.length; row++) connect(mesh, "body", rings[row - 1], rings[row]);
    P[] mouth = rings[rings.length - 1];
    P[] inner =
        EquipmentObjMesh.octagonalRing(8, top - .3, 8, filled ? 2.35 : 3.35, filled ? 1.55 : 2.25);
    connect(mesh, "edge", mouth, inner);
    P[] floor =
        EquipmentObjMesh.octagonalRing(
            8, filled ? top - 1.2 : 7.5, 8, filled ? 2.1 : 2.8, filled ? 1.4 : 1.8);
    connect(mesh, "edge", floor, inner, true);
    cap(mesh, "dark", floor, true);
    cap(mesh, "edge", rings[0], false);
    mesh.group("carry_loop");
    mesh.box("edge", 3.8, 7, 7.4, 4.6, 15, 8.6);
    mesh.box("edge", 11.4, 7, 7.4, 12.2, 15, 8.6);
    mesh.box("body", 4.6, 14.1, 7.4, 11.4, 15, 8.6);
    mesh.group("brass_fastener");
    mesh.box("metal", 7.5, 5, 11.6, 8.5, 6.2, 11.9);
    if (filled) {
      mesh.group("stored_rail_ends");
      for (double x : new double[] {6.4, 8.8}) {
        mesh.box("edge", x, 10.8, 7.3, x + .8, 13.6, 8.4);
        mesh.box("metal", x - .2, 13.4, 7.1, x + 1, 13.8, 8.6);
      }
    }
  }

  private static void connect(EquipmentObjMesh mesh, String material, P[] lower, P[] upper) {
    connect(mesh, material, lower, upper, false);
  }

  private static void connect(
      EquipmentObjMesh mesh, String material, P[] lower, P[] upper, boolean inward) {
    for (int side = 0; side < lower.length; side++) {
      int next = (side + 1) % lower.length;
      P outward =
          new P(
              (lower[side].x() + lower[next].x()) / 2 - 8,
              0,
              (lower[side].z() + lower[next].z()) / 2 - 8);
      if (inward) outward = outward.scale(-1);
      P[] points = {lower[side], lower[next], upper[next], upper[side]};
      double[][] uv = new double[4][2];
      for (int index = 0; index < 4; index++) {
        P point = points[index];
        uv[index][0] = (Math.abs(outward.x()) > Math.abs(outward.z()) ? point.z() : point.x()) / 16;
        uv[index][1] = 1 - point.y() / 16;
      }
      mesh.quad(material, points, outward, uv);
    }
  }

  private static void cap(EquipmentObjMesh mesh, String material, P[] ring, boolean up) {
    P center = new P(8, ring[0].y(), 8);
    for (int side = 0; side < ring.length; side++) {
      P next = ring[(side + 1) % ring.length];
      P first = ring[side];
      mesh.quad(
          material,
          new P[] {center, first, next, next},
          new P(0, up ? 1 : -1, 0),
          new double[][] {
            {.5, .5},
            {first.x() / 16, first.z() / 16},
            {next.x() / 16, next.z() / 16},
            {next.x() / 16, next.z() / 16}
          });
    }
  }

  private static void lighter(EquipmentObjMesh mesh, String item) {
    if (item.contains("/heavier")) {
      heavyLighter(mesh, item);
      return;
    }
    if (item.contains("/willows")) {
      liftArmLighter(mesh, item);
      return;
    }
    boolean open = item.endsWith("_open") || item.endsWith("_ignited");
    mesh.group("pocket_case");
    mesh.box("body", 4.5, 2, 6.8, 11.5, 10, 9.2);
    if (item.contains("/glass")) {
      mesh.group("glass_case_edges_and_inner_wick");
      for (double x : new double[] {4.5, 11.2})
        for (double z : new double[] {6.8, 8.9}) mesh.box("metal", x, 2, z, x + .3, 10, z + .3);
      mesh.box("metal", 4.5, 2, 6.8, 11.5, 2.3, 9.2);
      mesh.box("metal", 4.5, 9.7, 6.8, 11.5, 10, 9.2);
      mesh.box("dark", 7.1, 2.3, 7.5, 8.2, 9.7, 8.5);
      mesh.box("edge", 6.4, 8.3, 7.4, 9.3, 9.7, 8.6);
    }
    mesh.box("edge", 4.8, 1.6, 7, 11.2, 2, 9);
    mesh.group("hinge");
    mesh.box("edge", 11.5, 8.8, 7.2, 12.1, 10.2, 8.8);
    mesh.group("lid");
    if (open) {
      mesh.box("lid", 12.1, 9.1, 6.8, 14.5, 15.5, 9.2);
      if (item.contains("/glass")) {
        for (double x : new double[] {12.1, 14.2})
          mesh.box("metal", x, 9.1, 6.8, x + .3, 15.5, 7.1);
        mesh.box("metal", 12.1, 15.2, 6.8, 14.5, 15.5, 9.2);
      }
      mesh.box("edge", 11.5, 9.1, 7.2, 12.2, 10.2, 8.8);
      mesh.group("wick_insert");
      mesh.box("metal", 5.2, 10, 7, 8.7, 12.8, 9);
      mesh.box("dark", 6.2, 12.8, 7.7, 7.5, 13.2, 8.3);
      for (double x : new double[] {5.65, 7.5})
        for (double y : new double[] {10.6, 11.6}) {
          mesh.box("dark", x, y, 6.95, x + .55, y + .45, 7);
          mesh.box("dark", x, y, 9, x + .55, y + .45, 9.05);
        }
      mesh.group("flint_wheel");
      mesh.box("edge", 9, 10, 7.2, 10.7, 11.7, 8.8);
    } else {
      mesh.box("lid", 4.5, 10.1, 6.8, 11.5, 13.5, 9.2);
      if (item.contains("/glass")) {
        for (double x : new double[] {4.5, 11.2})
          mesh.box("metal", x, 10.1, 6.8, x + .3, 13.5, 7.1);
        mesh.box("metal", 4.5, 13.2, 6.8, 11.5, 13.5, 9.2);
      }
    }
    if (item.endsWith("_ignited")) {
      flame(mesh, 6.7, 13);
    }
  }

  private static void heavyLighter(EquipmentObjMesh mesh, String item) {
    mesh.group("heavy_ribbed_case");
    mesh.shell(
        "body",
        new P[][] {
          EquipmentObjMesh.octagonalRing(8, 2, 8, 3.2, 1.8),
          EquipmentObjMesh.octagonalRing(8, 3, 8, 3.9, 2.1),
          EquipmentObjMesh.octagonalRing(8, 10.5, 8, 3.9, 2.1),
          EquipmentObjMesh.octagonalRing(8, 11.2, 8, 3.4, 1.8)
        });
    for (double y : new double[] {2.8, 5.1, 7.4, 9.7}) {
      mesh.box("edge", 3.8, y, 7.1, 4.7, y + .5, 8.9);
      mesh.box("edge", 11.3, y, 7.1, 12.2, y + .5, 8.9);
    }
    mesh.group("permanent_exposed_wick_and_striker");
    mesh.box("metal", 6.2, 11.2, 7.4, 8.1, 12.1, 8.6);
    mesh.box("dark", 8.8, 11.2, 7.2, 10.9, 12.5, 8.8);
    if (item.endsWith("_ignited")) flame(mesh, 7.1, 12.1);
  }

  private static void liftArmLighter(EquipmentObjMesh mesh, String item) {
    mesh.group("red_fuel_case");
    mesh.box("body", 4.5, 2.5, 6.6, 9.8, 11, 9.4);
    mesh.box("metal", 5, 3.1, 9.4, 6, 10.4, 9.6);
    mesh.box("metal", 5, 3.1, 6.4, 6, 10.4, 6.6);
    mesh.group("brass_bands");
    mesh.box("wood", 4.3, 2, 6.4, 10, 2.8, 9.6);
    mesh.box("wood", 4.3, 10.4, 6.4, 10, 11.2, 9.6);
    mesh.group("open_side_handle");
    mesh.box("edge", 9.8, 3, 7.2, 13, 3.8, 8.8);
    mesh.box("body", 12.2, 3.8, 7.2, 13, 8.8, 8.8);
    mesh.box("edge", 9.8, 8.8, 7.2, 13, 9.6, 8.8);
    mesh.group("lift_arm_and_wick");
    mesh.box("wood", 7.2, 11.2, 7.3, 9.2, 12.2, 8.7);
    if (item.endsWith("_open") || item.endsWith("_ignited")) {
      mesh.plate(
          "edge",
          "wood",
          List.of(new P(4.5, 11.2, 8), new P(5.3, 11.2, 8), new P(6.4, 14.7, 8), new P(5.6, 15, 8)),
          1.2);
      mesh.box("wood", 5.5, 14.6, 7.2, 7.2, 15.2, 8.8);
    } else {
      mesh.box("edge", 4.5, 11.2, 7.4, 5.3, 13, 8.6);
      mesh.box("wood", 5.3, 12.2, 7.2, 9.3, 13, 8.8);
    }
    if (item.endsWith("_ignited")) flame(mesh, 8.1, 12.2);
  }

  private static void flame(EquipmentObjMesh mesh, double x, double bottom) {
    mesh.group("flame");
    mesh.plate(
        "flame",
        "flame",
        List.of(
            new P(x - 1, bottom, 8),
            new P(x + 1, bottom, 8),
            new P(x + 1, bottom + 2, 8),
            new P(x, bottom + 4.2, 8),
            new P(x - 1, bottom + 2, 8)),
        .7);
  }
}
