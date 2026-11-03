package dev.aeroscapes.aquatic;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Original rod meshes follow the materials and identifying motifs of the pinned inventory icons.
 */
final class RodModels {
  private static final String[] MATERIALS = {
    "shaft", "grip", "reel", "trim", "dark", "line", "bobber", "wood", "highlight", "leaf"
  };

  private RodModels() {}

  static void addResources(Path repository, Map<Path, byte[]> output) throws IOException {
    Path assets =
        repository.resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets/tide");
    for (var style : palettes().entrySet()) {
      String name = style.getKey() + "_fishing_rod";
      EquipmentModels.text(
          output,
          assets.resolve("modefite_items_override/" + name + ".json"),
          EquipmentModels.rodDefinition(name));
      for (boolean cast : new boolean[] {false, true}) {
        for (String suffix : List.of("", "_left", "_fp", "_fp_left")) {
          String child = name + (cast ? "_cast_held" : "_held") + suffix;
          EquipmentModels.text(
              output,
              assets.resolve("models/item/equipment/" + child + ".json"),
              model(style.getKey(), cast, suffix));
        }
      }
      output.put(
          assets.resolve("textures/item/equipment/" + name + ".png"), material(style.getValue()));
    }
  }

  static Map<String, int[]> palettes() {
    Map<String, int[]> styles = new LinkedHashMap<>();
    // Shaft, grip, reel, trim, dark, line, bobber, wood, highlight, foliage.
    styles.put(
        "stone",
        new int[] {
          0x493615, 0x646464, 0xB5B5B5, 0x8D8D8D, 0x281E0B, 0x646464, 0xEDEDE5, 0x493615, 0xB5B5B5,
          0x493615
        });
    styles.put(
        "iron",
        new int[] {
          0xD8D8D8, 0x493615, 0xD8D8D8, 0xFFFFFF, 0x181818, 0x646464, 0xFFFFFF, 0x493615, 0xFFFFFF,
          0x493615
        });
    styles.put(
        "golden",
        new int[] {
          0xDC9613, 0xB26411, 0xDC9613, 0xFDF55F, 0x752802, 0x646464, 0xFDF55F, 0xB26411, 0xFDF55F,
          0xB26411
        });
    styles.put(
        "crystal",
        new int[] {
          0x6F4FAB, 0x42286A, 0xCFA0F3, 0xFECBE6, 0x2A163F, 0x646464, 0xFECBE6, 0x493615, 0xCFA0F3,
          0x6F4FAB
        });
    styles.put(
        "diamond",
        new int[] {
          0x1AAAA7, 0x082520, 0x493615, 0x33EBCB, 0x082520, 0x646464, 0x8FFADF, 0x493615, 0x8FFADF,
          0x1AAAA7
        });
    styles.put(
        "netherite",
        new int[] {
          0x4F3C3E, 0x231012, 0x4A2940, 0x6C414F, 0x231012, 0x646464, 0x4A2940, 0x231012, 0x7B5662,
          0x231012
        });
    styles.put(
        "echo",
        new int[] {
          0x052A32, 0x052A32, 0x0A5060, 0x009295, 0x021C23, 0x646464, 0x29DFEB, 0x052A32, 0x29DFEB,
          0x009295
        });
    styles.put(
        "prismarine",
        new int[] {
          0x79B4A6, 0x082054, 0x21453B, 0x37D3E1, 0x21453B, 0x646464, 0x37D3E1, 0x21453B, 0xB0DBD0,
          0x79B4A6
        });
    styles.put(
        "sunflower",
        new int[] {
          0x493615, 0x281E0B, 0xFFEC4F, 0xE5A52A, 0x281E0B, 0x177C04, 0xE5A52A, 0x493615, 0xFFEC4F,
          0x55AB2D
        });
    styles.put(
        "village",
        new int[] {
          0x493615, 0x005300, 0xE31700, 0x17DD62, 0x281E0B, 0x646464, 0xFFFFFF, 0x493615, 0x81FF99,
          0x005300
        });
    styles.put(
        "blazing",
        new int[] {
          0x000001, 0x2B0178, 0x8308E4, 0xFFC100, 0x000001, 0xFFC100, 0xFFC100, 0x2B0178, 0xFFF32D,
          0x8308E4
        });
    styles.put(
        "honeycomb",
        new int[] {
          0xEA8E16, 0xD56B2B, 0xE57B24, 0xFABF29, 0xB65317, 0xD56B2B, 0xFADE29, 0xD56B2B, 0xFADE29,
          0xE57B24
        });
    styles.put(
        "midas",
        new int[] {
          0xDC9613, 0x752802, 0xDC9613, 0xFAD64A, 0x752802, 0x646464, 0xFDF55F, 0xB26411, 0xFDF55F,
          0xDC9613
        });
    return styles;
  }

