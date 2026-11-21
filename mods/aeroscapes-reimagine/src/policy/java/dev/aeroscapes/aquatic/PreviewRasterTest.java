package dev.aeroscapes.aquatic;

import dev.aeroscapes.aquatic.PreviewRaster.Quad;
import dev.aeroscapes.aquatic.PreviewRaster.Vertex;
import java.awt.image.BufferedImage;
import java.util.List;
import org.joml.Vector3f;

/** Screen-space checks for occlusion, near clipping and transparent glass at a shared edge. */
public final class PreviewRasterTest {
  private PreviewRasterTest() {}

  public static void main(String[] arguments) {
    Quad glass = surface(.6f, -1, -1, 0x80ff0000);
    Quad backdrop = surface(1, -1.3f, -1.3f, 0xff0000ff);
    BufferedImage behind = PreviewRaster.render(List.of(glass, backdrop), 64, 64, 90);
    int middle = behind.getRGB(32, 31);
    if (middle != behind.getRGB(33, 31) || (middle >> 16 & 255) < 80 || (middle & 255) < 80)
      throw new AssertionError("Transparent quad must blend once across its diagonal");
    Quad opaque = surface(.3f, -.6f, -.6f, 0xff00ff00);
    for (var order : List.of(List.of(glass, opaque, backdrop), List.of(backdrop, opaque, glass))) {
      int color = PreviewRaster.render(order, 64, 64, 90).getRGB(32, 32);
      if ((color >> 8 & 255) < 180 || (color & 0xff00ff) != 0)
        throw new AssertionError("Occluded layers leaked through");
    }
    var crossing = surface(.02f, -.10f, -.03f, 0xffffffff);
    BufferedImage cut = PreviewRaster.render(List.of(crossing), 64, 64, 90);
    boolean visible = false;
    for (int y = 0; y < 64; y++)
      for (int x = 0; x < 64; x++) if (cut.getRGB(x, y) != cut.getRGB(0, 0)) visible = true;
    if (!visible)
      throw new AssertionError("A quad crossing the near plane must retain its visible portion");
  }

  private static Quad surface(float halfWidth, float bottomZ, float topZ, int rgba) {
    var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, rgba);
    return new Quad(
        List.of(
            vertex(-halfWidth, -halfWidth, bottomZ),
            vertex(halfWidth, -halfWidth, bottomZ),
            vertex(halfWidth, halfWidth, topZ),
            vertex(-halfWidth, halfWidth, topZ)),
        image,
        0xffffffff);
  }

  private static Vertex vertex(float x, float y, float z) {
    return new Vertex(new Vector3f(x, y, z), 0, 0);
  }
}
