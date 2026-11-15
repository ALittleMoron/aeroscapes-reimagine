package dev.aeroscapes.sky.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.aeroscapes.sky.WhaleTextureComposer;
import dev.aeroscapes.sky.WhaleTextureComposer.Pixels;
import dev.aeroscapes.sky.WhaleVariety;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.FastColor;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Decodes and composes on reload workers; render calls only consult the completed texture map. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class WhaleBlockTextures {
  private static final ResourceLocation NATIVE =
      ResourceLocation.parse("skywhales:textures/entity/sky_whale.png");
  private static final ResourceLocation LAYOUT =
      ResourceLocation.parse("aeroscapes_reimagine:whales/material_layout.json");
  private static volatile Map<WhaleVariety, ResourceLocation> available = Map.of();

  private WhaleBlockTextures() {}

  @SubscribeEvent
  public static void register(RegisterClientReloadListenersEvent event) {
    boolean supported =
        ModList.get()
            .getModContainerById("skywhales")
            .map(container -> "0.1.9".equals(container.getModInfo().getVersion().toString()))
            .orElse(false);
    if (!supported) return;
    event.registerReloadListener(new Reload());
  }

  public static ResourceLocation texture(WhaleVariety variety) {
    return available.getOrDefault(variety, NATIVE);
  }

  private static final class Reload
      extends SimplePreparableReloadListener<Map<WhaleVariety, Pixels>> {
    @Override
    protected Map<WhaleVariety, Pixels> prepare(
        ResourceManager resources, ProfilerFiller profiler) {
      try {
        JsonObject layouts = readJson(resources, LAYOUT);
        var identifiers = new HashSet<String>();
        for (WhaleVariety variety : WhaleVariety.values())
          identifiers.addAll(
              WhaleTextureComposer.requiredTextureIds(layouts.getAsJsonObject(variety.id())));
        Map<String, Pixels> sources = new HashMap<>();
        for (String identifier : identifiers) {
          ResourceLocation sprite = ResourceLocation.parse(identifier);
          sources.put(
              identifier,
              readPixels(resources, sprite.withPath("textures/" + sprite.getPath() + ".png")));
        }
        Map<WhaleVariety, Pixels> prepared = new EnumMap<>(WhaleVariety.class);
        for (WhaleVariety variety : WhaleVariety.values())
          prepared.put(
              variety,
              WhaleTextureComposer.compose(
                  variety.id(),
                  readJson(resources, variety.model()),
                  layouts.getAsJsonObject(variety.id()),
                  sources));
        return Map.copyOf(prepared);
      } catch (IOException failure) {
        throw new UncheckedIOException(
            "Cannot prepare whale textures from installed block materials", failure);
      }
    }

    @Override
    protected void apply(
        Map<WhaleVariety, Pixels> prepared, ResourceManager resources, ProfilerFiller profiler) {
      Map<WhaleVariety, DynamicTexture> replacements = new EnumMap<>(WhaleVariety.class);
      try {
        for (var entry : prepared.entrySet()) {
          Pixels pixels = entry.getValue();
          NativeImage image = new NativeImage(pixels.width(), pixels.height(), false);
          try {
            for (int y = 0; y < pixels.height(); y++)
              for (int x = 0; x < pixels.width(); x++) {
                int color = pixels.get(x, y);
                image.setPixelRGBA(
                    x,
                    y,
                    FastColor.ABGR32.color(
                        color >>> 24, color & 255, (color >> 8) & 255, (color >> 16) & 255));
              }
            replacements.put(entry.getKey(), new DynamicTexture(image));
          } catch (RuntimeException failure) {
            image.close();
            throw failure;
          }
        }
      } catch (RuntimeException failure) {
        replacements.values().forEach(DynamicTexture::close);
        throw failure;
      }
      Map<WhaleVariety, ResourceLocation> completed = new EnumMap<>(WhaleVariety.class);
      var textures = Minecraft.getInstance().getTextureManager();
      for (var entry : replacements.entrySet()) {
        ResourceLocation id = entry.getKey().texture();
        // TextureManager.register closes the replaced texture after the new GPU image exists.
        textures.register(id, entry.getValue());
        completed.put(entry.getKey(), id);
      }
      available = Map.copyOf(completed);
    }
  }

  private static JsonObject readJson(ResourceManager resources, ResourceLocation id)
      throws IOException {
    try (var reader = new InputStreamReader(resources.open(id), StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  private static Pixels readPixels(ResourceManager resources, ResourceLocation id)
      throws IOException {
    try (var input = resources.open(id);
        NativeImage image = NativeImage.read(input)) {
      int[] pixels = new int[Math.multiplyExact(image.getWidth(), image.getHeight())];
      for (int y = 0; y < image.getHeight(); y++)
        for (int x = 0; x < image.getWidth(); x++) {
          int color = image.getPixelRGBA(x, y);
          pixels[y * image.getWidth() + x] =
              (FastColor.ABGR32.alpha(color) << 24)
                  | (FastColor.ABGR32.red(color) << 16)
                  | (FastColor.ABGR32.green(color) << 8)
                  | FastColor.ABGR32.blue(color);
        }
      return new Pixels(image.getWidth(), image.getHeight(), pixels);
    }
  }
}
