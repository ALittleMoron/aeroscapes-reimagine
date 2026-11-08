package dev.aeroscapes.aquatic;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Material textures for the instrument cases, horns and rope. */
final class VanillaEquipmentModels {
  private VanillaEquipmentModels() {}

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    for (var entry :
        Map.of(
                "copper_horn",
                0xE87D55,
                "copper_band",
                0x66645F,
                "gold",
                0xC69431,
                "silver",
                0xA1ABB0,
                "recovery",
                0x4B6873,
                "horn",
                0x69685E,
                "leather",
                0x996239)
            .entrySet()) {
      BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < 32; y++)
        for (int x = 0; x < 32; x++) {
          int shade =
              (x == 2 || x == 3)
                  ? 3
                  : x == 29 ? -3 : Math.floorMod(x * 7 + y * 13, 37) == 0 ? 1 : 0;
          if (entry.getKey().equals("leather"))
            shade =
                Math.floorMod(x + y * 2, 11) < 2 ? -2 : Math.floorMod(x + y * 2, 11) == 3 ? 2 : 0;
          if (entry.getKey().equals("horn"))
            shade = Math.floorMod(x + y / 5, 9) == 0 ? -2 : x % 9 == 2 ? 1 : 0;
          image.setRGB(x, y, EquipmentModels.shaded(entry.getValue(), shade));
        }
      output.put(
          assets.resolve(
              "aeroscapes_reimagine/textures/item/vanilla_equipment/" + entry.getKey() + ".png"),
          EquipmentModels.png(image));
    }
  }
}