  static String model(String style, boolean cast, String suffix) {
    if (!palettes().containsKey(style)) throw new IllegalArgumentException("Unknown rod: " + style);
    boolean left = suffix.endsWith("_left");
    List<String> parts = new ArrayList<>();
    addShaft(parts, style, left);
    addReel(parts, style, left);
    addMotif(parts, style, left);
    if (!cast) addHangingTackle(parts, style, left);
    return "{\n  \"parent\": \"minecraft:item/rods/held_fishing_"
        + (cast ? "cast" : "rod")
        + suffix
        + "\",\n  \"ambientocclusion\": false,\n  \"textures\": {\"particle\": \"tide:item/equipment/"
        + style
        + "_fishing_rod\"},\n  \"elements\": [\n    "
        + String.join(",\n    ", parts)
        + "\n  ]\n}\n";
  }

  private static void addShaft(List<String> parts, String style, boolean left) {
    String collar = style.equals("sunflower") ? "leaf" : "trim";
    double width = style.equals("honeycomb") ? 2.4 : style.equals("netherite") ? 2.2 : 1.8;
    part(
        parts,
        "butt",
        8 - width / 2,
        -1.6,
        13 - width / 2,
        8 + width / 2,
        0,
        13 + width / 2,
        "trim",
        left);
    part(parts, "grip", 7, 0, 12, 9, 6.8, 14, "grip", left);
    for (int index = 0; index < 4; index++) {
      double low = 6.8 + index * 5.7;
      double half = width / 2 - index * 0.12;
      String material =
          index % 2 == 1 && (style.equals("iron") || style.equals("diamond")) ? "dark" : "shaft";
      part(
          parts,
          "shaft_" + index,
          8 - half,
          low,
          13 - half,
          8 + half,
          low + (index == 3 ? 5.1 : 5.7),
          13 + half,
          material,
          left);
      part(parts, "joint_" + index, 7.25, low, 12.25, 8.75, low + 0.5, 13.75, collar, left);
    }
    part(parts, "tip", 7.55, 29, 12.55, 8.45, 29.3, 13.45, collar, left);
    part(parts, "tip_eye_mount", 7.7, 29.1, 11.55, 8.3, 29.4, 13.1, collar, left);
    // Guides sit on the front of the shaft; the last eye remains the highest point.
    for (int index = 0; index < 4; index++) {
      double high = index == 3 ? 29.8 : 11.2 + index * 6;
      eye(parts, "guide_" + index, 8, high, 11.55, 1.4, collar, left);
    }
    part(parts, "shaft_line_low", 7.91, 7.2, 11.46, 8.09, 14.8, 11.64, "line", left);
    part(parts, "shaft_line_mid", 7.91, 14.8, 11.46, 8.09, 22.4, 11.64, "line", left);
    part(parts, "shaft_line_high", 7.91, 22.4, 11.46, 8.09, 29.8, 11.64, "line", left);
  }

  private static void addReel(List<String> parts, String style, boolean left) {
    part(parts, "reel_seat", 7.45, 3.5, 10.3, 8.55, 7.4, 12.2, "wood", left);
    drum(parts, "spool", 5.7, 10.3, 6, 9.5, 1.35, "line", left);
    drum(parts, "flange_inner", 5.15, 5.7, 6, 9.5, 1.85, "reel", left);
    drum(parts, "flange_outer", 10.3, 10.85, 6, 9.5, 1.85, "reel", left);
    part(parts, "axle", 5, 5.65, 9.15, 11.6, 6.35, 9.85, "trim", left);
    part(parts, "crank", 11.25, 4.2, 9.18, 11.7, 6.25, 9.82, "reel", left);
    part(
        parts,
        "crank_knob",
        11.25,
        3.9,
        8.75,
        12.5,
        4.7,
        10.25,
        style.equals("village") ? "trim" : "grip",
        left);
    part(parts, "spool_pin", 4.65, 5.6, 9.1, 5.2, 6.4, 9.9, "trim", left);
  }

  private static void drum(
      List<String> parts,
      String name,
      double x1,
      double x2,
      double y,
      double z,
      double radius,
      String material,
      boolean left) {
    double narrow = radius * 0.5;
    part(
        parts,
        name + "_middle",
        x1,
        y - narrow,
        z - radius,
        x2,
        y + narrow,
        z + radius,
        material,
        left);
    part(
        parts,
        name + "_top",
        x1,
        y + narrow,
        z - narrow,
        x2,
        y + radius,
        z + narrow,
        material,
        left);
    part(
        parts,
        name + "_bottom",
        x1,
        y - radius,
        z - narrow,
        x2,
        y - narrow,
        z + narrow,
        material,
        left);
  }

