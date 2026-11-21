package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

final class HexStudyMaterials {
  private HexStudyMaterials() {}

  static JsonObject textures(
      String state, String source, Set<String> materials, Path provider, Map<Path, byte[]> output)
      throws IOException {
    boolean ancient = state.contains("ancient");
    var textures = new JsonObject();
    textures.addProperty("particle", source);
    for (String material : materials) {
      String field =
          (ancient && Set.of("paper", "inside", "paper_edge").contains(material) ? "ancient_" : "")
              + material;
      String relative = "item/equipment/hex/study/" + field;
      Path path = provider.resolve("textures/" + relative + ".png");
      if (!output.containsKey(path)) {
        var image = material.equals("coating") ? coating(source) : field(material, ancient);
        output.put(path, EquipmentModels.png(image));
      }
      textures.addProperty(material, "hexcasting:" + relative);
    }
    return textures;
  }

  static JsonObject slate(String state, String source, Path provider, Map<Path, byte[]> output)
      throws IOException {
    String stone = "hexcasting:block/slate";
    var textures = new JsonObject();
    textures.addProperty("particle", source);
    textures.addProperty("cover", stone);
    if (!state.equals("slate_written")) {
      textures.addProperty("pigment", stone);
      return textures;
    }
    var face = sprite(stone);
    var blank = sprite("hexcasting:item/slate_blank");
    var written = sprite(source);
    for (int y = 0; y < written.getHeight(); y++)
      for (int x = 0; x < written.getWidth(); x++) {
        int mark = written.getRGB(x, y);
        if (mark != blank.getRGB(x, y)) face.setRGB(x, y + 2, mark);
      }
    String field = "item/equipment/hex/study/slate_written";
    output.put(provider.resolve("textures/" + field + ".png"), EquipmentModels.png(face));
    textures.addProperty("pigment", "hexcasting:" + field);
    return textures;
  }

  private static BufferedImage field(String material, boolean ancient) {
    int rgb =
        switch (material) {
          case "paper" -> ancient ? 0xf5d78e : 0xfcfcf2;
          case "inside" -> ancient ? 0xe1b364 : 0xd6d6d6;
          case "paper_edge" -> ancient ? 0xa86120 : 0xaeaeae;
          case "ribbon" -> 0xb38ef3;
          case "knot" -> 0x6f4fab;
          case "lens_edge" -> 0x7954b4;
          default -> throw new IllegalArgumentException("Unknown Hex study material " + material);
        };
    int alpha = material.equals("lens_edge") ? 205 : 255;
    var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 64; y++)
      for (int x = 0; x < 64; x++) {
        int tone = (x / 2 + 2 * (y / 3) + x * y) % 13;
        int shade = tone == 3 ? -5 : tone == 9 ? 3 : 0;
        image.setRGB(
            x, y, alpha << 24 | EquipmentModels.shaded(0xff000000 | rgb, shade) & 0xffffff);
      }
    return image;
  }

  private static BufferedImage sprite(String source) throws IOException {
    String file = "/assets/" + source.replace(":", "/textures/") + ".png";
    try (var stream = HexStudyMaterials.class.getResourceAsStream(file)) {
      if (stream == null) throw new IOException("Missing Hex study art " + source);
      return ImageIO.read(stream);
    }
  }

  // Rectify the angled native glass onto a face-on lens. The warm glints transmit less light.
  private static BufferedImage coating(String source) throws IOException {
    var sprite = sprite(source);
    var face = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 32; y++)
      for (int x = 0; x < 32; x++) {
        double u = (2 * x + 1) / 32.0 - 1;
        double v = (2 * y + 1) / 32.0 - 1;
        double sx = 7.8 + 6.55 * u - 1.8 * v, sy = 7.8 + 6.55 * v - 1.8 * u;
        double nearest = Double.POSITIVE_INFINITY;
        int color = 0;
        for (int py = 0; py < sprite.getHeight(); py++)
          for (int px = 0; px < sprite.getWidth(); px++) {
            int sample = sprite.getRGB(px, py);
            if ((sample >>> 24) == 0) continue;
            double distance = (sx - px) * (sx - px) + (sy - py) * (sy - py);
            if (distance < nearest) {
              nearest = distance;
              color = sample & 0xffffff;
            }
          }
        int alpha =
            switch (color) {
              case 0xb38ef3 -> 89;
              case 0xfecbe6 -> 168;
              case 0xfffdd5 -> 231;
              case 0xcfa0f3 -> 108;
              case 0x8d6acc -> 112;
              case 0x54398a -> 178;
              case 0x6f4fab -> 146;
              default -> throw new IOException("Unexpected glass color in " + source);
            };
        face.setRGB(x, y, alpha << 24 | color);
      }
    return face;
  }
}
