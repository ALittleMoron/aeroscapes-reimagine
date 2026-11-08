package dev.aeroscapes.aquatic;

import java.awt.image.BufferedImage;
import java.io.IOException;

/** Coherent native coral/gold fields at the reference tools' coarse texel scale. */
final class CoralToolMaterials {
  private CoralToolMaterials() {}

  static BufferedImage surfaceTexture(String item) throws IOException {
    BufferedImage sprite = NativeEquipmentModels.image("hybrid_aquatic:item/coral_blade");
    int[] coral = {
      sprite.getRGB(11, 9), sprite.getRGB(8, 0), sprite.getRGB(7, 3),
      sprite.getRGB(8, 1), sprite.getRGB(7, 2), sprite.getRGB(13, 2)
    };
    int[] gold = {
      sprite.getRGB(9, 8), sprite.getRGB(6, 7), sprite.getRGB(7, 7), sprite.getRGB(8, 7)
    };
    String[] socket = {"1223", "1233", "1222", "0112"};
    BufferedImage surface = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 32; y++)
      for (int x = 0; x < 32; x++) {
        int color = coral[2];
        if (x >= 16 && y < 8) color = coral[x < 24 ? 3 : 4];
        else if (x >= 16 && y < 16) color = coral[1];
        else if (x >= 16 && x < 24 && y < 24)
          color = gold[socket[(y - 16) / 2].charAt((x - 16) / 2) - '0'];
        surface.setRGB(x, y, color);
      }
    String[] workingHead =
        switch (item) {
          case "coral_pickaxe" ->
              new String[] {
                "2222234422222222",
                "2222233322222222",
                "2222333333222222",
                "2333222222333222",
                "2211122221112222",
                "1111111111111111"
              };
          case "coral_axe" ->
              new String[] {
                "2222222223334322",
                "2222222233333322",
                "2222222333222332",
                "2222222222222332",
                "2222222222222332",
                "2222222211122332",
                "2222222111111222",
                "1111111111111111"
              };
          case "coral_shovel" ->
              new String[] {
                "2222223343222222",
                "2222233343222222",
                "2222333322222222",
                "2222332222222222",
                "2222322221222222",
                "2222222221222222",
                "2222222112222222",
                "2222211112222222"
              };
          case "coral_blade" ->
              new String[] {
                "2222223422222222",
                "2222223322222222",
                "2234223322342222",
                "2234223322342222",
                "2222223322222222",
                "2222223322222222",
                "2223332233322222",
                "2222222222222222",
                "2211122221112222",
                "1111111111111111"
              };
          default -> throw new IllegalArgumentException(item);
        };
    for (int y = 0; y < 16; y++)
      for (int x = 0; x < 16; x++)
        surface.setRGB(
            x, y, coral[workingHead[Math.min(y, workingHead.length - 1)].charAt(x) - '0']);
    return surface;
  }
}
