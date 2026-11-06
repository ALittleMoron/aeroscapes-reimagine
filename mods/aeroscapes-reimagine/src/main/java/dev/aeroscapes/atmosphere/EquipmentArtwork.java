package dev.aeroscapes.atmosphere;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Composes material paint from installed icons; generated provider pixels are not shipped. */
public final class EquipmentArtwork {
  public static final int SIZE = 128;
  public static final int MATERIAL_COLUMNS = 2;
  public static final int PROFILE_GRID = 48;
  public static final double PROFILE_MIN = -4;
  public static final double PROFILE_MAX = 20;
  public static final List<String> MATERIALS =
      List.of(
          "body", "grip", "edge", "dark", "rope", "glass", "paper", "detail", "green", "red",
          "gold", "band");

  private EquipmentArtwork() {}

  public static BufferedImage paint(BufferedImage icon, EquipmentStyle profile) {
    if (icon.getWidth() > icon.getHeight())
      throw new IllegalArgumentException("Equipment icons require a square first frame");
    double[] bodyRegion =
        switch (profile) {
          case KNIFE, BRUSH, CORAL, SHELL, HAMMER, SPADE, SAW, CHISEL, WRENCH ->
              new double[] {7, 0, 15, 8};
          case WATERING_CAN -> new double[] {3, 6, 12, 13};
          case WHISTLE -> new double[] {2, 4, 9, 9};
          case SPYGLASS -> new double[] {4, 7, 10, 12};
          case CLAW -> new double[] {2, 2, 7, 7};
          case BELL -> new double[] {8, 1, 15, 8};
          case CAPTURE_NET -> new double[] {6, 1, 10, 5};
          case COIL -> new double[] {3, 5, 8, 13};
          case MULTIMETER -> new double[] {3, 4, 5, 12};
          case FISH_FINDER -> new double[] {3, 5, 5, 12};
          case DIAL, DEPTH_METER, MONSTROMETER -> new double[] {1, 5, 5, 12};
          case CLIMATE_GAUGE -> new double[] {6, 1, 9, 7};
          case ALTIMETER -> new double[] {4, 2, 6, 14};
          case LUNCHBOX -> new double[] {2, 6, 5, 14};
          case TERMINAL -> new double[] {2, 3, 4, 13};
          case FISH -> new double[] {4, 5, 9, 8};
          case BOOK -> new double[] {1, 5, 4, 13};
          case ABACUS -> new double[] {1, 1, 15, 3};
          case BOOMERANG -> new double[] {3, 3, 7, 5};
          case BOW -> new double[] {0, 9, 5, 16};
          case CASTING_NET -> new double[] {10, 3, 15, 10};
          default -> new double[] {2, 2, 14, 14};
        };
    int[] body = palette(icon, bodyRegion);
    if (profile == EquipmentStyle.CAPTURE_NET || profile == EquipmentStyle.BOW)
      body = colorfulPalette(icon, bodyRegion);
    int[] grip =
        palette(
            icon,
            profile == EquipmentStyle.SAW
                ? new double[] {8, 9, 15, 16}
                : profile == EquipmentStyle.BOOMERANG
                    ? new double[] {8, 3, 10, 5}
                    : profile == EquipmentStyle.ABACUS
                        ? new double[] {10, 5, 13, 6}
                        : new double[] {0, 8, 8, 16});
    if (profile == EquipmentStyle.LUNCHBOX) grip = body;
    if (profile == EquipmentStyle.BOW) grip = new int[] {0x4A321A, 0x6B4C24, 0x8D6A33};
    if (profile == EquipmentStyle.CAPTURE_NET) grip = new int[] {0x473A24, 0x655232, 0x8A724B};
    int[][] colors = {
      body,
      grip,
      {body[1], body[2], blend(body[2], 0xFFFFFF, .3)},
      profile == EquipmentStyle.CASTING_NET
          ? new int[] {0x050508, 0x111118, 0x171720}
          : new int[] {body[0], body[0], body[1]},
      {0x60452E, 0x947044, 0xC5A16D},
      {0x54898E, 0xA8D4D8, 0xE5F6F4},
      {0xBDB8A7, 0xE7E2D2, 0xFAF6E8},
      profile == EquipmentStyle.CASTING_NET
          ? palette(icon, new double[] {3, 3, 5, 5})
          : new int[] {0x4B5357, 0x8D9B9F, 0xD5DDDE},
      greenPalette(icon),
      profile == EquipmentStyle.CLAW
          ? palette(icon, new double[] {9, 6, 13, 10})
          : new int[] {0x712721, 0xB64A32, 0xE48351},
      {0x8D5C14, 0xDC9D2C, 0xFFE467},
      profile == EquipmentStyle.BRUSH || profile == EquipmentStyle.BELL
          ? bandPalette(icon, profile)
          : body
    };
    BufferedImage atlas = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    for (int tile = 0; tile < colors.length; tile++)
      for (int y = 0; y < 16; y++)
        for (int x = 0; x < 16; x++) {
          int shade;
          if ((profile == EquipmentStyle.SPADE || profile == EquipmentStyle.SAW)
              && (tile == 0 || tile == 2)) shade = x < 3 ? 0 : x > 12 ? 2 : 1;
          else if (tile == 4) shade = Math.floorMod(x + y * 2, 5) < 2 ? 0 : x % 3 == 0 ? 2 : 1;
          else if (tile == 1) shade = x % 4 == 0 ? 0 : (x + y / 4) % 5 == 0 ? 2 : 1;
          else if (profile == EquipmentStyle.CORAL && tile == 0)
            shade = (x * 7 + y * 11) % 13 < 2 ? 0 : (x + y) % 7 == 0 ? 2 : 1;
          else if (profile == EquipmentStyle.SHELL && tile == 0) shade = (x + y / 3) % 3;
          else if (profile == EquipmentStyle.PAPER && tile == 0)
            shade = Math.floorMod(x * 13 + y * 7, 5) == 0 ? 0 : (x + y) % 4 == 0 ? 2 : 1;
          else shade = x == 1 || x == 2 && y < 4 ? 2 : x == 7 || y == 7 ? 0 : 1;
          atlas.setRGB(
              tile % MATERIAL_COLUMNS * 16 + x,
              tile / MATERIAL_COLUMNS * 16 + y,
              0xFF000000 | colors[tile][shade]);
        }
    // Rectify only painted, naturally thin working surfaces; housings are modeled separately.
    for (int y = 0; y < 96; y++)
      for (int x = 0; x < 96; x++) {
        double cx = PROFILE_MIN + (x + .5) / 4, cy = PROFILE_MAX - (y + .5) / 4;
        double sx = 8 + (cx + cy - 16) / Math.sqrt(2), sy = 8 + (cx - cy) / Math.sqrt(2);
        atlas.setRGB(32 + x, y, nearestPaint(icon, sx, sy));
      }
    return atlas;
  }

