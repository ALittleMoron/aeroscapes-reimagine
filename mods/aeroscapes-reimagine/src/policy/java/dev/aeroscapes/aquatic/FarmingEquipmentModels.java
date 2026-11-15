package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** Native farming functions and icons with separately constructed hand equipment. */
public final class FarmingEquipmentModels {
  static final List<String> ROOTS =
      List.of(
          "agricraft:journal",
          "agricraft:clipper",
          "agricraft:magnifying_glass",
          "agricraft:wooden_rake",
          "agricraft:iron_rake",
          "agricraft:trowel",
          "agricraft:seed_bag",
          "agricraft:greenhouse_monitor",
          "animalhusbandry:brush",
          "animalhusbandry:magnifying_glass",
          "animalhusbandry:farm_ledger",
          "animalhusbandry:animal_medicine",
          "animalhusbandry:fertility_potion");
  static final List<String> STATES =
      List.of(
          "agricraft:journal",
          "agricraft:clipper",
          "agricraft:magnifying_glass",
          "agricraft:wooden_rake",
          "agricraft:iron_rake",
          "agricraft:trowel",
          "agricraft:trowel_plant",
          "agricraft:seed_bag",
          "agricraft:seed_bag_empty",
          "agricraft:seed_bag_full",
          "agricraft:greenhouse_monitor",
          "animalhusbandry:brush",
          "animalhusbandry:magnifying_glass",
          "animalhusbandry:farm_ledger",
          "animalhusbandry:animal_medicine",
          "animalhusbandry:fertility_potion");

  private static final List<String> MATERIALS =
      List.of(
          "wood",
          "wood_dark",
          "iron",
          "iron_edge",
          "leather",
          "red_wood",
          "cord",
          "pages",
          "indigo",
          "ochre",
          "bristles",
          "green",
          "soil",
          "pink",
          "red",
          "blue",
          "glass",
          "glass_dark",
          "glass_pink",
          "medicine",
          "white",
          "iron_dark",
          "gold");
  private static final int[] COLORS = {
    0xFF8B642E, 0xFF51401E, 0xFFA8ABA9, 0xFFE2E4DD, 0xFF9B551F, 0xFF6A2014,
    0xFFC5AD81, 0xFFE1DBBC, 0xFF211448, 0xFFAE5F30, 0xFFD6B28B, 0xFF44972C,
    0xFF614325, 0xFFEAB8A3, 0xFFBA2C28, 0xFF477FA5, 0x607BAFB5, 0x80576369,
    0x80F093D6, 0xFFBB8D51, 0xFFEEE8D5, 0xFF363D3E, 0xFFD7AD39
  };
  private static final String TEXTURE = "agricraft:item/aeroscapes_farming/materials";

