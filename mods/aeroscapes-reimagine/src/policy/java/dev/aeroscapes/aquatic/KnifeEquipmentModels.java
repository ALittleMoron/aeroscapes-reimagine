package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import dev.aeroscapes.atmosphere.EquipmentStyle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** Solid single-edged utility knives with square grips and native material palettes. */
final class KnifeEquipmentModels {
  static final List<String> TIERS = List.of("flint", "iron", "golden", "diamond", "netherite");
  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/knives/";
  private static final String TEXTURES = "aeroscapes_reimagine:item/knives/";

  private KnifeEquipmentModels() {}

  static boolean supports(String id) {
    return TIERS.stream().anyMatch(tier -> id.equals("farmersdelight:" + tier + "_knife"));
  }

  static JsonObject model(String id, JsonObject original) {
    JsonObject model = original.deepCopy();
    model.addProperty("loader", "neoforge:obj");
    model.addProperty("model", DIRECTORY + tier(id) + ".obj");
    model.addProperty("automatic_culling", false);
    model.addProperty("emissive_ambient", false);
    model.addProperty("flip_v", false);
    model.addProperty("ambientocclusion", false);
    model.addProperty("render_type", "minecraft:cutout");
    model.getAsJsonObject("textures").addProperty("particle", "#layer0");
    JsonObject display = EquipmentSculpture.transforms(id, EquipmentStyle.KNIFE, original);
    Vector3f grip = new Vector3f(8, 2.75f, 8);
    HeldItemModels.resizeHeld(display, .68f, .7f, grip);
    HeldItemModels.anchorHeld(display, grip);
    // ItemInHandLayer's attachment sits two pixels below and in front of the palm.
    for (var entry : display.entrySet()) {
      var translation = entry.getValue().getAsJsonObject().getAsJsonArray("translation");
      translation.set(1, new com.google.gson.JsonPrimitive(translation.get(1).getAsFloat() - 2));
      translation.set(2, new com.google.gson.JsonPrimitive(translation.get(2).getAsFloat() + 2));
    }
    model.add("display", display);
    return model;
  }

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Path directory = assets.resolve("aeroscapes_reimagine/models/item/knives");
    StringBuilder materials = new StringBuilder();
    for (String tier : TIERS) {
      EquipmentObjMesh mesh = new EquipmentObjMesh();
      for (String material : List.of("blade", "edge", "grip", "bolster")) {
        String name = tier + "_" + material;
        mesh.material(material, name);
        int column = material.equals("edge") || material.equals("bolster") ? 8 : 0;
        int row = material.equals("grip") || material.equals("bolster") ? 8 : 0;
        mesh.region(
            name, (column + .25) / 16, (row + .25) / 16, (column + 7.75) / 16, (row + 7.75) / 16);
        materials
            .append("newmtl ")
            .append(name)
            .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd ")
            .append(TEXTURES)
            .append(tier)
            .append("\n\n");
      }
      mesh.group("grip");
      mesh.plate(
          "grip",
          "grip",
          List.of(
              new P(7.15, .7, 8), new P(8.85, .7, 8),
              new P(8.85, 7.5, 8), new P(7.15, 7.5, 8)),
          1.65);
      mesh.group("bolster");
      mesh.plate(
          "bolster",
          "bolster",
          List.of(
              new P(7.05, 7.5, 8), new P(8.95, 7.5, 8),
              new P(8.95, 8.05, 8), new P(7.05, 8.05, 8)),
          1.75);
      mesh.group("blade");
      blade(mesh, tier.equals("flint"));
      output.put(directory.resolve(tier + ".obj"), mesh.obj().getBytes(StandardCharsets.UTF_8));
      output.put(
          assets.resolve("aeroscapes_reimagine/textures/item/knives/" + tier + ".png"),
          EquipmentModels.png(texture(tier)));
    }
    output.put(
        directory.resolve("equipment.mtl"),
        (materials.toString().stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8));
  }

  private static String tier(String id) {
    return id.substring("farmersdelight:".length(), id.length() - "_knife".length());
  }

  private static BufferedImage texture(String tier) throws IOException {
    BufferedImage nativeSprite =
        NativeEquipmentModels.image("farmersdelight:item/" + tier + "_knife");
    BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 16; y++)
      for (int x = 0; x < 16; x++) {
        int color;
        if (y < 8)
          color =
              x >= 8
                  ? nativeSprite.getRGB(tier.equals("flint") ? 7 : 8, tier.equals("flint") ? 7 : 4)
                  : nativeSprite.getRGB(x < 6 ? 8 : 9, 5);
        else if (x >= 8) color = nativeSprite.getRGB(6, 9);
        else if (tier.equals("flint"))
          color =
              nativeSprite.getRGB(
                  y == 10 || y == 11 ? 4 : x < 2 ? 2 : 3,
                  y == 10 || y == 11 ? 11 : x < 2 ? 13 : 12);
        else color = nativeSprite.getRGB(x < 2 ? 4 : x < 6 ? 5 : 4, x < 6 ? 10 : 11);
        if ((color >>> 24) != 255) throw new IOException("Transparent knife material: " + tier);
        image.setRGB(x, y, color);
      }
    return image;
  }

  private static void blade(EquipmentObjMesh mesh, boolean flint) {
    double[][] stations =
        flint
            ? new double[][] {
              {7.95, 6.8, 9},
              {10.2, 6.35, 9.15},
              {12.4, 6.55, 9},
              {14.2, 6.7, 9},
              {15.4, 7.8, 8.9},
              {16, 8.65, 8.8}
            }
            : new double[][] {{7.95, 6.5, 9}, {13.4, 6.5, 9}, {15, 7.25, 9}, {16, 8.7, 8.85}};
    for (int index = 0; index < stations.length - 1; index++) {
      double[] a = stations[index], b = stations[index + 1];
      double insetA = Math.min(.42, (a[2] - a[1]) * .4);
      double insetB = Math.min(.42, (b[2] - b[1]) * .4);
      for (int side : new int[] {-1, 1}) {
        double z = 8 + side * (flint ? .4 : .32);
        mesh.quad(
            "blade",
            new P[] {
              new P(a[1] + insetA, a[0], z), new P(a[2], a[0], z),
              new P(b[2], b[0], z), new P(b[1] + insetB, b[0], z)
            },
            new P(0, 0, side),
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}});
        P heel = new P(a[1], a[0], 8 + side * .05),
            innerHeel = new P(a[1] + insetA, a[0], z),
            innerTip = new P(b[1] + insetB, b[0], z),
            tip = new P(b[1], b[0], 8 + side * .05);
        mesh.quad(
            "edge",
            new P[] {heel, innerHeel, innerTip, innerTip},
            new P(-1, 0, side),
            new double[][] {{0, 1}, {1, 1}, {1, 0}, {1, 0}});
        mesh.quad(
            "edge",
            new P[] {heel, innerTip, tip, tip},
            new P(-1, 0, side),
            new double[][] {{0, 1}, {1, 0}, {0, 0}, {0, 0}});
      }
      mesh.quad(
          "blade",
          new P[] {
            new P(a[2], a[0], 7.68 - (flint ? .08 : 0)),
            new P(b[2], b[0], 7.68 - (flint ? .08 : 0)),
            new P(b[2], b[0], 8.32 + (flint ? .08 : 0)),
            new P(a[2], a[0], 8.32 + (flint ? .08 : 0))
          },
          new P(1, 0, 0),
          new double[][] {{0, 1}, {0, 0}, {1, 0}, {1, 1}});
      mesh.quad(
          "edge",
          new P[] {
            new P(a[1], a[0], 7.95), new P(b[1], b[0], 7.95),
            new P(b[1], b[0], 8.05), new P(a[1], a[0], 8.05)
          },
          new P(-1, 0, 0),
          new double[][] {{0, 1}, {0, 0}, {1, 0}, {1, 1}});
    }
    for (int index : new int[] {0, stations.length - 1}) {
      double[] end = stations[index];
      double inset = Math.min(.42, (end[2] - end[1]) * .4), half = flint ? .4 : .32;
      P outward = new P(0, index == 0 ? -1 : 1, 0);
      mesh.quad(
          "blade",
          new P[] {
            new P(end[1] + inset, end[0], 8 - half), new P(end[2], end[0], 8 - half),
            new P(end[2], end[0], 8 + half), new P(end[1] + inset, end[0], 8 + half)
          },
          outward,
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
      mesh.quad(
          "edge",
          new P[] {
            new P(end[1], end[0], 7.95), new P(end[1] + inset, end[0], 8 - half),
            new P(end[1] + inset, end[0], 8 + half), new P(end[1], end[0], 8.05)
          },
          outward,
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
    }
  }
}
