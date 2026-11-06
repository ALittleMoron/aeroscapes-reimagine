package dev.aeroscapes.atmosphere;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.awt.image.BufferedImage;
import net.minecraft.util.FastColor;

/** Material regions, rectification and sprite recipes without a Minecraft client. */
public final class EquipmentArtworkTest {
  private EquipmentArtworkTest() {}

  public static void main(String[] arguments) {
    BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 16; y++)
      for (int x = 0; x < 16; x++)
        icon.setRGB(
            x, y, x < 8 ? (y < 8 ? 0xFFD44B3A : 0xFFD4C03A) : (y < 8 ? 0xFF315DC8 : 0xFF7831C8));
    BufferedImage painted = EquipmentArtwork.paint(icon, EquipmentStyle.KNIFE);
    require(
        painted.getWidth() == 128 && painted.getHeight() == 128,
        "The model atlas has its declared size");
    require(
        painted.getRGB(48, 48) == 0xFFD44B3A
            && painted.getRGB(112, 48) == 0xFF7831C8
            && painted.getRGB(80, 16) == 0xFF315DC8
            && painted.getRGB(80, 80) == 0xFFD4C03A,
        "Rectification preserves source colors and left/right orientation");
    int grip = painted.getRGB(24, 8), blade = painted.getRGB(8, 8);
    require((grip >> 16 & 255) > (grip & 255), "The grip keeps its own red material region");
    require(
        (blade & 255) > (blade >> 16 & 255),
        "The blade keeps its blue region rather than the grip color");
    int rgba = 0xFF123456;
    require(
        FastColor.ABGR32.fromArgb32(rgba) == 0xFF563412,
        "NativeImage receives ABGR, not BufferedImage's ARGB");
    require(
        FastColor.ABGR32.fromArgb32(FastColor.ABGR32.fromArgb32(rgba)) == rgba,
        "Input/output pixel conversion preserves every channel");
    var recipe =
        JsonParser.parseString(
            """
        {"sprite":"aeroscapes_reimagine:item/test","resource":"minecraft:item/compass_00","profile":"dial"}
        """);
    var source = EquipmentPaintSprites.CODEC.codec().parse(JsonOps.INSTANCE, recipe).getOrThrow();
    require(
        source.profile() == EquipmentStyle.DIAL
            && source.resource().getPath().equals("item/compass_00"),
        "Atlas recipes resolve their provider texture and validated physical style");
    recipe.getAsJsonObject().addProperty("profile", "unknown");
    require(
        EquipmentPaintSprites.CODEC.codec().parse(JsonOps.INSTANCE, recipe).error().isPresent(),
        "An invalid artwork recipe cannot silently select a different material layout");
    System.out.println(
        "Equipment paint: source regions, rectification, channel order and recipe codec passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