  private static void addMotif(List<String> parts, String style, boolean left) {
    switch (style) {
      case "crystal" -> {
        for (int index = 0; index < 3; index++) {
          double x = 5.7 + index * 1.8;
          double high = index == 1 ? 29.2 : 27.6;
          part(
              parts,
              "crown_" + index,
              x,
              24.1,
              12.3,
              x + 1.3,
              high,
              13.7,
              index == 1 ? "highlight" : "reel",
              left);
          part(
              parts,
              "crystal_lug_" + index,
              x,
              0.5,
              12.2,
              x + 1.2,
              3 + index * 0.4,
              13.8,
              "trim",
              left);
        }
      }
      case "echo" -> {
        for (int index = 0; index < 3; index++) {
          double y = 11.4 + index * 6.5;
          part(parts, "sculk_cross_" + index, 6.1, y, 12.4, 9.9, y + 0.8, 13.6, "trim", left);
          part(
              parts,
              "sculk_knot_" + index,
              7.45,
              y - 0.6,
              11.9,
              8.55,
              y + 1.6,
              14.1,
              "highlight",
              left);
        }
      }
      case "prismarine" -> {
        part(parts, "blue_collar", 6.8, 3.2, 11.8, 9.2, 4.7, 14.2, "trim", left);
        part(parts, "blue_tip", 7, 27.3, 12, 9, 28.7, 14, "grip", left);
        part(parts, "sea_glass_tip", 7.25, 28.7, 12.25, 8.75, 29.5, 13.75, "trim", left);
      }
      case "sunflower" -> {
        for (int index = 0; index < 5; index++) {
          double y = 4 + index * 5;
          double x = index % 2 == 0 ? 6.8 : 8.5;
          part(parts, "vine_" + index, x, y, 11.6, x + 0.7, y + 4.2, 12.4, "leaf", left);
          part(
              parts, "leaf_" + index, x - 0.6, y + 1.5, 11.5, x + 1.2, y + 2.3, 12.1, "leaf", left);
        }
        for (int index = 0; index < 4; index++) {
          double y = index < 2 ? 4 : 7.5;
          double z = index % 2 == 0 ? 7.6 : 10.3;
          part(parts, "petal_" + index, 4.7, y, z, 5.2, y + 1.4, z + 1.1, "highlight", left);
        }
        part(parts, "flower_center", 4.3, 5.3, 8.5, 4.8, 7.1, 10.5, "trim", left);
      }
      case "village" -> {
        part(parts, "emerald_butt", 6.7, -1.3, 11.7, 9.3, 0.7, 14.3, "trim", left);
        part(parts, "emerald_tip", 6.8, 26.7, 11.8, 9.2, 29.2, 14.2, "trim", left);
        part(parts, "emerald_facet", 7.2, 27.2, 11.5, 8.8, 28.7, 11.9, "highlight", left);
      }
      case "blazing" -> {
        for (int index = 0; index < 3; index++) {
          double y = 0.6 + index * 2;
          part(parts, "purple_band_" + index, 6.8, y, 11.8, 9.2, y + 0.8, 14.2, "reel", left);
        }
        part(parts, "flame_left", 5.7, 23, 12.3, 6.8, 27.5, 13.7, "trim", left);
        part(parts, "flame_left_tip", 6.3, 27.5, 12.4, 7.1, 28.8, 13.6, "highlight", left);
        part(parts, "flame_right", 9.2, 23, 12.3, 10.3, 26.8, 13.7, "trim", left);
        part(parts, "flame_bridge", 6.8, 22.6, 12.3, 9.2, 23.6, 13.7, "trim", left);
        part(parts, "flame_core", 7.6, 25.6, 11.9, 8.4, 28.6, 12.6, "highlight", left);
      }
      case "honeycomb" -> {
        for (int index = 0; index < 8; index++) {
          double y = 0.2 + index * 3.4;
          double shift = index % 2 == 0 ? -0.55 : 0.55;
          part(parts, "comb_rim_" + index, 6 + shift, y, 11, 10 + shift, y + 2.9, 15, "trim", left);
          part(
              parts,
              "comb_cell_front_" + index,
              6.8 + shift,
              y + 0.6,
              10.8,
              9.2 + shift,
              y + 2.3,
              11.15,
              "shaft",
              left);
          part(
              parts,
              "comb_cell_back_" + index,
              6.8 + shift,
              y + 0.6,
              14.85,
              9.2 + shift,
              y + 2.3,
              15.2,
              "reel",
              left);
        }
      }
      case "midas" -> {
        for (int index = 0; index < 4; index++) {
          double y = 5 + index * 6;
          double x = index % 2 == 0 ? 5.5 : 8.5;
          part(parts, "gold_branch_" + index, x, y, 12.2, x + 2.3, y + 1.2, 13.8, "trim", left);
          part(
              parts,
              "gold_nugget_" + index,
              x,
              y + 0.7,
              12,
              x + 1.4,
              y + 2.4,
              14,
              "highlight",
              left);
        }
      }
      default -> {}
    }
  }

