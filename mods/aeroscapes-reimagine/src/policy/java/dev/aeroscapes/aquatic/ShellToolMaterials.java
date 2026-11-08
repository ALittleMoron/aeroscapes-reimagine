package dev.aeroscapes.aquatic;

import java.awt.image.BufferedImage;
import java.io.IOException;

/** Broad shell growth and nacre fields sampled from the pinned native shell palette. */
final class ShellToolMaterials {
  private ShellToolMaterials() {}

  static BufferedImage surfaceTexture() throws IOException {
    BufferedImage nativeSprite = NativeEquipmentModels.image("hybrid_aquatic:item/seashell_shovel");
    int[] palette = {
      nativeSprite.getRGB(14, 4), nativeSprite.getRGB(9, 1),
      nativeSprite.getRGB(10, 2), nativeSprite.getRGB(8, 3),
      nativeSprite.getRGB(9, 2), nativeSprite.getRGB(10, 4)
    };
    BufferedImage texture = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) texture.setRGB(x, y, palette[2]);
    field(
        texture,
        0,
        0,
        palette,
        "22223333",
        "22233333",
        "22333344",
        "23333444",
        "23334444",
        "23333444",
        "22333344",
        "22233333");
    field(texture, 8, 0, palette, "3333", "2222", "2232", "2222");
    field(texture, 12, 0, palette, "2222", "2222", "2232", "2222");
    field(texture, 8, 4, palette, "0011", "0111", "1111", "1121");
    field(
        texture,
        0,
        8,
        palette,
        "22333322",
        "23344332",
        "34444443",
        "34455443",
        "34444443",
        "33444433",
        "23344332",
        "22233222");
    field(texture, 8, 8, palette, "1222", "1222", "1232", "1122");
    return texture;
  }

  private static void field(
      BufferedImage texture, int left, int top, int[] palette, String... rows) {
    for (int y = 0; y < rows.length; y++)
      for (int x = 0; x < rows[y].length(); x++)
        texture.setRGB(left + x, top + y, palette[rows[y].charAt(x) - '0']);
  }
}
