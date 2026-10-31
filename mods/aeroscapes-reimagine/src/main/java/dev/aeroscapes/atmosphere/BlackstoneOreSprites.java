package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.aeroscapes.worldgen.BlackstoneOres;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/** Composes installed artwork locally; provider pixels are never distributed in the owned JAR. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class BlackstoneOreSprites implements SpriteSource {
  private static final MapCodec<BlackstoneOreSprites> CODEC =
      MapCodec.unit(BlackstoneOreSprites::new);
  private static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

  @SubscribeEvent
  public static void register(RegisterSpriteSourceTypesEvent event) {
    event.register(ResourceLocation.parse("aeroscapes_reimagine:blackstone_ores"), TYPE);
  }

  @Override
  public SpriteSourceType type() {
    return TYPE;
  }

  @Override
  public void run(ResourceManager resources, Output output) {
    for (String material : BlackstoneOres.MATERIALS)
      for (String suffix : new String[] {"", "_top"}) {
        ResourceLocation sprite =
            ResourceLocation.parse(
                "aeroscapes_reimagine:block/blackstone_" + material + "_ore" + suffix);
        output.add(sprite, loader -> compose(resources, loader, material, suffix, sprite));
      }
  }

  private static SpriteContents compose(
      ResourceManager resources,
      SpriteResourceLoader loader,
      String material,
      String suffix,
      ResourceLocation sprite) {
    String namespace = BlackstoneOres.sourceNamespace(material);
    ResourceLocation ore =
        ResourceLocation.fromNamespaceAndPath(
            namespace, "textures/block/deepslate_" + material + "_ore.png");
    try (NativeImage original = read(resources, ore);
        NativeImage deepslate =
            read(resources, ResourceLocation.parse("minecraft:textures/block/deepslate.png"));
        NativeImage blackstone =
            read(
                resources,
                ResourceLocation.parse("minecraft:textures/block/blackstone" + suffix + ".png"))) {
      if (original.getWidth() != deepslate.getWidth()
          || original.getHeight() != deepslate.getHeight()
          || original.getWidth() != original.getHeight()
          || blackstone.getWidth() != blackstone.getHeight())
        throw new IOException(
            "Blackstone ore composition requires matching square ore/base textures");
      int size = Math.max(original.getWidth(), blackstone.getWidth());
      NativeImage result = new NativeImage(size, size, false);
      for (int x = 0; x < size; x++)
        for (int y = 0; y < size; y++) {
          int sourceX = x * original.getWidth() / size;
          int sourceY = y * original.getHeight() / size;
          int orePixel = original.getPixelRGBA(sourceX, sourceY);
          int basePixel = deepslate.getPixelRGBA(sourceX, sourceY);
          result.setPixelRGBA(
              x,
              y,
              orePixel == basePixel
                  ? blackstone.getPixelRGBA(
                      x * blackstone.getWidth() / size, y * blackstone.getHeight() / size)
                  : orePixel);
        }
      return new SpriteContents(sprite, new FrameSize(size, size), result, ResourceMetadata.EMPTY);
    } catch (IOException failure) {
      LogUtils.getLogger()
          .warn("Cannot compose {}; using its installed ore texture", sprite, failure);
      return resources
          .getResource(ore)
          .map(resource -> loader.loadSprite(sprite, resource))
          .orElse(null);
    }
  }

  private static NativeImage read(ResourceManager resources, ResourceLocation id)
      throws IOException {
    try (InputStream input = resources.getResourceOrThrow(id).open()) {
      return NativeImage.read(input);
    }
  }
}
