package dev.aeroscapes.atmosphere;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Fill missing captions without replacing sound pools or resource-pack captions. */
public final class SoundCaptions {
  private static final Map<ResourceLocation, String> CAPTIONS = load();

  private SoundCaptions() {}

  public static String subtitle(ResourceLocation event, String original) {
    return original == null ? CAPTIONS.get(event) : original;
  }

  static Map<ResourceLocation, String> captions() {
    return CAPTIONS;
  }

  private static Map<ResourceLocation, String> load() {
    String path = "/assets/aeroscapes_reimagine/sound_captions.json";
    try (var stream = SoundCaptions.class.getResourceAsStream(path)) {
      if (stream == null) throw new IllegalStateException("Missing owned sound captions: " + path);
      var values =
          JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .getAsJsonObject();
      Map<ResourceLocation, String> captions = new HashMap<>();
      for (var entry : values.entrySet()) {
        var value = entry.getValue();
        if (!value.isJsonPrimitive()
            || !value.getAsJsonPrimitive().isString()
            || value.getAsString().isBlank())
          throw new IllegalStateException("Invalid caption for " + entry.getKey());
        captions.put(ResourceLocation.parse(entry.getKey()), value.getAsString());
      }
      return Map.copyOf(captions);
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot load owned sound captions", failure);
    }
  }
}