  private static int[] bandPalette(BufferedImage icon, EquipmentStyle profile) {
    List<Integer> colors = new ArrayList<>();
    double scale = icon.getWidth() / 16.0;
    int left = profile == EquipmentStyle.BELL ? 3 : 4;
    int bottom = profile == EquipmentStyle.BELL ? 9 : 7;
    int right = profile == EquipmentStyle.BELL ? 7 : 9;
    for (int y = (int) (bottom * scale); y < (int) ((bottom + 4) * scale); y++)
      for (int x = (int) (left * scale); x < (int) (right * scale); x++) {
        int color = icon.getRGB(x, y);
        if (color >>> 24 > 128) colors.add(color & 0xFFFFFF);
      }
    int strongest = colors.stream().mapToInt(EquipmentArtwork::chroma).max().orElse(0);
    if (strongest > 100) colors.removeIf(color -> chroma(color) < strongest * .5);
    if (colors.isEmpty()) throw new IllegalArgumentException("Empty equipment band material");
    colors.sort(Comparator.comparingInt(EquipmentArtwork::light));
    return new int[] {
      colors.get(colors.size() / 6),
      colors.get(colors.size() / 2),
      colors.get(colors.size() * 5 / 6)
    };
  }

  private static int chroma(int color) {
    int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
    return Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
  }

  private static int[] colorfulPalette(BufferedImage icon, double[] region) {
    List<Integer> colors = regionColors(icon, region);
    int strongest = colors.stream().mapToInt(EquipmentArtwork::chroma).max().orElseThrow();
    colors.removeIf(color -> chroma(color) < strongest * .6);
    return shades(colors);
  }

  private static int[] greenPalette(BufferedImage icon) {
    List<Integer> colors = regionColors(icon, new double[] {0, 0, 16, 16});
    colors.removeIf(
        color -> {
          int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
          return green < 80 || green < red * 1.2 || green < blue * 1.1;
        });
    return colors.isEmpty() ? new int[] {0x294125, 0x50743C, 0x84A45D} : shades(colors);
  }

  private static int nearestPaint(BufferedImage icon, double u, double v) {
    double scale = icon.getWidth() / 16.0;
    int x = (int) Math.floor(u * scale), y = (int) Math.floor(v * scale);
    int size = icon.getWidth(), height = Math.min(size, icon.getHeight());
    if (x >= 0 && y >= 0 && x < size && y < height && (icon.getRGB(x, y) >>> 24) > 0)
      return icon.getRGB(x, y);
    double nearest = Double.POSITIVE_INFINITY;
    int color = 0;
    for (int py = 0; py < height; py++)
      for (int px = 0; px < size; px++) {
        int candidate = icon.getRGB(px, py);
        if (candidate >>> 24 == 0) continue;
        double distance = Math.pow(px + .5 - u * scale, 2) + Math.pow(py + .5 - v * scale, 2);
        if (distance < nearest) {
          nearest = distance;
          color = candidate;
        }
      }
    return color;
  }

  private static int blend(int first, int second, double weight) {
    int result = 0;
    for (int shift : new int[] {0, 8, 16})
      result |=
          (int)
                  Math.round(
                      ((first >> shift) & 255) * (1 - weight) + ((second >> shift) & 255) * weight)
              << shift;
    return result;
  }

  private static int[] palette(BufferedImage icon, double[] region) {
    return shades(regionColors(icon, region));
  }

  private static List<Integer> regionColors(BufferedImage icon, double[] region) {
    List<Integer> colors = new ArrayList<>();
    double scale = icon.getWidth() / 16.0;
    for (int y = (int) (region[1] * scale); y < (int) (region[3] * scale); y++)
      for (int x = (int) (region[0] * scale); x < (int) (region[2] * scale); x++) {
        int color = icon.getRGB(x, y);
        if (color >>> 24 > 128) colors.add(color & 0xFFFFFF);
      }
    if (colors.isEmpty()) throw new IllegalArgumentException("Empty painted material region");
    return colors;
  }

  private static int[] shades(List<Integer> colors) {
    colors.sort(Comparator.comparingInt(EquipmentArtwork::light));
    return new int[] {
      colors.get(colors.size() / 6),
      colors.get(colors.size() / 2),
      colors.get(colors.size() * 5 / 6)
    };
  }

  private static int light(int color) {
    return ((color >> 16 & 255) * 3 + (color >> 8 & 255) * 6 + (color & 255)) / 10;
  }
}
