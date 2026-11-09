package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.joml.Vector3f;

/** Physical surfaces sample fields of the pinned provider textures. */
final class IronHeldMesh {
  static final List<String> HANDS =
      List.of(
          "firstperson_righthand",
          "firstperson_lefthand",
          "thirdperson_righthand",
          "thirdperson_lefthand");
  final EquipmentObjMesh obj;
  final String texture;
  final int width;
  final int height;
  private final Map<String, Sprite> sprites = new LinkedHashMap<>();
  private final Map<String, Patch> patches = new LinkedHashMap<>();
  private final Map<String, Integer> glowLevels = new LinkedHashMap<>();
  private final Map<String, EquipmentObjMesh> layers = new LinkedHashMap<>();
  private final Map<Path, byte[]> fields = new LinkedHashMap<>();

  IronHeldMesh(String texture) throws IOException {
    this.texture = texture;
    var sprite = sprite(texture);
    width = sprite.width();
    height = sprite.height();
    obj = layer("minecraft:cutout");
  }

  EquipmentObjMesh layer(String renderType) {
    return layers.computeIfAbsent(
        renderType,
        ignored -> {
          var mesh = new EquipmentObjMesh();
          patches.forEach((material, patch) -> patch.map(mesh, material));
          return mesh;
        });
  }

  void patch(String material, int x0, int y0, int x1, int y1) throws IOException {
    patch(material, texture, x0, y0, x1, y1);
  }

  void patch(String material, String source, int x0, int y0, int x1, int y1) throws IOException {
    crop(material, source, x0, y0, x1, y1, 1, true);
  }

  void overlay(String material, String source, int x0, int y0, int x1, int y1) throws IOException {
    crop(material, source, x0, y0, x1, y1, 1, false);
  }

  void field(String material, int x0, int y0, int x1, int y1) throws IOException {
    field(material, texture, x0, y0, x1, y1);
  }

  void glow(String material, int level) {
    if (!patches.containsKey(material) || level < 1 || level > 15) {
      throw new IllegalArgumentException("Invalid equipment glow: " + material + " at " + level);
    }
    glowLevels.put(material, level);
  }

