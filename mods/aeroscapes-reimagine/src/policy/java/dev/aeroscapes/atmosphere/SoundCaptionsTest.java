package dev.aeroscapes.atmosphere;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

/** Caption real pinned events without replacing pools, music or resource-pack caption choices. */
public final class SoundCaptionsTest {
  private SoundCaptionsTest() {}

  public static void main(String[] arguments) throws IOException {
    var sounds = read("/assets/irons_spellbooks/sounds.json");
    var english = read("/assets/aeroscapes_reimagine/lang/en_us.json");
    var russian = read("/assets/aeroscapes_reimagine/lang/ru_ru.json");
    for (var entry : SoundCaptions.captions().entrySet()) {
      require(sounds.has(entry.getKey().getPath()), "Caption names a real pinned sound: " + entry);
      String caption = SoundCaptions.subtitle(entry.getKey(), null);
      var event = new WeighedSoundEvents(entry.getKey(), caption);
      require(
          event.getSubtitle().getContents() instanceof TranslatableContents translated
              && translated.getKey().equals(caption),
          "Native subtitle registry receives the translation key");
      require(english.has(caption) && russian.has(caption), "Both languages resolve: " + caption);
      require(
          !english.get(caption).getAsString().isBlank()
              && !russian.get(caption).getAsString().isBlank()
              && !english.get(caption).equals(russian.get(caption)),
          "Meaningful bilingual caption: " + caption);
      require(
          SoundCaptions.subtitle(entry.getKey(), "player.pack.caption")
              .equals("player.pack.caption"),
          "A resource pack's subtitle wins");
    }
    require(
        SoundCaptions.subtitle(ResourceLocation.withDefaultNamespace("entity.arrow.shoot"), null)
            == null,
        "Unselected sound events stay native");
    require(
        SoundCaptions.subtitle(ResourceLocation.parse("irons_spellbooks:ui.tick"), null) == null
            && SoundCaptions.subtitle(
                    ResourceLocation.parse("irons_spellbooks:music.dead_king_lullaby"), null)
                == null,
        "Decorative music and UI ticks do not flood event captions");
    System.out.println("Sound captions: real events, bilingual text and native precedence passed");
  }

  private static JsonObject read(String path) throws IOException {
    try (var stream = SoundCaptionsTest.class.getResourceAsStream(path)) {
      if (stream == null) throw new AssertionError("Missing fixture resource " + path);
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