  private static void eye(
      List<String> parts,
      String name,
      double x,
      double y,
      double z,
      double width,
      String material,
      boolean left) {
    part(
        parts,
        name + "_left",
        x - width / 2,
        y - 0.7,
        z - 0.35,
        x - width / 2 + 0.3,
        y + 0.3,
        z + 0.35,
        material,
        left);
    part(
        parts,
        name + "_right",
        x + width / 2 - 0.3,
        y - 0.7,
        z - 0.35,
        x + width / 2,
        y + 0.3,
        z + 0.35,
        material,
        left);
    part(
        parts,
        name + "_top",
        x - width / 2,
        y + 0.3,
        z - 0.35,
        x + width / 2,
        y + 0.6,
        z + 0.35,
        material,
        left);
  }

  private static void addHangingTackle(List<String> parts, String style, boolean left) {
    part(parts, "tip_return", 7.91, 29.65, 10.35, 8.09, 29.83, 11.64, "line", left);
    part(parts, "hanging_line_top", 7.91, 22, 10.35, 8.09, 29.8, 10.53, "line", left);
    part(parts, "hanging_line_bottom", 7.91, 14.2, 10.35, 8.09, 22, 10.53, "line", left);
    part(parts, "bobber_top", 7.2, 13.5, 9.65, 8.8, 14.3, 11.25, "trim", left);
    part(parts, "bobber_bottom", 7.2, 12.7, 9.65, 8.8, 13.5, 11.25, "bobber", left);
    part(
        parts,
        "hook_stem",
        7.8,
        11.6,
        10.15,
        8.2,
        12.7,
        10.65,
        style.equals("blazing") ? "dark" : "reel",
        left);
    part(parts, "hook_point", 7.8, 11.4, 9.6, 8.2, 11.9, 10.65, "reel", left);
  }

  private static void part(
      List<String> parts,
      String name,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      String material,
      boolean left) {
    if (left) {
      double mirroredLow = 16 - x2;
      x2 = 16 - x1;
      x1 = mirroredLow;
    }
    int slot = List.of(MATERIALS).indexOf(material);
    if (slot < 0) throw new IllegalArgumentException("Unknown rod material: " + material);
    double u = slot % 4 * 4;
    double v = slot / 4 * 4;
    List<String> faces = new ArrayList<>();
    for (String side : List.of("north", "east", "south", "west", "up", "down")) {
      double width =
          switch (side) {
            case "east", "west" -> z2 - z1;
            default -> x2 - x1;
          };
      double height =
          switch (side) {
            case "up", "down" -> z2 - z1;
            default -> y2 - y1;
          };
      if (width > 8 || height > 8)
        throw new IllegalArgumentException("Split long rod part: " + name);
      faces.add(
          String.format(
              Locale.ROOT,
              "\"%s\": {\"uv\": [%.4f, %.4f, %.4f, %.4f], \"texture\": \"#particle\"}",
              side,
              u,
              v,
              u + width / 2,
              v + height / 2));
    }
    parts.add(
        String.format(
            Locale.ROOT,
            "{\"name\": \"%s\", \"from\": [%.4f, %.4f, %.4f], \"to\": [%.4f, %.4f, %.4f], \"faces\": {%s}}",
            name,
            x1,
            y1,
            z1,
            x2,
            y2,
            z2,
            String.join(", ", faces)));
  }

  private static byte[] material(int[] palette) throws IOException {
    if (palette.length != MATERIALS.length) throw new IllegalArgumentException("Rod palette size");
    BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    for (int slot = 0; slot < palette.length; slot++) {
      for (int y = 0; y < 8; y++) {
        for (int x = 0; x < 8; x++) {
          int shade = (7 - x - y) / 2 + Math.floorMod(x * 3 + y * 5, 3) - 1;
          image.setRGB(
              slot % 4 * 8 + x, slot / 4 * 8 + y, EquipmentModels.shaded(palette[slot], shade));
        }
      }
    }
    return EquipmentModels.png(image);
  }
}