  void field(String material, String source, int x0, int y0, int x1, int y1) throws IOException {
    var sprite = sprite(source);
    validateCrop(material, source, sprite, x0, y0, x1, y1, true);
    String signature = source + "/" + material + "/" + x0 + "/" + y0 + "/" + x1 + "/" + y1;
    String path =
        "item/equipment/iron_fields/"
            + UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8));
    String resource = "irons_spellbooks:" + path;
    fields.put(
        Path.of("irons_spellbooks/textures/" + path + ".png"),
        EquipmentModels.png(paintField(sprite, x0, y0, x1, y1, signature.hashCode())));
    if (sprite.animation() != null) {
      var animation = sprite.animation().deepCopy();
      animation.addProperty("width", 64);
      animation.addProperty("height", 64);
      var metadata = new JsonObject();
      metadata.add("animation", animation);
      fields.put(
          Path.of("irons_spellbooks/textures/" + path + ".png.mcmeta"),
          (metadata + "\n").getBytes(StandardCharsets.UTF_8));
    }
    register(material, new Patch(resource, 0, 0, 1, 1, 1, true));
  }

  private static BufferedImage paintField(Sprite source, int x0, int y0, int x1, int y1, int seed) {
    var image = source.image();
    int across = image.getWidth() / source.width(), down = image.getHeight() / source.height();
    var field = new BufferedImage(64, 64 * across * down, BufferedImage.TYPE_INT_ARGB);
    var random = new SplittableRandom(seed);
    int[] cells = random.ints(64 * 64).toArray();
    int count = (x1 - x0) * (y1 - y0);
    for (int frame = 0; frame < across * down; frame++) {
      int[] palette =
          image.getRGB(
              x0 + frame % across * source.width(),
              y0 + frame / across * source.height(),
              x1 - x0,
              y1 - y0,
              null,
              0,
              x1 - x0);
      int red = 0, green = 0, blue = 0;
      for (int color : palette) {
        red += color >>> 16 & 255;
        green += color >>> 8 & 255;
        blue += color & 255;
      }
      for (int y = 0; y < 64; y++) {
        for (int x = 0; x < 64; x++) {
          int group = cells[(y / 3) * 64 + x / 3];
          int detail = cells[y * 64 + x];
          int color = palette[Math.floorMod(group, count)];
          int tone = Math.floorMod(group >>> 12, 15) - 7 + Math.floorMod(detail, 5) - 2;
          int rgb =
              0xff000000
                  | ((2 * (color >>> 16 & 255) + 3 * red / count) / 5) << 16
                  | ((2 * (color >>> 8 & 255) + 3 * green / count) / 5) << 8
                  | ((2 * (color & 255) + 3 * blue / count) / 5);
          field.setRGB(x, frame * 64 + y, EquipmentModels.shaded(rgb, tone));
        }
      }
    }
    return field;
  }

  void glass(String material, int x0, int y0, int x1, int y1, double alpha) throws IOException {
    crop(material, texture, x0, y0, x1, y1, alpha, true);
  }

  void dial(String material, double x0, double y0, double x1, double y1) {
    register(
        material, new Patch("#dial", x0 / width, y0 / height, x1 / width, y1 / height, 1, false));
  }

  private void crop(
      String material, String source, int x0, int y0, int x1, int y1, double alpha, boolean opaque)
      throws IOException {
    var sprite = sprite(source);
    validateCrop(material, source, sprite, x0, y0, x1, y1, opaque);
    register(
        material,
        new Patch(
            source,
            (x0 + .04) / sprite.width(),
            (y0 + .04) / sprite.height(),
            (x1 - .04) / sprite.width(),
            (y1 - .04) / sprite.height(),
            alpha,
            false));
  }

  private static void validateCrop(
      String material, String source, Sprite sprite, int x0, int y0, int x1, int y1, boolean opaque)
      throws IOException {
    if (x0 < 0 || y0 < 0 || x0 >= x1 || y0 >= y1 || x1 > sprite.width() || y1 > sprite.height())
      throw new IOException("UV crop outside " + source + ": " + material);
    if (opaque) {
      for (int top = 0; top < sprite.image().getHeight(); top += sprite.height())
        for (int left = 0; left < sprite.image().getWidth(); left += sprite.width())
          for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
              if (sprite.image().getRGB(left + x, top + y) >>> 24 != 255)
                throw new IOException(
                    "Transparent material "
                        + material
                        + " in "
                        + source
                        + " at "
                        + x
                        + ","
                        + y
                        + " frame "
                        + top / sprite.height());
    }
  }

  private void register(String material, Patch patch) {
    if (patches.putIfAbsent(material, patch) != null)
      throw new IllegalArgumentException("Duplicate material " + material);
    layers.values().forEach(mesh -> patch.map(mesh, material));
  }

  private Sprite sprite(String source) throws IOException {
    if (sprites.containsKey(source)) return sprites.get(source);
    String path = "/assets/" + source.replace(":", "/textures/") + ".png";
    BufferedImage image;
    try (var stream = IronHeldMesh.class.getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing texture " + source);
      image = ImageIO.read(stream);
    }
    int frameWidth = image.getWidth(), frameHeight = image.getHeight();
    JsonObject animation = null;
    try (var stream = IronHeldMesh.class.getResourceAsStream(path + ".mcmeta")) {
      if (stream != null) {
        var metadata =
            JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
        if (metadata.has("animation")) {
          animation = metadata.getAsJsonObject("animation");
          if (animation.has("width")) {
            frameWidth = animation.get("width").getAsInt();
            if (animation.has("height")) frameHeight = animation.get("height").getAsInt();
          } else if (animation.has("height")) {
            frameHeight = animation.get("height").getAsInt();
          } else {
            frameWidth = frameHeight = Math.min(frameWidth, frameHeight);
          }
        }
      }
    }
    if (frameWidth <= 0
        || frameHeight <= 0
        || image.getWidth() % frameWidth != 0
        || image.getHeight() % frameHeight != 0)
      throw new IOException("Invalid animation frame size for " + source);
    var sprite = new Sprite(image, frameWidth, frameHeight, animation);
    sprites.put(source, sprite);
    return sprite;
  }

  JsonObject write(Path assets, Map<Path, byte[]> output, String name, JsonObject display) {
    fields.forEach(
        (path, bytes) -> {
          var previous = output.putIfAbsent(assets.resolve(path), bytes);
          if (previous != null && !java.util.Arrays.equals(previous, bytes))
            throw new IllegalStateException("Conflicting equipment material field: " + path);
        });
    String directory = "models/item/equipment/iron/";
    Path folder = assets.resolve("irons_spellbooks").resolve(directory).resolve(name);
    String prefix = "irons_spellbooks:" + directory + name + "/";
    var materials = new StringBuilder();
    patches.forEach(
        (nameOfMaterial, patch) ->
            materials
                .append("newmtl ")
                .append(nameOfMaterial)
                .append("\nKa ")
                .append(ambient(nameOfMaterial))
                .append("\nKd 1 1 1 ")
                .append(patch.alpha())
                .append("\nmap_Kd ")
                .append(patch.texture())
                .append("\n\n"));
    EquipmentModels.text(
        output, folder.resolve("equipment.mtl"), materials.toString().stripTrailing() + "\n");
    JsonObject children = new JsonObject();
    JsonArray order = new JsonArray();
    for (var layer : layers.entrySet()) {
      String file = layer.getKey().substring(layer.getKey().indexOf(':') + 1) + ".obj";
      String key = layer.getKey().equals("minecraft:translucent") ? "glass" : "body";
      EquipmentModels.text(output, folder.resolve(file), layer.getValue().obj());
      children.add(key, model(prefix + file, layer.getKey()));
      order.add(key);
    }
    JsonObject model;
    if (layers.size() == 1) {
      model = children.getAsJsonObject(order.get(0).getAsString());
    } else {
      model = new JsonObject();
      model.addProperty("loader", "neoforge:composite");
      model.add("children", children);
      model.add("item_render_order", order);
      model.add("textures", textures());
    }
    model.add("display", display);
    return model;
  }

  private JsonObject model(String path, String renderType) {
    var result = new JsonObject();
    result.addProperty("loader", "neoforge:obj");
    result.addProperty("model", path);
    result.addProperty("automatic_culling", false);
    result.addProperty("emissive_ambient", !glowLevels.isEmpty());
    result.addProperty("flip_v", false);
    result.addProperty("ambientocclusion", false);
    result.addProperty("render_type", renderType);
    result.add("textures", textures());
    return result;
  }

  private String ambient(String material) {
    int level = glowLevels.getOrDefault(material, 0);
    if (level == 0) return "0 0 0";
    // NeoForge truncates the RGB average multiplied by 15 to a packed light level.
    float value = level == 15 ? 1 : (level + .25f) / 15;
    return value + " " + value + " " + value;
  }

  private JsonObject textures() {
    var result = new JsonObject();
    result.addProperty("particle", texture);
    if (patches.values().stream().anyMatch(patch -> patch.texture().equals("#dial")))
      result.addProperty("dial", texture);
    return result;
  }

  static JsonObject display(P grip, double scale, double pitch, double yaw, double roll) {
    JsonObject result = new JsonObject();
    for (String hand : HANDS) {
      var transform = new JsonObject();
      transform.add("rotation", vector(pitch, yaw, roll));
      transform.add("scale", vector(scale, scale, scale));
      result.add(hand, transform);
    }
    HeldItemModels.anchorHeld(
        result, new Vector3f((float) grip.x(), (float) grip.y(), (float) grip.z()));
    return result;
  }

  static JsonArray vector(double... values) {
    var result = new JsonArray();
    for (double value : values) result.add(value);
    return result;
  }

  private record Sprite(BufferedImage image, int width, int height, JsonObject animation) {}

  private record Patch(
      String texture, double u0, double v0, double u1, double v1, double alpha, boolean projected) {
    void map(EquipmentObjMesh mesh, String material) {
      if (projected) mesh.projectedField(material);
      else mesh.region(material, u0, v0, u1, v1);
    }
  }
}