  private FarmingEquipmentModels() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional --check");
    boolean check = arguments.length == 2 && arguments[1].equals("--check");
    if (arguments.length == 2 && !check) throw new IllegalArgumentException(arguments[1]);
    Map<Path, byte[]> output = new LinkedHashMap<>();
    addResources(
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets"),
        output);
    for (var entry : output.entrySet()) {
      if (check) {
        if (!Files.isRegularFile(entry.getKey())
            || !Arrays.equals(Files.readAllBytes(entry.getKey()), entry.getValue()))
          throw new IllegalStateException("Stale farming model: " + entry.getKey());
      } else {
        Files.createDirectories(entry.getKey().getParent());
        Files.write(entry.getKey(), entry.getValue());
      }
    }
    System.out.println(
        "Farming equipment resources " + (check ? "verified: " : "generated: ") + output.size());
  }

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Map<String, JsonObject> models = new LinkedHashMap<>();
    StringBuilder library = new StringBuilder();
    for (String material : MATERIALS)
      library
          .append("newmtl ")
          .append(material)
          .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd ")
          .append(TEXTURE)
          .append("\n\n");
    for (String namespace : List.of("agricraft", "animalhusbandry"))
      EquipmentModels.text(
          output,
          assets.resolve(namespace + "/models/item/aeroscapes_farming/equipment.mtl"),
          library.toString());
    output.put(
        assets.resolve("agricraft/textures/item/aeroscapes_farming/materials.png"),
        EquipmentModels.png(materials()));
    output.put(
        assets
            .resolve(
                "../../aeroscapes-reimagine-atmosphere/assets/agricraft/textures/item/greenhouse_monitor.png")
            .normalize(),
        EquipmentModels.png(monitorIcon()));
    for (String id : STATES) {
      String[] key = id.split(":", 2);
      EquipmentObjMesh mesh = mesh();
      String item = key[1];
      switch (item) {
        case "journal", "farm_ledger" -> book(mesh, item.equals("journal"));
        case "clipper" -> clipper(mesh);
        case "magnifying_glass" -> magnifier(mesh, key[0].equals("animalhusbandry"));
        case "wooden_rake", "iron_rake" -> rake(mesh, item.equals("iron_rake"));
        case "trowel", "trowel_plant" -> trowel(mesh, item.endsWith("_plant"));
        case "seed_bag", "seed_bag_empty", "seed_bag_full" ->
            sack(mesh, item.endsWith("_full") ? 1 : item.endsWith("_empty") ? 0 : .5);
        case "greenhouse_monitor" -> monitor(mesh);
        case "brush" -> brush(mesh);
        case "animal_medicine" -> medicine(mesh);
        case "fertility_potion" -> potion(mesh);
        default -> throw new IllegalStateException(item);
      }
      EquipmentModels.text(
          output,
          assets.resolve(key[0] + "/models/item/aeroscapes_farming/" + item + ".obj"),
          mesh.obj());
      JsonObject model = new JsonObject();
      model.addProperty("loader", "neoforge:obj");
      model.addProperty("model", key[0] + ":models/item/aeroscapes_farming/" + item + ".obj");
      model.addProperty("automatic_culling", false);
      model.addProperty("flip_v", false);
      model.addProperty("ambientocclusion", false);
      model.addProperty("render_type", "minecraft:translucent");
      JsonObject textures = new JsonObject();
      textures.addProperty("particle", TEXTURE);
      model.add("textures", textures);
      model.add("display", display(item));
      models.put(id, model);
    }
    HeldItemModels.add(assets, output, models);
  }

  private static EquipmentObjMesh mesh() {
    EquipmentObjMesh mesh = new EquipmentObjMesh();
    for (int index = 0; index < MATERIALS.size(); index++) {
      int x = index % 8 * 64, y = index / 8 * 64;
      mesh.region(MATERIALS.get(index), x / 512.0, y / 192.0, (x + 64) / 512.0, (y + 64) / 192.0);
      mesh.projectedField(MATERIALS.get(index));
    }
    return mesh;
  }

  private static void book(EquipmentObjMesh mesh, boolean journal) {
    String cover = journal ? "indigo" : "ochre";
    mesh.group("bound_pages_and_board_covers");
    mesh.box("pages", 5.15, 4.3, 7.25, 10.7, 12, 8.75);
    mesh.box(cover, 4.8, 4, 7, 11, 12.3, 7.25);
    mesh.box(cover, 4.8, 4, 8.75, 11, 12.3, 9);
    mesh.box(cover, 4.8, 4, 7.25, 5.15, 12.3, 8.75);
    if (!journal) {
      mesh.group("two_gold_spine_bands");
      for (double y : new double[] {5.55, 10.15}) {
        mesh.box("gold", 4.75, y, 6.96, 5.45, y + .55, 9.04);
      }
      mesh.group("frontal_bull_skull_and_paired_horns");
      for (int side : new int[] {-1, 1}) {
        mesh.plate(
            "pages",
            "pages",
            List.of(
                new P(8 + side * .7, 10.2, 9.02),
                new P(8 + side * 1.5, 10.55, 9.02),
                new P(8 + side * 1.8, 11.3, 9.02),
                new P(8 + side * 1.45, 11.05, 9.02),
                new P(8 + side * .5, 10.75, 9.02),
                new P(8 + side * .3, 10.2, 9.02)),
            .06);
      }
      mesh.plate(
          "white",
          "pages",
          List.of(
              new P(7.15, 10.55, 9.05),
              new P(8.85, 10.55, 9.05),
              new P(9, 9.7, 9.05),
              new P(8.45, 9.15, 9.05),
              new P(8.35, 7.9, 9.05),
              new P(8, 7.55, 9.05),
              new P(7.65, 7.9, 9.05),
              new P(7.55, 9.15, 9.05),
              new P(7, 9.7, 9.05)),
          .1);
      for (int side : new int[] {-1, 1}) {
        mesh.plate(
            "iron_dark",
            "iron_dark",
            List.of(
                new P(8 + side * .2, 10.1, 9.12),
                new P(8 + side * .78, 10.05, 9.12),
                new P(8 + side * .68, 9.55, 9.12),
                new P(8 + side * .25, 9.65, 9.12)),
            .04);
      }
      mesh.plate(
          "iron_dark",
          "iron_dark",
          List.of(new P(7.8, 8.45, 9.12), new P(8.2, 8.45, 9.12), new P(8, 8.05, 9.12)),
          .04);
    }
  }

  private static void clipper(EquipmentObjMesh mesh) {
    mesh.group("two_connected_pruning_snip_halves");
    mesh.plate(
        "red_wood",
        "wood_dark",
        List.of(
            new P(6, 3.4, 7.65), new P(6.8, 3.2, 7.65), new P(8.5, 8.8, 7.65), new P(7.6, 9, 7.65)),
        .8);
    mesh.plate(
        "red_wood",
        "wood_dark",
        List.of(
            new P(9.1, 3.3, 8.35),
            new P(9.9, 3.6, 8.35),
            new P(8.4, 9, 8.35),
            new P(7.5, 8.8, 8.35)),
        .8);
    mesh.plate(
        "iron",
        "iron_edge",
        List.of(
            new P(7.55, 8.4, 7.65),
            new P(8.45, 8.4, 7.65),
            new P(7.4, 12.7, 7.65),
            new P(6.45, 14.1, 7.65),
            new P(6.6, 11, 7.65)),
        .35);
    mesh.plate(
        "iron",
        "iron_edge",
        List.of(
            new P(7.55, 8.4, 8.35),
            new P(8.5, 8.4, 8.35),
            new P(9.35, 11.7, 8.35),
            new P(8.7, 13.3, 8.35),
            new P(8.6, 11.2, 8.35)),
        .35);
    mesh.beam("iron_edge", new P(8, 8.8, 7.15), new P(8, 8.8, 8.85), .45, 8);
  }

  private static void magnifier(EquipmentObjMesh mesh, boolean farm) {
    mesh.group("square_grip_socket_lens_and_open_rim");
    mesh.box("wood", 7.45, 2.5, 7.45, 8.55, 7.5, 8.55);
    mesh.box(farm ? "iron_dark" : "iron", 7.35, 7, 7.35, 8.65, 8, 8.65);
    String rim = farm ? "iron_dark" : "iron";
    double outer = 2.65, inner = 2.17, y = 10;
    for (int side = 0; side < 12; side++) {
      double a = side * Math.PI / 6, b = (side + 1) * Math.PI / 6;
      P p = new P(8 + outer * Math.cos(a), y + outer * Math.sin(a), 8);
      P q = new P(8 + outer * Math.cos(b), y + outer * Math.sin(b), 8);
      P r = new P(8 + inner * Math.cos(b), y + inner * Math.sin(b), 8);
      P s = new P(8 + inner * Math.cos(a), y + inner * Math.sin(a), 8);
      mesh.plate(rim, farm ? "iron" : "iron_edge", List.of(p, q, r, s), .55);
      P center = new P(8, y, 8);
      mesh.quad(farm ? "glass_dark" : "glass", new P[] {center, s, r, r}, new P(0, 0, 1), null);
      mesh.quad(farm ? "glass_dark" : "glass", new P[] {center, r, s, s}, new P(0, 0, -1), null);
    }
  }

  private static void rake(EquipmentObjMesh mesh, boolean iron) {
    String working = iron ? "iron" : "wood";
    mesh.group("square_handle_and_continuous_rake_mount");
    mesh.box("wood", 7.5, 2.2, 7.5, 8.5, 9.7, 8.5);
    mesh.box(iron ? "iron" : "wood_dark", 7.35, 9, 7.35, 8.65, 10.4, 8.65);
    mesh.box(working, 4.8, 10.1, 7.55, 11.2, 10.9, 8.45);
    mesh.group("separate_forward_bent_cultivating_tines");
    for (double x : new double[] {5.15, 8, 10.85}) {
      mesh.box(working, x - .35, 10.9, 7.55, x + .35, 12.4, 8.45);
      mesh.box(working, x - .35, 11.75, 6.15, x + .35, 12.4, 7.55);
    }
  }

  private static void trowel(EquipmentObjMesh mesh, boolean plant) {
    mesh.group("square_wood_grip_tang_and_concave_iron_scoop");
    mesh.box("wood", 7.4, 2.3, 7.4, 8.6, 7.2, 8.6);
    mesh.box("cord", 7.3, 6.25, 7.3, 8.7, 6.8, 8.7);
    mesh.box("iron", 7.65, 6.8, 7.7, 8.35, 9, 8.3);
    P[][] cross = {
      {new P(7.5, 8.2, 8.3), new P(8, 8.2, 7.9), new P(8.5, 8.2, 8.3)},
      {new P(6.2, 10.2, 8.6), new P(8, 10.2, 7.8), new P(9.8, 10.2, 8.6)},
      {new P(6.45, 12.4, 8.6), new P(8, 12.4, 7.8), new P(9.55, 12.4, 8.6)},
      {new P(7.8, 14, 8.15), new P(8, 14.1, 7.95), new P(8.2, 14, 8.15)}
    };
    for (int row = 1; row < cross.length; row++)
      for (int side = 0; side < 2; side++) {
        P[] points = {
          cross[row - 1][side], cross[row - 1][side + 1], cross[row][side + 1], cross[row][side]
        };
        surface(mesh, "iron_edge", points, new P(0, 0, 1));
        P[] back = Arrays.stream(points).map(p -> p.add(new P(0, 0, -.22))).toArray(P[]::new);
        surface(mesh, "iron", back, new P(0, 0, -1));
      }
    for (int side : new int[] {0, 2})
      for (int row = 1; row < cross.length; row++) {
        P a = cross[row - 1][side], b = cross[row][side];
        surface(
            mesh,
            "iron",
            new P[] {a, b, b.add(new P(0, 0, -.22)), a.add(new P(0, 0, -.22))},
            new P(side == 0 ? -1 : 1, 0, 0));
      }
    if (plant) {
      mesh.group("root_ball_and_carried_green_plant");
      mesh.shell(
          "soil",
          new P[][] {
            ring(9.9, 1.05, .7, 8.8), ring(11.1, 1.25, .85, 8.8), ring(11.65, .9, .65, 8.8)
          });
      mesh.box("green", 7.82, 11.4, 8.7, 8.18, 14.4, 9.06);
      mesh.beam("green", new P(8, 12.8, 8.9), new P(6.8, 13.8, 8.9), .28, 4);
      mesh.beam("green", new P(8, 13.3, 8.9), new P(9.1, 14.25, 8.9), .3, 4);
    }
  }

  private static void surface(EquipmentObjMesh mesh, String material, P[] points, P outward) {
    mesh.quad(material, new P[] {points[0], points[1], points[2], points[2]}, outward, null);
    mesh.quad(material, new P[] {points[0], points[2], points[3], points[3]}, outward, null);
  }

  private static P[] ring(double y, double width, double depth, double z) {
    return EquipmentObjMesh.octagonalRing(8, y, z, width, depth);
  }

  private static void sack(EquipmentObjMesh mesh, double fill) {
    mesh.group("folded_leather_sack_belly_and_gathered_neck");
    double depth = 1.1 + fill * 1.3;
    double width = .9 + fill * .1;
    mesh.shell(
        "leather",
        new P[][] {
          sackRing(2.6, 1.7 * width, depth * .65, .1),
              sackRing(3.15, 2.85 * width, depth * .9, -.2),
          sackRing(4.5, 3.3 * width, depth, -.15), sackRing(6.4, 2.9 * width, depth * .96, .15),
          sackRing(8.1, 1.7, depth * .65, .1), ring(9.3, 1, .8, 8),
          ring(10.1, .85, .7, 8), ring(10.7, 1.35, .9, 8)
        });
    mesh.group("cord_cinch_and_short_tied_ends");
    mesh.shell("cord", new P[][] {ring(9.45, 1.15, .95, 8), ring(9.85, 1.15, .95, 8)});
    mesh.beam("cord", new P(8.7, 9.65, 8.7), new P(10.2, 8.4, 8.9), .14, 4);
    mesh.beam("cord", new P(8.7, 9.65, 8.7), new P(9.6, 7.9, 8.8), .14, 4);
  }

  private static P[] sackRing(double y, double width, double depth, double shift) {
    P[] result = ring(y, width, depth, 8);
    for (int side = 0; side < result.length; side++) {
      P point = result[side];
      double fold = side % 3 == 0 ? .88 : 1;
      result[side] = new P(8 + (point.x() - 8) * fold + shift, y, 8 + (point.z() - 8) * fold);
    }
    return result;
  }

  private static void monitor(EquipmentObjMesh mesh) {
    mesh.group("single_wood_cased_twin_glass_environment_instrument");
    mesh.box("wood_dark", 5.2, 3.3, 7.2, 10.8, 12, 8.5);
    mesh.group("continuous_metal_bezel_around_inset_readout");
    mesh.box("iron", 5.35, 3.5, 8.5, 5.65, 11.8, 9.35);
    mesh.box("iron", 10.35, 3.5, 8.5, 10.65, 11.8, 9.35);
    mesh.box("iron", 5.65, 3.5, 8.5, 10.35, 4.1, 9.35);
    mesh.box("iron", 5.65, 10.45, 8.5, 10.35, 11.8, 9.35);
    mesh.box("pages", 5.65, 4.1, 8.7, 10.35, 10.45, 8.92);
    for (int side = 0; side < 2; side++) {
      double x = 6.7 + side * 2.6;
      String color = side == 0 ? "red" : "blue";
      mesh.box(color, x - .16, 5, 8.94, x + .16, side == 0 ? 8.65 : 7.6, 9.14);
      mesh.beam(color, new P(x, 4.95, 9.06), new P(x, 4.95, 9.16), .4, 8);
      for (int tick = 0; tick < 4; tick++)
        mesh.box("wood_dark", x + .45, 6 + tick, 8.94, x + .8, 6.13 + tick, 8.99);
    }
    mesh.plate(
        "green",
        "green",
        List.of(
            new P(6.8, 10.5, 9.39),
            new P(8, 11.45, 9.39),
            new P(9.2, 10.5, 9.39),
            new P(8.9, 10.5, 9.39),
            new P(8, 11.15, 9.39),
            new P(7.1, 10.5, 9.39)),
        .08);
    mesh.box("glass", 5.65, 4.1, 9.19, 10.35, 10.45, 9.27);
  }

  private static void brush(EquipmentObjMesh mesh) {
    mesh.group("broad_wooden_brush_back_and_straw_bristle_bundle");
    mesh.box("wood", 4.5, 5.4, 6.1, 11.5, 6.5, 9.9);
    mesh.box("wood_dark", 4.7, 5, 6.3, 11.3, 5.4, 9.7);
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 6; column++)
        mesh.box(
            "bristles",
            4.85 + column * 1.04,
            3.45,
            6.45 + row * 1.02,
            5.66 + column * 1.04,
            5.15,
            7.25 + row * 1.02);
    mesh.group("raised_connected_palm_handle");
    mesh.box("wood_dark", 5.15, 6.5, 7.15, 6.05, 8.1, 8.85);
    mesh.box("wood_dark", 9.95, 6.5, 7.15, 10.85, 8.1, 8.85);
    mesh.box("wood", 5.15, 8.1, 7.15, 10.85, 9.1, 8.85);
  }

  private static void medicine(EquipmentObjMesh mesh) {
    mesh.group("open_wooden_medicine_bowl_and_visible_herbal_contents");
    int sides = 12;
    P[][] rings = new P[3][sides];
    double[] y = {3.1, 3.8, 5.7}, radius = {1.6, 2.3, 3};
    for (int row = 0; row < rings.length; row++)
      for (int side = 0; side < sides; side++) {
        double angle = side * Math.PI * 2 / sides;
        rings[row][side] =
            new P(8 + radius[row] * Math.cos(angle), y[row], 8 + radius[row] * Math.sin(angle));
      }
    for (int side = 0; side < sides; side++) {
      int next = (side + 1) % sides;
      P outward = rings[2][side].add(new P(-8, -5.7, -8));
      for (int row = 1; row < rings.length; row++)
        surface(
            mesh,
            "wood",
            new P[] {
              rings[row - 1][side], rings[row - 1][next], rings[row][next], rings[row][side]
            },
            outward);
      P a = new P(8 + .9 * (rings[2][side].x() - 8), 5.7, 8 + .9 * (rings[2][side].z() - 8));
      P b = new P(8 + .9 * (rings[2][next].x() - 8), 5.7, 8 + .9 * (rings[2][next].z() - 8));
      mesh.quad("wood_dark", new P[] {rings[2][side], rings[2][next], b, a}, new P(0, 1, 0), null);
      P c = new P(8 + .82 * (a.x() - 8), 4.4, 8 + .82 * (a.z() - 8));
      P d = new P(8 + .82 * (b.x() - 8), 4.4, 8 + .82 * (b.z() - 8));
      surface(mesh, "wood_dark", new P[] {a, b, d, c}, outward.scale(-1));
      mesh.quad(
          "wood_dark",
          new P[] {new P(8, 3.1, 8), rings[0][side], rings[0][next], rings[0][next]},
          new P(0, -1, 0),
          null);
    }
    mesh.shell("medicine", new P[][] {ring(4.8, 2.3, 2.3, 8), ring(5.15, 2.45, 2.45, 8)});
    mesh.box("bristles", 6.3, 5.15, 7, 7.4, 5.5, 8.1);
    mesh.box("pink", 8.6, 5.15, 8.1, 9.55, 5.55, 9.1);
    mesh.box("green", 7.7, 5.15, 6.8, 8.15, 5.4, 8.65);
  }

  private static void potion(EquipmentObjMesh mesh) {
    mesh.group("pink_fertility_vial_with_glass_shoulder_neck_and_stopper");
    mesh.shell(
        "glass_pink",
        new P[][] {
          ring(3.8, .85, .85, 8),
          ring(4.4, 1.85, 1.55, 8),
          ring(7, 1.85, 1.55, 8),
          ring(8.5, .7, .65, 8),
          ring(10.2, .7, .65, 8)
        });
    mesh.shell(
        "pink", new P[][] {ring(4, .75, .7, 8), ring(4.5, 1.6, 1.3, 8), ring(6.8, 1.6, 1.3, 8)});
    mesh.box("cord", 7.27, 10, 7.27, 8.73, 10.6, 8.73);
  }

  private static JsonObject display(String item) {
    JsonObject display = new JsonObject();
    boolean broad = List.of("journal", "farm_ledger", "greenhouse_monitor").contains(item);
    float scale = broad ? .65f : item.equals("brush") ? .7f : .68f;
    Vector3f grip =
        switch (item) {
          case "journal", "farm_ledger" -> new Vector3f(10.4f, 5.8f, 8);
          case "greenhouse_monitor" -> new Vector3f(10.25f, 4.7f, 8);
          case "seed_bag", "seed_bag_empty", "seed_bag_full" -> new Vector3f(8, 2.6f, 8);
          case "brush" -> new Vector3f(8, 8.6f, 8);
          case "animal_medicine" -> new Vector3f(8, 3.1f, 8);
          case "fertility_potion" -> new Vector3f(8, 3.8f, 8);
          case "clipper" -> new Vector3f(8, 5.4f, 8);
          default -> new Vector3f(8, 4.5f, 8);
        };
    for (String context :
        List.of(
            "firstperson_righthand",
            "firstperson_lefthand",
            "thirdperson_righthand",
            "thirdperson_lefthand")) {
      JsonObject transform = new JsonObject();
      transform.add(
          "rotation",
          vector(
              broad ? 0 : item.equals("brush") ? 168 : item.equals("animal_medicine") ? 0 : -12,
              broad ? -20 : -12,
              broad ? -8 : item.equals("brush") ? -90 : 0));
      transform.add("scale", vector(scale, scale, scale));
      display.add(context, transform);
      JsonObject single = new JsonObject();
      single.add(context, transform);
      Vector3f anchor = new Vector3f(grip);
      if (broad && context.endsWith("lefthand")) anchor.x = 16 - anchor.x;
      HeldItemModels.anchorHeld(single, anchor, new Vector3f(0, 0, 0));
    }
    return display;
  }

  private static JsonArray vector(float x, float y, float z) {
    JsonArray result = new JsonArray();
    result.add(x);
    result.add(y);
    result.add(z);
    return result;
  }

  private static BufferedImage materials() {
    BufferedImage image = new BufferedImage(512, 192, BufferedImage.TYPE_INT_ARGB);
    for (int material = 0; material < COLORS.length; material++)
      for (int y = 0; y < 64; y++)
        for (int x = 0; x < 64; x++) {
          int color = COLORS[material];
          int variation =
              ((x / 3 * 11 + y / 4 * 7 + material * 13) % 5 - 2)
                  * (material < 2 ? 3 : material < 6 ? 2 : 1);
          int shaded = EquipmentModels.shaded(color & 0xFFFFFF, variation);
          image.setRGB(
              material % 8 * 64 + x, material / 8 * 64 + y, color & 0xFF000000 | shaded & 0xFFFFFF);
        }
    return image;
  }

  private static BufferedImage monitorIcon() {
    BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int y = 1; y < 15; y++)
      for (int x = 3; x < 13; x++) {
        int color = x == 3 || x == 12 || y == 1 || y == 14 ? 0xFF51401E : 0xFFADAFA8;
        if (x > 4 && x < 11 && y > 3 && y < 13) color = 0xFFE1DBBC;
        image.setRGB(x, y, color);
      }
    for (int y = 6; y < 12; y++) image.setRGB(6, y, 0xFFBA2C28);
    for (int y = 8; y < 12; y++) image.setRGB(9, y, 0xFF477FA5);
    for (int x : new int[] {5, 6, 7}) image.setRGB(x, 12, 0xFFBA2C28);
    for (int x : new int[] {8, 9, 10}) image.setRGB(x, 12, 0xFF477FA5);
    image.setRGB(7, 3, 0xFF44972C);
    image.setRGB(8, 2, 0xFF44972C);
    image.setRGB(9, 3, 0xFF44972C);
    for (int y : new int[] {5, 7, 9}) {
      image.setRGB(7, y, 0xFF51401E);
      image.setRGB(10, y, 0xFF51401E);
    }
    return image;
  }
}
