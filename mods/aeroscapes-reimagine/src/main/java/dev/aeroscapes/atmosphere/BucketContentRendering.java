package dev.aeroscapes.atmosphere;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Maps the native contents' artwork onto a vessel surface, without sampling its metal rim. */
public final class BucketContentRendering {
  private static final ResourceLocation PROFILES =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "bucket_contents.json");
  private static final ResourceLocation HIGHLIGHT =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "item/bucket_highlight");
  private static final Set<String> KINDS =
      Set.of(
          "smooth",
          "molten",
          "viscous",
          "suspension",
          "chunks",
          "garnish",
          "mound",
          "solid",
          "steam",
          "iridescent",
          "gel");
  private static final float FLOOR = 1.8F / 16;
  private static final List<Point> OCTAGON = octagon();
  private static volatile Map<ResourceLocation, Profile> profiles;

  private BucketContentRendering() {}

  public record Rect(int left, int top, int right, int bottom) {
    public Rect {
      if (left < 0 || top < 0 || right <= left || bottom <= top)
        throw new IllegalArgumentException("Content rectangle must have positive pixel area");
    }

    boolean contains(int x, int y) {
      return x >= left && x < right && y >= top && y < bottom;
    }
  }

  public record Profile(
      ResourceLocation sprite,
      String kind,
      List<Rect> mask,
      List<Rect> steam,
      Rect rect,
      int tint,
      int light,
      List<List<Rect>> steamFrames,
      int frameTicks) {
    public Profile {
      if (sprite == null
          || rect == null
          || kind == null
          || !KINDS.contains(kind)
          || light < 0
          || light > 15
          || frameTicks < 1) throw new IllegalArgumentException("Invalid bucket content profile");
      mask = List.copyOf(mask);
      steam = List.copyOf(steam);
      steamFrames = steamFrames.stream().map(List::copyOf).toList();
    }

    public Profile(
        ResourceLocation sprite,
        String kind,
        List<Rect> mask,
        List<Rect> steam,
        Rect rect,
        int tint,
        int light) {
      this(sprite, kind, mask, steam, rect, tint, light, List.of(), 1);
    }
  }

  public static Profile profile(ResourceLocation bucket) {
    var loaded = profiles;
    if (loaded == null) {
      loaded = load();
      profiles = loaded;
    }
    return loaded.get(bucket);
  }

  public static void clear() {
    profiles = null;
  }

  private static Map<ResourceLocation, Profile> load() {
    var resource = Minecraft.getInstance().getResourceManager().getResource(PROFILES);
    if (resource.isEmpty()) return Map.of();
    try (var reader = resource.get().openAsReader()) {
      return parse(reader);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read bucket content profiles", exception);
    }
  }

  static Map<ResourceLocation, Profile> parse(Reader reader) {
    var document = object(JsonParser.parseReader(reader), "document");
    var entries = object(required(document, "profiles"), "profiles");
    Map<ResourceLocation, Profile> result = new HashMap<>();
    for (var entry : entries.entrySet()) {
      var value = object(entry.getValue(), entry.getKey());
      try {
        result.put(
            ResourceLocation.parse(entry.getKey()),
            new Profile(
                ResourceLocation.parse(string(required(value, "sprite"))),
                string(required(value, "kind")),
                rectangles(required(value, "mask")),
                rectangles(required(value, "steam")),
                rectangle(required(value, "rect")),
                color(required(value, "tint")),
                integer(required(value, "light")),
                value.has("steam_frames") ? frames(value.get("steam_frames")) : List.of(),
                value.has("frame_ticks") ? integer(value.get("frame_ticks")) : 1));
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException(
            "Invalid bucket contents for " + entry.getKey(), exception);
      }
    }
    return Map.copyOf(result);
  }

  private static JsonObject object(JsonElement value, String field) {
    if (!value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object");
    return value.getAsJsonObject();
  }

  private static JsonElement required(JsonObject value, String field) {
    var member = value.get(field);
    if (member == null || member.isJsonNull())
      throw new IllegalArgumentException("Missing bucket content field: " + field);
    return member;
  }

  private static String string(JsonElement value) {
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException("Expected a string");
    return value.getAsString();
  }

  private static int integer(JsonElement value) {
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException("Expected an integer");
    try {
      return value.getAsBigDecimal().intValueExact();
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Expected an exact 32-bit integer", exception);
    }
  }

  private static int color(JsonElement value) {
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException("Expected an ARGB integer");
    try {
      long number = value.getAsBigDecimal().longValueExact();
      if (number < Integer.MIN_VALUE || number > 0xFFFFFFFFL)
        throw new IllegalArgumentException("ARGB is outside 32 bits");
      return (int) number;
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Expected an exact ARGB integer", exception);
    }
  }

  private static Rect rectangle(JsonElement value) {
    if (!value.isJsonArray() || value.getAsJsonArray().size() != 4)
      throw new IllegalArgumentException("Expected four rectangle coordinates");
    var coordinates = value.getAsJsonArray();
    return new Rect(
        integer(coordinates.get(0)),
        integer(coordinates.get(1)),
        integer(coordinates.get(2)),
        integer(coordinates.get(3)));
  }

  private static List<Rect> rectangles(JsonElement value) {
    if (!value.isJsonArray()) throw new IllegalArgumentException("Expected a rectangle list");
    List<Rect> result = new ArrayList<>();
    for (var rectangle : value.getAsJsonArray()) result.add(rectangle(rectangle));
    return List.copyOf(result);
  }

  private static List<List<Rect>> frames(JsonElement value) {
    if (!value.isJsonArray()) throw new IllegalArgumentException("Expected a steam frame list");
    List<List<Rect>> result = new ArrayList<>();
    for (var frame : value.getAsJsonArray()) result.add(rectangles(frame));
    return List.copyOf(result);
  }

  public static BakedModel create(
      BakedModel template,
      TextureAtlasSprite sprite,
      Profile profile,
      float fraction,
      int tint,
      boolean emissive,
      double ticks) {
    var highlight =
        Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(HIGHLIGHT);
    if (highlight.contents().name().equals(MissingTextureAtlasSprite.getLocation()))
      highlight = null;
    return create(template, sprite, profile, fraction, tint, emissive, ticks, highlight);
  }

  static BakedModel create(
      BakedModel template,
      TextureAtlasSprite sprite,
      Profile profile,
      float fraction,
      int tint,
      boolean emissive,
      double ticks,
      TextureAtlasSprite highlight) {
    var surface = new Surface(sprite, profile, Math.clamp(fraction, 0, 1), ticks);
    List<BakedQuad> quads = new ArrayList<>();
    if (fraction > 0) {
      surface.top(quads, tint, emissive);
      surface.sides(quads, tint, emissive);
      if (highlight != null) surface.steam(quads, highlight, emissive);
    }
    var immutable = List.copyOf(quads);
    return new BakedModelWrapper<>(template) {
      @Override
      public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return side == null ? immutable : List.of();
      }

      @Override
      public List<BakedQuad> getQuads(
          BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
        return getQuads(state, side, random);
      }

      @Override
      public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        return List.of(this);
      }

      @Override
      public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return List.of(
            emissive
                ? RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS)
                : RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
      }

      @Override
      public boolean usesBlockLight() {
        return false;
      }
    };
  }

  private record Point(float x, float z) {}

  private record Vertex(float x, float y, float z) {}

  private record Pixel(int x, int y, int color) {}

  private static final class Surface {
    private final TextureAtlasSprite sprite;
    private final Profile profile;
    private final String kind;
    private final double ticks;
    private final float radius;
    private final float height;
    private final int width;
    private final int depth;
    private final Rect bounds;
    private final List<Pixel> pixels;
    private final List<Integer> baseColors;

    private Surface(TextureAtlasSprite sprite, Profile profile, float fraction, double ticks) {
      this.sprite = sprite;
      this.profile = profile;
      this.kind = profile == null ? "smooth" : profile.kind();
      this.ticks = ticks;
      this.radius = (3.7F + 1.1F * fraction) / 16;
      this.height = (1.8F + 7.2F * fraction) / 16;
      this.width = sprite.contents().width();
      this.depth = sprite.contents().height();
      var full = new Rect(0, 0, width, depth);
      var mask = profile == null || profile.mask().isEmpty() ? List.of(full) : profile.mask();
      this.pixels = pixels(mask);
      this.bounds = bounds(mask);
      var base = profile == null ? full : profile.rect();
      var selected =
          pixels.stream()
              .filter(pixel -> base.contains(pixel.x(), pixel.y()))
              .map(Pixel::color)
              .toList();
      this.baseColors =
          baseCluster(selected.isEmpty() ? List.of(pixels.getFirst().color()) : selected);
    }

    private static List<Integer> baseCluster(List<Integer> colors) {
      int selected = colors.getFirst();
      int largest = 0;
      for (int candidate : colors) {
        int nearby = 0;
        for (int color : colors) if (colorDistance(candidate, color) <= 3600) nearby++;
        if (nearby > largest) {
          selected = candidate;
          largest = nearby;
        }
      }
      List<Integer> result = new ArrayList<>();
      for (int color : colors) if (colorDistance(selected, color) <= 3600) result.add(color);
      return result.stream().distinct().toList();
    }

    private List<Pixel> pixels(List<Rect> rectangles) {
      List<Pixel> result = new ArrayList<>();
      for (var rectangle : rectangles) {
        if (rectangle.right() > width || rectangle.bottom() > depth)
          throw new IllegalArgumentException(
              "Bucket content mask exceeds " + sprite.contents().name());
        for (int y = rectangle.top(); y < rectangle.bottom(); y++)
          for (int x = rectangle.left(); x < rectangle.right(); x++) {
            if (vaporPixel(x, y)) continue;
            int color = sprite.getPixelRGBA(0, x, y);
            if ((color >>> 24) != 0) result.add(new Pixel(x, y, color));
          }
      }
      if (result.isEmpty())
        throw new IllegalArgumentException("Bucket content mask has no visible pixels");
      return List.copyOf(result);
    }

    private boolean vaporPixel(int x, int y) {
      if (profile == null) return false;
      for (var frame : profile.steamFrames())
        for (var rectangle : frame) if (rectangle.contains(x, y)) return true;
      return false;
    }

    private boolean food() {
      return kind.equals("chunks")
          || kind.equals("garnish")
          || kind.equals("mound")
          || kind.equals("solid");
    }

    private Pixel sample(float x, float z) {
      double horizontal = (x + 1) / 2;
      double vertical = (z + 1) / 2;
      if (profile != null && !food()) {
        double phase = ticks * (kind.equals("viscous") ? .018 : .035);
        if (kind.equals("smooth") || kind.equals("molten")) {
          horizontal =
              .5 + .43 * Math.sin(x * 2.1 + z * 3.8 + phase + .6 * Math.sin(z * 4 - phase));
          vertical =
              .5 + .42 * Math.sin(z * 3.3 - x * 2.6 - phase * .7 + .6 * Math.cos(x * 4 + phase));
        } else {
          horizontal += .13 * Math.sin(z * 3.1 + phase) + .055 * Math.cos(x * 5.3 - phase * .7);
          vertical += .12 * Math.sin(x * 3.7 - phase * .8) + .045 * Math.cos(z * 4.2 + phase);
        }
      }
      return nearest(
          bounds.left() + horizontal * (bounds.right() - bounds.left()) - .5,
          bounds.top() + vertical * (bounds.bottom() - bounds.top()) - .5);
    }

    private Pixel nearest(double x, double y) {
      Pixel selected = pixels.getFirst();
      double distance = Double.POSITIVE_INFINITY;
      for (var pixel : pixels) {
        double next = (pixel.x() - x) * (pixel.x() - x) + (pixel.y() - y) * (pixel.y() - y);
        if (next < distance) {
          selected = pixel;
          distance = next;
        }
      }
      return selected;
    }

    private float elevation(Point point) {
      double squared = point.x() * point.x() + point.z() * point.z();
      double mound = Math.max(0, 1 - squared);
      double wave =
          Math.sin(point.x() * 4.3 + ticks * .09) * Math.cos(point.z() * 3.8 - ticks * .075);
      return height
          + switch (kind) {
            case "solid" -> (float) (.095 * mound);
            case "mound" -> (float) (.095 * mound + .004 * wave);
            case "chunks" -> (float) (.075 * ingredient(point) + .003 * wave);
            case "garnish" -> (float) (.06 * ingredient(point) + .003 * wave);
            case "gel" -> (float) (.035 * mound + .007 * wave);
            case "viscous" -> (float) (.0035 * wave);
            default -> (float) (.006 * wave);
          };
    }

    private double ingredient(Point point) {
      double total = 0;
      for (var offset :
          List.of(
              new Point(0, 0),
              new Point(.08F, 0),
              new Point(-.08F, 0),
              new Point(0, .08F),
              new Point(0, -.08F))) {
        int color = sample(point.x() + offset.x(), point.z() + offset.z()).color();
        double minimum = Double.POSITIVE_INFINITY;
        for (int base : baseColors) {
          minimum = Math.min(minimum, colorDistance(color, base));
        }
        total += Math.clamp((Math.sqrt(minimum) - 24) / 90, 0, 1);
      }
      return total / 5;
    }

    private Vertex vertex(Point point, float lift) {
      return new Vertex(
          .5F + radius * point.x(), elevation(point) + lift, .5F + radius * point.z());
    }

    private void top(List<BakedQuad> quads, int tint, boolean emissive) {
      int columns = Math.max(8, bounds.right() - bounds.left());
      int rows = Math.max(8, bounds.bottom() - bounds.top());
      for (int row = 0; row < rows; row++)
        for (int column = 0; column < columns; column++) {
          float left = -1 + 2F * column / columns;
          float right = -1 + 2F * (column + 1) / columns;
          float top = -1 + 2F * row / rows;
          float bottom = -1 + 2F * (row + 1) / rows;
          var polygon =
              clip(
                  List.of(
                      new Point(left, top),
                      new Point(left, bottom),
                      new Point(right, bottom),
                      new Point(right, top)));
          if (polygon.size() < 3) continue;
          var pixel = sample((left + right) / 2, (top + bottom) / 2);
          if (polygon.size() == 4)
            quads.add(
                quad(
                    sprite,
                    pixel,
                    vertex(polygon.get(0), 0),
                    vertex(polygon.get(1), 0),
                    vertex(polygon.get(2), 0),
                    vertex(polygon.get(3), 0),
                    tint,
                    emissive,
                    Direction.UP));
          else
            for (int index = 1; index < polygon.size() - 1; index++)
              quads.add(
                  quad(
                      sprite,
                      pixel,
                      vertex(polygon.getFirst(), 0),
                      vertex(polygon.get(index), 0),
                      vertex(polygon.get(index + 1), 0),
                      vertex(polygon.get(index + 1), 0),
                      tint,
                      emissive,
                      Direction.UP));
        }
    }

    private void sides(List<BakedQuad> quads, int tint, boolean emissive) {
      for (int index = 0; index < OCTAGON.size(); index++) {
        var first = OCTAGON.get(index);
        var second = OCTAGON.get((index + 1) % OCTAGON.size());
        var topFirst = vertex(first, 0);
        var topSecond = vertex(second, 0);
        var pixel =
            nearest(
                (bounds.left() + bounds.right() - 1) / 2.0,
                (bounds.top() + bounds.bottom() - 1) / 2.0);
        quads.add(
            quad(
                sprite,
                pixel,
                topFirst,
                new Vertex(.5F + first.x() * 3.7F / 16, FLOOR, .5F + first.z() * 3.7F / 16),
                new Vertex(.5F + second.x() * 3.7F / 16, FLOOR, .5F + second.z() * 3.7F / 16),
                topSecond,
                tint,
                emissive,
                Direction.getNearest(first.x() + second.x(), 0, first.z() + second.z())));
      }
    }

    private void steam(List<BakedQuad> quads, TextureAtlasSprite highlight, boolean emissive) {
      if (profile == null) return;
      var union =
          profile.steam().isEmpty()
              ? profile.steamFrames().stream().flatMap(List::stream).toList()
              : profile.steam();
      if (union.isEmpty()) return;
      var frame =
          profile.steamFrames().isEmpty()
              ? profile.steam()
              : profile
                  .steamFrames()
                  .get(
                      Math.floorMod(
                          (long) Math.floor(ticks / profile.frameTicks()),
                          profile.steamFrames().size()));
      if (frame.isEmpty()) return;
      List<Pixel> steamPixels = new ArrayList<>();
      for (var rectangle : frame) {
        if (rectangle.right() > width || rectangle.bottom() > depth)
          throw new IllegalArgumentException("Steam mask exceeds native sprite frame");
        for (int y = rectangle.top(); y < rectangle.bottom(); y++)
          for (int x = rectangle.left(); x < rectangle.right(); x++)
            steamPixels.add(new Pixel(x, y, -1));
      }
      var steamBounds = bounds(union);
      float columns = steamBounds.right() - steamBounds.left();
      float rows = steamBounds.bottom() - steamBounds.top();
      float pixelScale = .32F / Math.max(columns, rows);
      float base = Math.max(height + .08F, 10.28F / 16);
      for (int plume = 0; plume < 2; plume++) {
        double phase = ticks / 45 + plume * .5;
        float cycle = (float) (phase - Math.floor(phase));
        float rise = .16F * cycle;
        float drift = (float) Math.sin(ticks * .04 + plume * 2) * .025F;
        float center = .5F + (plume == 0 ? -.22F : .22F);
        int tint = (int) (255 * (.6 + .25 * Math.sin(cycle * Math.PI))) << 24 | 0xFFFFFF;
        for (int sheet = 0; sheet < 2; sheet++)
          for (var pixel : steamPixels) {
            float left = (pixel.x() - steamBounds.left() - columns / 2) * pixelScale + drift;
            float right = left + pixelScale;
            if (plume == 0) {
              float mirroredLeft = -right;
              right = -left;
              left = mirroredLeft;
            }
            float top = base + (steamBounds.bottom() - pixel.y()) * pixelScale + rise;
            float bottom = top - pixelScale;
            Vertex a = steamVertex(sheet, center, left, top);
            Vertex b = steamVertex(sheet, center, left, bottom);
            Vertex c = steamVertex(sheet, center, right, bottom);
            Vertex d = steamVertex(sheet, center, right, top);
            quads.add(
                quad(
                    highlight,
                    new Pixel(0, 0, -1),
                    a,
                    b,
                    c,
                    d,
                    tint,
                    emissive,
                    sheet == 0 ? Direction.SOUTH : Direction.WEST));
          }
      }
    }
  }

  private static Vertex steamVertex(int sheet, float center, float horizontal, float vertical) {
    return sheet == 0
        ? new Vertex(center + horizontal, vertical, .5F)
        : new Vertex(center, vertical, .5F + horizontal);
  }

  private static Point polar(float radius, double angle) {
    return new Point((float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius);
  }

  private static Rect bounds(List<Rect> rectangles) {
    int left = Integer.MAX_VALUE;
    int top = Integer.MAX_VALUE;
    int right = 0;
    int bottom = 0;
    for (var rectangle : rectangles) {
      left = Math.min(left, rectangle.left());
      top = Math.min(top, rectangle.top());
      right = Math.max(right, rectangle.right());
      bottom = Math.max(bottom, rectangle.bottom());
    }
    return new Rect(left, top, right, bottom);
  }

  private static List<Point> octagon() {
    List<Point> points = new ArrayList<>();
    for (int index = 0; index < 8; index++) points.add(polar(1, -index * Math.PI / 4));
    return List.copyOf(points);
  }

  private static List<Point> clip(List<Point> polygon) {
    for (int edge = 0; edge < OCTAGON.size(); edge++) {
      var first = OCTAGON.get(edge);
      var second = OCTAGON.get((edge + 1) % OCTAGON.size());
      List<Point> clipped = new ArrayList<>();
      for (int index = 0; index < polygon.size(); index++) {
        var previous = polygon.get((index + polygon.size() - 1) % polygon.size());
        var current = polygon.get(index);
        float before = cross(first, second, previous);
        float after = cross(first, second, current);
        if ((before <= 0) != (after <= 0)) {
          float fraction = before / (before - after);
          clipped.add(
              new Point(
                  previous.x() + fraction * (current.x() - previous.x()),
                  previous.z() + fraction * (current.z() - previous.z())));
        }
        if (after <= 0) clipped.add(current);
      }
      polygon = clipped;
      if (polygon.isEmpty()) break;
    }
    return polygon;
  }

  private static float cross(Point first, Point second, Point point) {
    return (second.x() - first.x()) * (point.z() - first.z())
        - (second.z() - first.z()) * (point.x() - first.x());
  }

  private static int colorDistance(int first, int second) {
    int red = (first & 255) - (second & 255);
    int green = (first >> 8 & 255) - (second >> 8 & 255);
    int blue = (first >> 16 & 255) - (second >> 16 & 255);
    return red * red + green * green + blue * blue;
  }

  private static BakedQuad quad(
      TextureAtlasSprite sprite,
      Pixel pixel,
      Vertex first,
      Vertex second,
      Vertex third,
      Vertex fourth,
      int tint,
      boolean emissive,
      Direction direction) {
    int[] vertices = new int[IQuadTransformer.STRIDE * 4];
    var points = List.of(first, second, third, fourth);
    float normalX =
        (second.y() - first.y()) * (third.z() - first.z())
            - (second.z() - first.z()) * (third.y() - first.y());
    float normalY =
        (second.z() - first.z()) * (third.x() - first.x())
            - (second.x() - first.x()) * (third.z() - first.z());
    float normalZ =
        (second.x() - first.x()) * (third.y() - first.y())
            - (second.y() - first.y()) * (third.x() - first.x());
    float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
    int normal =
        length == 0
            ? 0
            : (Math.round(normalX / length * 127) & 255)
                | (Math.round(normalY / length * 127) & 255) << 8
                | (Math.round(normalZ / length * 127) & 255) << 16;
    int packed = tint & 0xFF00FF00 | (tint >>> 16 & 255) | (tint & 255) << 16;
    for (int index = 0; index < 4; index++) {
      int start = index * IQuadTransformer.STRIDE;
      var point = points.get(index);
      vertices[start + IQuadTransformer.POSITION] = Float.floatToRawIntBits(point.x());
      vertices[start + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(point.y());
      vertices[start + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(point.z());
      vertices[start + IQuadTransformer.COLOR] = packed;
      vertices[start + IQuadTransformer.UV0] =
          Float.floatToRawIntBits(sprite.getU((pixel.x() + .5F) / sprite.contents().width()));
      vertices[start + IQuadTransformer.UV0 + 1] =
          Float.floatToRawIntBits(sprite.getV((pixel.y() + .5F) / sprite.contents().height()));
      vertices[start + IQuadTransformer.UV2] = emissive ? 0x00F000F0 : 0;
      vertices[start + IQuadTransformer.NORMAL] = normal;
    }
    return new BakedQuad(vertices, -1, direction, sprite, false, false);
  }
}
