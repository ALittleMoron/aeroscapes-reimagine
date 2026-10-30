package dev.aeroscapes.atmosphere;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Original pixel artwork composed deterministically, without external image inputs. */
public final class CelestialAtlas {
  private CelestialAtlas() {}

  public static void main(String[] arguments) throws IOException {
    BufferedImage image = new BufferedImage(128, 32, BufferedImage.TYPE_INT_ARGB);
    for (int sprite = 0; sprite < 4; sprite++) {
      for (int y = 0; y < 32; y++) {
        for (int x = 0; x < 32; x++) {
          image.setRGB(sprite * 32 + x, y, pixel(sprite, x - 15.5, y - 15.5));
        }
      }
    }
    ImageIO.write(image, "png", Path.of(arguments[0]).toFile());
  }

  private static int pixel(int sprite, double x, double y) {
    double distance = Math.hypot(x, y);
    if (sprite == 3) {
      double glow = Math.max(0, 1 - distance / 13);
      double core = Math.max(0, 1 - Math.max(Math.abs(x), Math.abs(y)) / 5);
      double rays = Math.abs(x) < 1 || Math.abs(y) < 1 ? glow * 0.35 : 0;
      int alpha = (int) (255 * Math.min(1, core + rays + glow * glow * 0.12));
      return color(alpha, 255, 250, 235);
    }
    double radius = sprite == 1 ? 10 : sprite == 0 ? 9 : 8;
    if (distance > radius) {
      int alpha = (int) (18 * Math.max(0, 1 - (distance - radius) / 4));
      return color(alpha, 255, 225, 180);
    }
    double shading = Math.min(1, 0.86 - x / radius * 0.12 - y / radius * 0.09);
    int red;
    int green;
    int blue;
    if (sprite == 0) {
      red = 255;
      green = 241;
      blue = 201;
    } else if (sprite == 1) {
      boolean band = Math.floorMod((int) Math.floor(y) + 10, 6) < 2;
      red = band ? 223 : 255;
      green = band ? 190 : 234;
      blue = band ? 151 : 205;
    } else {
      red = 255;
      green = 186;
      blue = 145;
    }
    return color(255, (int) (red * shading), (int) (green * shading), (int) (blue * shading));
  }

  private static int color(int alpha, int red, int green, int blue) {
    return alpha << 24 | red << 16 | green << 8 | blue;
  }
}
