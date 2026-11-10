package dev.aeroscapes.interfaceart;

import com.google.common.hash.Hashing;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

public final class AtlasPanelPalette {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final ResourceLocation INDEX =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "atlas/panels.json");
  private static final Codec<Map<ResourceLocation, String>> CODEC =
      Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING);
  private static final ResourceLocation CHIPPED_WORKBENCH =
      ResourceLocation.parse("chipped:textures/gui/container/workbench.png");
  private static final ResourceLocation TRANSMOG_TABLE =
      ResourceLocation.parse("irons_lib:textures/gui/transmog_table.png");
  private static final Map<Integer, Integer> WORKBENCH_COLORS =
      Map.of(
          0xff335469, 0xff695342,
          0xff6298bc, 0xffd0bdb0,
          0xff7caacc, 0xffdaccbf,
          0xff234256, 0xff584130);
  private static final Map<Integer, Integer> TRANSMOG_COLORS =
      Map.of(
          0xff695d5e, 0xff8d7762,
          0xff504245, 0xff5e4531,
          0xffae9a87, 0xffc1ac99);
  private static volatile Map<ResourceLocation, String> auditedTextures = Map.of();

  private AtlasPanelPalette() {}

  static void refresh(ResourceManager resources) {
    auditedTextures = Map.of();
    var artwork = resources.getResource(InterfaceArt.ATLAS);
    var index = resources.getResource(INDEX);
    if (artwork.isEmpty()
        || index
            .map(Resource::sourcePackId)
            .filter(artwork.get().sourcePackId()::equals)
            .isEmpty()) {
      return;
    }

    try (var reader = index.get().openAsReader()) {
      var parsed = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
      if (parsed.values().stream().anyMatch(digest -> !digest.matches("[a-f0-9]{64}"))) {
        throw new IllegalArgumentException("Invalid SHA-256 in Island Atlas panel index");
      }
      auditedTextures = Map.copyOf(parsed);
    } catch (IOException | RuntimeException exception) {
      LOGGER.warn("Cannot read the Island Atlas panel index", exception);
    }
  }

  public static NativeImage read(ResourceLocation texture, InputStream stream) throws IOException {
    String expected = auditedTextures.get(texture);
    if (expected == null) return NativeImage.read(stream);

    byte[] png = stream.readAllBytes();
    var image = NativeImage.read(new ByteArrayInputStream(png));
    // The provider can change the same path and a higher-priority pack can supply different art.
    if (Hashing.sha256().hashBytes(png).toString().equals(expected)) recolor(texture, image);
    return image;
  }

  private static void recolor(ResourceLocation texture, NativeImage image) {
    boolean terminal =
        texture.getNamespace().equals("toms_storage")
            && (texture.getPath().equals("textures/gui/storage_terminal.png")
                || texture.getPath().equals("textures/gui/crafting_terminal.png"));
    boolean barrel =
        texture.getNamespace().equals("sophisticatedstorage")
            && texture.getPath().equals("textures/gui/limited_barrels.png");
    boolean workbench = texture.equals(CHIPPED_WORKBENCH);
    boolean transmog = texture.equals(TRANSMOG_TABLE);
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int original = image.getPixelRGBA(x, y);
        int changed =
            switch (original) {
              case 0xffc4c4c4, 0xffc5c5c5, 0xffc6c6c6, 0xffc7c7c7 -> 0xffd7c3b0;
              // Tom's uses the slot gray for its white-text search input too.
              case 0xff898989, 0xff8a8a8a, 0xff8b8b8b ->
                  terminal && x >= 80 && x < 172 && y >= 4 && y < 17 ? 0xff513c2b : 0xffaa9480;
              case 0xffa4a4a4 -> barrel ? 0xffb9a995 : original;
              case 0xff969696 -> barrel ? 0xffab9883 : original;
              default -> original;
            };
        // Leave the painted tool rack and the transmog preview windows alone.
        if (workbench && (x >= 80 || y < 146)) {
          changed = WORKBENCH_COLORS.getOrDefault(original, changed);
        } else if (transmog) {
          changed = TRANSMOG_COLORS.getOrDefault(original, changed);
        }
        if (changed != original) image.setPixelRGBA(x, y, changed);
      }
    }
  }
}
