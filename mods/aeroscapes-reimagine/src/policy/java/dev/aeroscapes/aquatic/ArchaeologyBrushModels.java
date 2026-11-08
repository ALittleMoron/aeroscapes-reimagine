package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Adapts the accepted Refined brush using vanilla atlas palettes and its existing hand poses. */
final class ArchaeologyBrushModels {
  private static final String NAMESPACE = "aeroscapes_reimagine:item/brush_palettes/";
  private static final List<String> TIERS = List.of("iron", "diamond", "netherite");

  private ArchaeologyBrushModels() {}

  static boolean supports(String id) {
    return TIERS.stream()
        .anyMatch(
            tier -> {
              String root = "betterarcheology:" + tier + "_brush";
              return id.equals(root) || id.startsWith(root + "_brushing_");
            });
  }

  static JsonObject model(String id) {
    String[] state = id.substring("betterarcheology:".length()).split("_brush(?:_brushing)?", 2);
    String suffix = state.length == 2 ? state[1] : "";
    String palette = "_aeroscapes_" + state[0];
    JsonObject textures = new JsonObject();
    textures.addProperty("1", "minecraft:item/held_brush" + palette);
    textures.addProperty("5", "minecraft:item/stick_held_" + palette);
    textures.addProperty("particle", "#1");
    JsonObject model = new JsonObject();
    model.addProperty("parent", "minecraft:item/equipment/held_brush" + suffix);
    model.add("textures", textures);
    return model;
  }

  static void addPalettes(Path assets, Map<Path, byte[]> output, JsonArray atlas)
      throws IOException {
    // Only these five provider colors occur on the ferrule. Unmapped bristle colors pass through.
    atlas.add(
        paletteSource(
            assets,
            output,
            "held_brush",
            "ferrule",
            new int[] {0x6E788C, 0xAD4E2B, 0xCC5D38, 0xDD6A4A, 0xE48771},
            new int[][] {
              {0x585858, 0x727272, 0xA8A8A8, 0xD8D8D8, 0xFFFFFF},
              {0x156F64, 0x20C5B5, 0x4AEDD9, 0xA1FBE8, 0xD5FFF6},
              {0x684E1E, 0xA67A15, 0xE9B115, 0xF5DB52, 0xFDFF76}
            }));
    atlas.add(
        paletteSource(
            assets,
            output,
            "stick_held_",
            "grip",
            new int[] {0x382B18, 0x4C3D26, 0x5F4A2B, 0x745A36, 0x917142, 0x987849},
            new int[][] {
              {0x1A0D00, 0x3B1E00, 0x472400, 0x542C00, 0x5D3000, 0x6B3700},
              {0x07080E, 0x141623, 0x1B1D2E, 0x242539, 0x2B2D43, 0x363852},
              {0x231012, 0x2F2122, 0x4A2940, 0x4F3C3E, 0x603432, 0x734543}
            }));
  }

  private static JsonObject paletteSource(
      Path assets, Map<Path, byte[]> output, String texture, String part, int[] key, int[][] colors)
      throws IOException {
    JsonObject source = new JsonObject();
    source.addProperty("type", "minecraft:paletted_permutations");
    JsonArray textures = new JsonArray();
    textures.add("minecraft:item/" + texture);
    source.add("textures", textures);
    source.addProperty("palette_key", writePalette(assets, output, part + "_key", key));
    JsonObject permutations = new JsonObject();
    for (int index = 0; index < TIERS.size(); index++) {
      String tier = TIERS.get(index);
      permutations.addProperty(
          "aeroscapes_" + tier, writePalette(assets, output, part + "_" + tier, colors[index]));
    }
    source.add("permutations", permutations);
    return source;
  }

  private static String writePalette(
      Path assets, Map<Path, byte[]> output, String name, int[] colors) throws IOException {
    BufferedImage image = new BufferedImage(colors.length, 1, BufferedImage.TYPE_INT_ARGB);
    for (int index = 0; index < colors.length; index++)
      image.setRGB(index, 0, 0xFF000000 | colors[index]);
    String id = NAMESPACE + name;
    output.put(assets.resolve(id.replace(":", "/textures/") + ".png"), EquipmentModels.png(image));
    return id;
  }
}
