package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;

/** Pack overrides take precedence over the pinned native artifact's textures. */
final class PreviewTextures {
  private final Path assets;
  private final long tick;
  private final Map<String, BufferedImage> cache = new HashMap<>();

  PreviewTextures(Path assets, long tick) {
    this.assets = assets;
    this.tick = tick;
  }

  private InputStream open(String key) throws IOException {
    Path path = assets.resolve(key);
    return Files.isRegularFile(path)
        ? Files.newInputStream(path)
        : getClass().getResourceAsStream("/assets/" + key);
  }

  BufferedImage load(String name) throws IOException {
    var cached = cache.get(name);
    if (cached != null) return cached;
    var id = ResourceLocation.parse(name);
    String key = id.getNamespace() + "/textures/" + id.getPath() + ".png";
    BufferedImage image;
    try (var input = open(key)) {
      if (input == null) throw new IOException("Missing preview sprite: " + name);
      image = ImageIO.read(input);
      if (image == null) throw new IOException("Unreadable preview sprite: " + name);
    }
    try (var metadata = open(key + ".mcmeta")) {
      if (metadata != null) {
        var section =
            JsonParser.parseReader(new InputStreamReader(metadata, StandardCharsets.UTF_8))
                .getAsJsonObject();
        if (section.has("animation")) image = frame(image, section.getAsJsonObject("animation"));
      }
    }
    cache.put(name, image);
    return image;
  }

  private BufferedImage frame(BufferedImage sheet, JsonObject animation) throws IOException {
    boolean hasW = animation.has("width"), hasH = animation.has("height");
    int side = Math.min(sheet.getWidth(), sheet.getHeight());
    int width = hasW ? animation.get("width").getAsInt() : hasH ? sheet.getWidth() : side;
    int height = hasH ? animation.get("height").getAsInt() : hasW ? sheet.getHeight() : side;
    if (width <= 0
        || height <= 0
        || sheet.getWidth() % width != 0
        || sheet.getHeight() % height != 0) throw new IOException("Invalid sprite frame size");
    int columns = sheet.getWidth() / width, count = columns * (sheet.getHeight() / height);
    int duration = animation.has("frametime") ? animation.get("frametime").getAsInt() : 1;
    var frames = new ArrayList<int[]>();
    if (animation.has("frames"))
      for (var f : animation.getAsJsonArray("frames")) {
        var obj = f.isJsonObject() ? f.getAsJsonObject() : null;
        frames.add(
            new int[] {
              obj == null ? f.getAsInt() : obj.get("index").getAsInt(),
              obj != null && obj.has("time") ? obj.get("time").getAsInt() : duration
            });
      }
    if (frames.isEmpty()) for (int i = 0; i < count; i++) frames.add(new int[] {i, duration});
    int total = 0;
    for (var f : frames) {
      if (f[0] < 0 || f[0] >= count || f[1] <= 0) throw new IOException("Invalid sprite animation");
      total = Math.addExact(total, f[1]);
    }
    long cursor = Math.floorMod(tick, total);
    for (var f : frames) {
      if (cursor < f[1])
        return sheet.getSubimage(
            (f[0] % columns) * width, (f[0] / columns) * height, width, height);
      cursor -= f[1];
    }
    throw new IOException("Empty sprite animation");
  }
}
