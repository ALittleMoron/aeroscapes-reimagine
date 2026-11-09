package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.joml.Vector3f;

/** Native cave equipment, with separate physical housings and stateful working surfaces. */
final class GalosphereEquipmentModels {
  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/galosphere_equipment/";

  private GalosphereEquipmentModels() {}

  static boolean supports(String id) {
    return id.startsWith("galosphere:monstrometer")
        || id.startsWith("galosphere:saltbound_tablet")
        || List.of(
                "galosphere:echo_bell",
                "galosphere:glow_flare",
                "galosphere:palladium_bomb",
                "galosphere:spectre_bound_spyglass",
                "galosphere:spectre_flare")
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
          "firstperson_righthand":{"rotation":[0,-70,10],"translation":[0,0,0],"scale":[0.65,0.65,0.65]},
          "firstperson_lefthand":{"rotation":[0,70,-10],"translation":[0,0,0],"scale":[0.65,0.65,0.65]},
          "thirdperson_righthand":{"rotation":[0,90,0],"translation":[0,0,0],"scale":[0.7,0.7,0.7]},
          "thirdperson_lefthand":{"rotation":[0,-90,0],"translation":[0,0,0],"scale":[0.7,0.7,0.7]}
        }
        """)
            .getAsJsonObject();
    boolean gauge = id.contains("monstrometer"), tablet = id.contains("saltbound_tablet");
    Vector3f grip = new Vector3f(8, 3, 8);
    if (gauge || tablet) {
      for (var entry : display.entrySet()) {
        JsonObject hand = entry.getValue().getAsJsonObject();
        hand.add("rotation", JsonParser.parseString("[-25,0,0]"));
        hand.add("scale", JsonParser.parseString(gauge ? "[0.5,0.5,0.5]" : "[0.65,0.65,0.65]"));
      }
      grip = gauge ? new Vector3f(8, 3.6f, 7.2f) : new Vector3f(5, 4.5f, 8);
    } else if (id.endsWith("palladium_bomb")) {
      grip = new Vector3f(8, 3.5f, 8);
      for (var entry : display.entrySet())
        entry.getValue().getAsJsonObject().add("scale", JsonParser.parseString("[0.7,0.7,0.7]"));
    }
    if (id.equals("galosphere:spectre_flare"))
      for (var entry : display.entrySet())
        entry.getValue().getAsJsonObject().add("rotation", JsonParser.parseString("[-25,0,0]"));
    HeldItemModels.anchorHeld(display, grip);
    if (tablet) {
      // Preserve the accepted left edge grip while centering the right palm on the slab.
      JsonObject rightHands = new JsonObject();
      for (String context : List.of("firstperson_righthand", "thirdperson_righthand"))
        rightHands.add(context, display.get(context));
      HeldItemModels.anchorHeld(rightHands, new Vector3f(8, 4.5f, 8));
    }
    // The ordinary ItemInHandLayer attachment is below and ahead of the palm.
    for (var entry : display.entrySet()) {
      var translation = entry.getValue().getAsJsonObject().getAsJsonArray("translation");
      translation.set(1, new com.google.gson.JsonPrimitive(translation.get(1).getAsFloat() - 2));
      translation.set(2, new com.google.gson.JsonPrimitive(translation.get(2).getAsFloat() + 2));
    }
    model.add("display", display);
    if (id.equals("galosphere:spectre_flare")) {
      JsonObject body = model.deepCopy();
      body.remove("display");
      JsonObject glass = body.deepCopy();
      glass.addProperty("model", DIRECTORY + "spectre_flare_glass.obj");
      glass.addProperty("render_type", "minecraft:translucent");
      JsonObject children = new JsonObject();
      children.add("body", body);
      children.add("glass", glass);
      model.addProperty("loader", "neoforge:composite");
      for (String field :
          List.of("model", "automatic_culling", "emissive_ambient", "flip_v", "render_type"))
        model.remove(field);
      model.add("children", children);
      model.add("item_render_order", JsonParser.parseString("[\"body\",\"glass\"]"));
    }
    return model;
  }

  static void addResources(Path assets, Map<Path, byte[]> output, Map<String, JsonObject> models)
      throws IOException {
    StringBuilder materials = new StringBuilder();
    Path directory = assets.resolve("aeroscapes_reimagine/models/item/galosphere_equipment");
    for (String id : models.keySet()) {
      if (!supports(id)) continue;
      EquipmentObjMesh mesh = new EquipmentObjMesh();
      EquipmentObjMesh glass = new EquipmentObjMesh();
      String texture =
          NativeEquipmentModels.inherited(id)
              .getAsJsonObject("textures")
              .get("layer0")
              .getAsString();
      for (var entry :
          regions(id).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        String material = name(id) + "_" + entry.getKey();
        double[] crop = entry.getValue();
        for (EquipmentObjMesh target : List.of(mesh, glass)) {
          target.material(entry.getKey(), material);
          target.region(
              material,
              (crop[0] + .2) / 16,
              (crop[1] + .2) / 16,
              (crop[2] - .2) / 16,
              (crop[3] - .2) / 16);
        }
        materials
            .append("newmtl ")
            .append(material)
            .append("\nKa 0 0 0\nKd 1 1 1 ")
            .append(
                id.equals("galosphere:spectre_flare") && entry.getKey().equals("glass")
                    ? "0.24"
                    : "1")
            .append("\nmap_Kd ")
            .append(materialTexture(id, entry.getKey(), texture))
            .append("\n\n");
      }
      if (id.contains("monstrometer")) gauge(mesh);
      else if (id.contains("saltbound_tablet")) tablet(mesh);
      else
        switch (id) {
          case "galosphere:echo_bell" -> bell(mesh);
          case "galosphere:glow_flare" -> glowFlare(mesh);
          case "galosphere:palladium_bomb" -> bomb(mesh);
          case "galosphere:spectre_bound_spyglass" -> spyglass(mesh);
          case "galosphere:spectre_flare" -> {
            spectreFlare(mesh);
            bottleGlass(glass);
            output.put(
                directory.resolve("spectre_flare_glass.obj"),
                glass.obj().getBytes(StandardCharsets.UTF_8));
          }
          default -> throw new IllegalArgumentException("Unknown Galosphere equipment: " + id);
        }
      output.put(directory.resolve(name(id) + ".obj"), mesh.obj().getBytes(StandardCharsets.UTF_8));
    }
    output.put(
        directory.resolve("equipment.mtl"),
        (materials.toString().stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8));
  }

  private static String name(String id) {
    return id.substring("galosphere:".length());
  }

  private static double[] pixel(int x, int y) {
    return new double[] {x, y, x + 1, y + 1};
  }

  static String materialTexture(String id, String material, String nativeTexture) {
    return id.equals("galosphere:spectre_flare") && material.startsWith("spectre_")
        ? "galosphere:entity/spectre/spectre"
        : nativeTexture;
  }

  static Map<String, double[]> regions(String id) {
    if (id.contains("monstrometer"))
      return Map.of(
          "metal",
          pixel(7, 2),
          "dark",
          pixel(0, 1),
          "body",
          pixel(7, 12),
          "face",
          new double[] {5, 4, 11, 9});
    if (id.contains("saltbound_tablet"))
      return Map.of(
          "body",
          new double[] {6, 10, 12, 14},
          "edge",
          pixel(5, 13),
          "light",
          pixel(4, 2),
          "chip",
          pixel(13, 4),
          "crack",
          pixel(13, 6),
          "stone",
          new double[] {7, 5, 9, 8});
    return switch (id) {
      case "galosphere:echo_bell" ->
          Map.of(
              "body",
              new double[] {6, 7, 10, 9},
              "edge",
              pixel(9, 6),
              "dark",
              pixel(8, 3),
              "grip",
              new double[] {2, 12, 3, 14},
              "band",
              pixel(4, 10));
      case "galosphere:glow_flare" ->
          Map.of(
              "body",
              new double[] {6, 7, 9, 11},
              "edge",
              pixel(5, 6),
              "light",
              pixel(7, 3),
              "cap",
              pixel(5, 4),
              "fuse",
              pixel(11, 12));
      case "galosphere:palladium_bomb" ->
          Map.of(
              "body",
              pixel(5, 12),
              "edge",
              pixel(4, 8),
              "dark",
              pixel(4, 1),
              "light",
              pixel(8, 3),
              "metal",
              pixel(6, 3),
              "wrap",
              pixel(6, 3),
              "fuse",
              pixel(13, 1));
      case "galosphere:spectre_bound_spyglass" ->
          Map.of(
              "grip",
              new double[] {4, 8, 6, 10},
              "metal",
              pixel(8, 5),
              "edge",
              pixel(10, 1),
              "glass",
              pixel(11, 3),
              "dark",
              pixel(3, 14));
      case "galosphere:spectre_flare" ->
          Map.ofEntries(
              Map.entry("body", new double[] {6, 11, 9, 13}),
              Map.entry("metal", pixel(6, 9)),
              Map.entry("glass", pixel(11, 3)),
              Map.entry("orange", pixel(5, 8)),
              Map.entry("fuse", pixel(11, 13)),
              Map.entry("spectre_head", new double[] {2, 4, 4, 8}),
              Map.entry("spectre_body", new double[] {2, 0, 4, 3}),
              Map.entry("spectre_wing", new double[] {8.5, 8, 9, 9}),
              Map.entry("spectre_eyes", new double[] {0, 0, .5, 1}));
      default -> throw new IllegalArgumentException("Unknown Galosphere materials: " + id);
    };
  }

  private static void bell(EquipmentObjMesh mesh) {
    mesh.group("grip");
    box(mesh, "grip", 7.2, .8, 7.2, 8.8, 7.4, 8.8);
    mesh.group("band");
    box(mesh, "band", 7.05, 7.4, 7.05, 8.95, 8.35, 8.95);
    mesh.group("shell");
    double[][] outer = {{8.35, .9}, {9.1, 1.8}, {10.3, 2.4}, {11.8, 2.65}, {13.4, 3.7}, {14, 4.05}};
    double[][] inner = {
      {8.65, .65}, {9.1, 1.45}, {10.3, 2.05}, {11.8, 2.3}, {13.4, 3.35}, {14, 3.7}
    };
    lathe(mesh, "body", outer, false, false);
    lathe(mesh, "dark", inner, true, false);
    cap(mesh, "dark", 8.65, .65, 1);
    rim(mesh, "edge", 14, 4.05, 3.7);
    mesh.group("clapper");
    lathe(mesh, "dark", new double[][] {{8.35, .14}, {12.4, .14}}, false, true);
    lathe(mesh, "edge", new double[][] {{12.4, .4}, {12.8, .7}, {13.3, .3}}, false, true);
  }

  private static void glowFlare(EquipmentObjMesh mesh) {
    mesh.group("grip");
    lathe(mesh, "body", new double[][] {{1, .9}, {9.2, .9}}, false, true);
    mesh.group("cap");
    lathe(mesh, "edge", new double[][] {{9.2, 1.9}, {9.8, 1.9}}, false, true);
    lathe(mesh, "cap", new double[][] {{9.8, 1.8}, {12.5, .35}}, false, true);
    lathe(mesh, "light", new double[][] {{12.5, .35}, {13.1, .18}}, false, true);
    fuse(mesh, new P(8, 1, 8), new P(8.5, .5, 8), new P(10, 1.1, 8), new P(10.7, 2, 8));
  }

  private static void gauge(EquipmentObjMesh mesh) {
    mesh.group("case");
    VanillaEquipmentMesh.caseShell(mesh, "metal");
    mesh.group("back");
    disc(mesh, "body", 4.12, 7.61, -1);
    mesh.group("face");
    disc(mesh, "face", 4.12, 8.39, 1);
    mesh.group("pins");
    for (int x : new int[] {-1, 1})
      for (int y : new int[] {-1, 1})
        mesh.beam(
            "dark",
            new P(8 + x * 3.35, 8 + y * 3.35, 8),
            new P(8 + x * 4.7, 8 + y * 4.7, 8),
            .35,
            4);
  }

  private static void bomb(EquipmentObjMesh mesh) {
    double[][] rings = {
      {-4.1, .15},
      {-3.55, 2.05},
      {-2.05, 3.55},
      {-.95, 3.99},
      {-.95, 4.15},
      {.95, 4.15},
      {.95, 3.99},
      {2.05, 3.55},
      {3.55, 2.05},
      {4.1, .15}
    };
    for (int ring = 1; ring < rings.length; ring++) {
      boolean wrapping = ring >= 4 && ring <= 6;
      mesh.group(wrapping ? "wrapping" : "body");
      for (int side = 0; side < 12; side++) {
        double a = side * Math.PI / 6, b = (side + 1) * Math.PI / 6;
        P[] points = {
          bombPoint(rings[ring - 1], a), bombPoint(rings[ring - 1], b),
          bombPoint(rings[ring], b), bombPoint(rings[ring], a)
        };
        P normal = points[1].add(points[2]).scale(.5).add(new P(-8, -7.4, -8));
        if (rings[ring][0] == rings[ring - 1][0])
          normal = bombDirection(new P(0, ring == 4 ? -1 : 1, 0));
        mesh.quad(
            wrapping ? "wrap" : "body",
            points,
            normal,
            new double[][] {
              {side / 12.0, 1}, {(side + 1) / 12.0, 1}, {(side + 1) / 12.0, 0}, {side / 12.0, 0}
            });
      }
    }
    for (int end : new int[] {0, rings.length - 1})
      for (int side = 0; side < 12; side++) {
        P center = bombDirection(new P(0, rings[end][0], 0)).add(new P(8, 7.4, 8));
        P a = bombPoint(rings[end], side * Math.PI / 6),
            b = bombPoint(rings[end], (side + 1) * Math.PI / 6);
        mesh.quad(
            "body",
            new P[] {center, a, b, b},
            bombDirection(new P(0, end == 0 ? -1 : 1, 0)),
            new double[][] {{.5, .5}, {0, 1}, {1, 1}, {1, 1}});
      }
    mesh.group("spikes");
    for (P direction :
        List.of(
            new P(-1, .45, 0),
            new P(1, .45, 0),
            new P(-1, -.45, 0),
            new P(1, -.45, 0),
            new P(0, .2, -1),
            new P(0, -.2, 1))) {
      var axis = direction.vector().normalize();
      var across = new org.joml.Vector3d(axis).cross(0, 1, 0).normalize().mul(.32);
      var up = new org.joml.Vector3d(axis).cross(across).normalize().mul(.32);
      P base = new P(8 + axis.x * 3.85, 7.4 + axis.y * 3.85, 8 + axis.z * 3.85);
      P tip = new P(8 + axis.x * 4.9, 7.4 + axis.y * 4.9, 8 + axis.z * 4.9);
      P[] corners = new P[4];
      for (int side = 0; side < 4; side++) {
        double angle = side * Math.PI / 2;
        corners[side] =
            base.add(
                new P(
                    across.x * Math.cos(angle) + up.x * Math.sin(angle),
                    across.y * Math.cos(angle) + up.y * Math.sin(angle),
                    across.z * Math.cos(angle) + up.z * Math.sin(angle)));
      }
      for (int side = 0; side < 4; side++)
        mesh.quad(
            "dark",
            new P[] {corners[side], corners[(side + 1) % 4], tip, tip},
            corners[side].add(base.scale(-1)),
            new double[][] {{0, 1}, {1, 1}, {.5, 0}, {.5, 0}});
    }
    mesh.group("collar");
    lathe(mesh, "dark", new double[][] {{11.25, .65}, {11.9, .65}}, false, true);
    fuse(mesh, new P(8, 11.9, 8), new P(8.1, 12.8, 8), new P(9.1, 13.3, 8), new P(9.4, 14.1, 8));
  }

  private static P bombPoint(double[] ring, double angle) {
    return bombDirection(new P(ring[1] * Math.cos(angle), ring[0], ring[1] * Math.sin(angle)))
        .add(new P(8, 7.4, 8));
  }

  private static P bombDirection(P point) {
    double angle = Math.toRadians(30);
    return new P(
        point.x() * Math.cos(angle) - point.y() * Math.sin(angle),
        point.x() * Math.sin(angle) + point.y() * Math.cos(angle),
        point.z());
  }

  private static void tablet(EquipmentObjMesh mesh) {
    double[] columns = {3.6, 4.7, 6.2, 7.1, 8.9, 10.1, 10.28, 11.4, 12.4};
    double[] rows = {2, 3.6, 3.78, 5.2, 8.2, 11.2, 11.38, 12.5, 14.1};
    for (int row = 0; row < rows.length - 1; row++)
      for (int column = 0; column < columns.length - 1; column++) {
        double depth = tabletInset(column, row);
        P[] front = tabletCell(columns, rows, column, row, depth);
        boolean socket = (column == 2 || column == 3) && (row == 3 || row == 4);
        String material = depth == 0 ? "body" : socket || depth > .4 ? "chip" : "crack";
        mesh.group(socket ? "socket" : depth > 0 ? "recesses" : "salt_body");
        tabletFace(mesh, material, front, 1);
        tabletFace(mesh, "edge", tabletCell(columns, rows, column, row, 1.7), -1);
        for (int side = 0; side < 4; side++) {
          int next = (side + 1) % 4;
          int adjacentColumn = column + (side == 1 ? 1 : side == 3 ? -1 : 0);
          int adjacentRow = row + (side == 2 ? 1 : side == 0 ? -1 : 0);
          boolean boundary =
              adjacentColumn < 0
                  || adjacentColumn >= columns.length - 1
                  || adjacentRow < 0
                  || adjacentRow >= rows.length - 1;
          double adjacentDepth = boundary ? 1.7 : tabletInset(adjacentColumn, adjacentRow);
          if (adjacentDepth <= depth) continue;
          P a = front[side], b = front[next];
          P c = b.add(new P(0, 0, depth - adjacentDepth)),
              d = a.add(new P(0, 0, depth - adjacentDepth));
          P outward = new P(b.y() - a.y(), a.x() - b.x(), 0);
          surface(mesh, boundary ? "edge" : "chip", new P[] {a, b, c, d}, outward);
        }
      }
    mesh.group("green_stone");
    List<P> outline =
        List.of(
            new P(6.5, 5.5, 8.8),
            new P(8.45, 5.65, 8.8),
            new P(8.65, 8.9, 8.8),
            new P(8.1, 10.95, 8.8),
            new P(6.6, 10.8, 8.8),
            new P(6.45, 8, 8.8));
    P center = new P(7.5, 8.1, 9.06);
    for (int edge = 0; edge < outline.size(); edge++) {
      P a = outline.get(edge), b = outline.get((edge + 1) % outline.size());
      mesh.quad(
          "stone",
          new P[] {a, b, center, center},
          new P(0, 0, 1),
          new double[][] {stoneUv(a), stoneUv(b), stoneUv(center), stoneUv(center)});
      surface(
          mesh,
          "stone",
          new P[] {a, a.add(new P(0, 0, -.5)), b.add(new P(0, 0, -.5)), b},
          new P(b.y() - a.y(), a.x() - b.x(), 0));
    }
  }

  private static double tabletInset(int column, int row) {
    if ((column == 2 || column == 3) && (row == 3 || row == 4)) return .55;
    if ((column == 0 && row == 3)
        || (column == 7 && (row == 4 || row == 5))
        || (row == 7 && (column == 1 || column == 2))
        || (row == 0 && column == 7)) return .5;
    if ((column == 5 && row <= 2) || (row == 1 && column >= 5) || (row == 5 && column <= 2))
      return .22;
    return 0;
  }

  private static P[] tabletCell(
      double[] columns, double[] rows, int column, int row, double inset) {
    return new P[] {
      tabletPoint(columns[column], rows[row], inset),
      tabletPoint(columns[column + 1], rows[row], inset),
      tabletPoint(columns[column + 1], rows[row + 1], inset),
      tabletPoint(columns[column], rows[row + 1], inset)
    };
  }

  private static P tabletPoint(double x, double y, double inset) {
    return new P(
        x + .22 * Math.sin((y - 2) * Math.PI / 12.1),
        y + .12 * Math.sin((x - 3.6) * Math.PI / 8.8),
        8.8 + .3 * Math.pow((x - 8) / 4.4, 2) - inset);
  }

  private static void tabletFace(
      EquipmentObjMesh mesh, String material, P[] points, int direction) {
    for (int[] triangle : List.of(new int[] {0, 1, 2}, new int[] {0, 2, 3})) {
      P a = points[triangle[0]], b = points[triangle[1]], c = points[triangle[2]];
      mesh.quad(
          material,
          new P[] {a, b, c, c},
          new P(0, 0, direction),
          new double[][] {tabletUv(a), tabletUv(b), tabletUv(c), tabletUv(c)});
    }
  }

  private static double[] tabletUv(P point) {
    return new double[] {(point.x() - 3.6) / 9.05, (14.22 - point.y()) / 12.22};
  }

  private static double[] stoneUv(P point) {
    return new double[] {(point.x() - 6.4) / 2.3, (11 - point.y()) / 5.6};
  }

  private static void surface(EquipmentObjMesh mesh, String material, P[] points, P outward) {
    for (int[] triangle : List.of(new int[] {0, 1, 2}, new int[] {0, 2, 3}))
      mesh.quad(
          material,
          new P[] {
            points[triangle[0]], points[triangle[1]], points[triangle[2]], points[triangle[2]]
          },
          outward,
          new double[][] {{0, 1}, {1, 1}, {1, 0}, {1, 0}});
  }

  private static void spyglass(EquipmentObjMesh mesh) {
    mesh.group("grip");
    lathe(mesh, "grip", new double[][] {{.8, 1.25}, {7.6, 1.25}}, false, true);
    mesh.group("draw_tube");
    lathe(mesh, "metal", new double[][] {{7.6, 1.45}, {10.5, 1.45}}, false, true);
    mesh.group("objective");
    lathe(mesh, "edge", new double[][] {{10.5, 1.85}, {14, 1.85}}, false, false);
    lathe(mesh, "dark", new double[][] {{13.65, 1.5}, {14, 1.5}}, true, false);
    rim(mesh, "metal", 14, 1.85, 1.5);
    mesh.group("lens");
    lathe(mesh, "glass", new double[][] {{13.6, 1.5}, {13.65, 1.5}}, false, true);
    mesh.group("eyepiece");
    lathe(mesh, "dark", new double[][] {{.4, 1.4}, {.8, 1.4}}, false, true);
  }

  private static void spectreFlare(EquipmentObjMesh mesh) {
    mesh.group("grip");
    lathe(mesh, "body", new double[][] {{1, 1}, {6.8, 1}}, false, true);
    mesh.group("collar");
    lathe(mesh, "orange", new double[][] {{6.8, 1.8}, {8.1, 1.8}}, false, true);
    lathe(mesh, "metal", new double[][] {{8.1, 1.85}, {8.6, 1.85}}, false, true);
    spectre(mesh, UnaryOperator.identity(), 1);
    fuse(mesh, new P(8, 1, 8), new P(8.6, .5, 8), new P(10, 1.2, 8), new P(10.8, 2.2, 8));
  }

  static void bottledSpectre(EquipmentObjMesh mesh, P base, double scale) {
    P sourceBase = new P(8, 10.85, 8);
    spectre(mesh, point -> point.add(sourceBase.scale(-1)).scale(scale).add(base), scale);
  }

  private static void spectre(EquipmentObjMesh mesh, UnaryOperator<P> place, double scale) {
    mesh.group("spectre_body");
    P low = place.apply(new P(7.25, 10.85, 7.4));
    P high = place.apply(new P(8.75, 12.2, 8.6));
    box(mesh, "spectre_body", low.x(), low.y(), low.z(), high.x(), high.y(), high.z());
    mesh.group("spectre_head");
    P[] headFront = {
      new P(6.95, 12.2, 9.65), new P(9.05, 12.2, 9.65), new P(9.05, 14, 9.65), new P(6.95, 14, 9.65)
    };
    P[] front = new P[4], back = new P[4];
    for (int corner = 0; corner < 4; corner++) {
      P p = headFront[corner];
      front[corner] = place.apply(p);
      back[corner] = place.apply(new P(p.x(), p.y(), 7.6));
    }
    for (int side = 0; side < 4; side++) {
      int next = (side + 1) % 4;
      P a = front[side], b = front[next];
      surface(
          mesh,
          "spectre_body",
          new P[] {a, back[side], back[next], b},
          new P(b.y() - a.y(), a.x() - b.x(), 0));
    }
    mesh.quad(
        "spectre_body", back, new P(0, 0, -1), new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
    mesh.quad(
        "spectre_head", front, new P(0, 0, 1), new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
    mesh.group("spectre_wings");
    for (int side : new int[] {-1, 1}) {
      mesh.plate(
          "spectre_wing",
          "spectre_wing",
          List.of(
              place.apply(new P(8 + side * .65, 11.15, 7.9)),
              place.apply(new P(8 + side * 2.1, 11.4, 7.65)),
              place.apply(new P(8 + side * 2.05, 12.45, 7.5)),
              place.apply(new P(8 + side * .6, 12, 7.9))),
          .18 * scale);
      mesh.beam(
          "spectre_eyes",
          place.apply(new P(8 + side * .65, 13.6, 9.3)),
          place.apply(new P(8 + side * .9, 14.15, 9.6)),
          .07 * scale,
          4);
    }
  }

  private static void bottleGlass(EquipmentObjMesh mesh) {
    mesh.group("bottle");
    lathe(
        mesh,
        "glass",
        new double[][] {
          {8.15, .9}, {9.25, .9}, {9.9, 1.9}, {10.6, 2.7}, {13.8, 2.7}, {14.8, 1.9}, {15.2, .8}
        },
        false,
        false);
    lathe(
        mesh,
        "glass",
        new double[][] {
          {8.3, .7},
          {9.25, .7},
          {10.02, 1.78},
          {10.7, 2.5},
          {13.7, 2.5},
          {14.65, 1.78},
          {15.02, .65}
        },
        true,
        false);
    cap(mesh, "glass", 15.2, .8, 1);
    cap(mesh, "glass", 15.02, .65, -1);
    cap(mesh, "glass", 8.15, .9, -1);
    cap(mesh, "glass", 8.3, .7, 1);
  }

  private static void fuse(EquipmentObjMesh mesh, P... points) {
    mesh.group("fuse");
    mesh.tube("fuse", List.of(points), .18, 4, 2);
  }

  private static void box(
      EquipmentObjMesh mesh,
      String material,
      double x,
      double y,
      double z,
      double endX,
      double endY,
      double endZ) {
    double center = (z + endZ) / 2;
    mesh.plate(
        material,
        material,
        List.of(
            new P(x, y, center),
            new P(endX, y, center),
            new P(endX, endY, center),
            new P(x, endY, center)),
        endZ - z);
  }

  private static void disc(
      EquipmentObjMesh mesh, String material, double radius, double z, int side) {
    for (int segment = 0; segment < 16; segment++) {
      double a = segment * Math.PI / 8, b = (segment + 1) * Math.PI / 8;
      mesh.quad(
          material,
          new P[] {
            new P(8, 8, z),
            new P(8 + radius * Math.cos(a), 8 + radius * Math.sin(a), z),
            new P(8 + radius * Math.cos(b), 8 + radius * Math.sin(b), z),
            new P(8 + radius * Math.cos(b), 8 + radius * Math.sin(b), z)
          },
          new P(0, 0, side),
          new double[][] {
            {.5, .5},
            {.5 + .5 * Math.cos(a), .5 - .5 * Math.sin(a)},
            {.5 + .5 * Math.cos(b), .5 - .5 * Math.sin(b)},
            {.5 + .5 * Math.cos(b), .5 - .5 * Math.sin(b)}
          });
    }
  }

  private static void lathe(
      EquipmentObjMesh mesh, String material, double[][] profile, boolean inner, boolean caps) {
    for (int ring = 1; ring < profile.length; ring++)
      for (int side = 0; side < 8; side++) {
        double a = side * Math.PI / 4, b = (side + 1) * Math.PI / 4;
        double[] lower = profile[ring - 1], upper = profile[ring];
        double length = profile[profile.length - 1][0] - profile[0][0];
        double lowerV = 1 - (lower[0] - profile[0][0]) / length;
        double upperV = 1 - (upper[0] - profile[0][0]) / length;
        mesh.quad(
            material,
            new P[] {
              radial(lower[0], lower[1], a),
              radial(lower[0], lower[1], b),
              radial(upper[0], upper[1], b),
              radial(upper[0], upper[1], a)
            },
            new P(
                    Math.cos((a + b) / 2),
                    (lower[1] - upper[1]) / (upper[0] - lower[0]),
                    Math.sin((a + b) / 2))
                .scale(inner ? -1 : 1),
            new double[][] {
              {side / 8.0, lowerV},
              {(side + 1) / 8.0, lowerV},
              {(side + 1) / 8.0, upperV},
              {side / 8.0, upperV}
            });
      }
    if (caps)
      for (int end : new int[] {0, profile.length - 1}) {
        double[] ring = profile[end];
        cap(mesh, material, ring[0], ring[1], (end == 0 ? -1 : 1) * (inner ? -1 : 1));
      }
  }

  private static void cap(
      EquipmentObjMesh mesh, String material, double y, double radius, int direction) {
    for (int side = 0; side < 8; side++) {
      P first = radial(y, radius, side * Math.PI / 4),
          second = radial(y, radius, (side + 1) * Math.PI / 4);
      mesh.quad(
          material,
          new P[] {new P(8, y, 8), first, second, second},
          new P(0, direction, 0),
          new double[][] {{.5, .5}, {0, 1}, {1, 1}, {1, 1}});
    }
  }

  private static P radial(double y, double radius, double angle) {
    return new P(8 + radius * Math.cos(angle), y, 8 + radius * Math.sin(angle));
  }

  private static void rim(
      EquipmentObjMesh mesh, String material, double y, double outer, double inner) {
    for (int side = 0; side < 8; side++) {
      double a = side * Math.PI / 4, b = (side + 1) * Math.PI / 4;
      mesh.quad(
          material,
          new P[] {
            radial(y, inner, a), radial(y, outer, a), radial(y, outer, b), radial(y, inner, b)
          },
          new P(0, 1, 0),
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
    }
  }
}
