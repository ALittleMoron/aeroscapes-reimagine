package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.aeroscapes.engineering.OilRockBlock;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/** Stains installed host textures at reload; provider artwork stays with its provider. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class OilRockSprites implements SpriteSource {
  private static final MapCodec<OilRockSprites> CODEC = MapCodec.unit(OilRockSprites::new);
  private static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

  @SubscribeEvent
  public static void register(RegisterSpriteSourceTypesEvent event) {
    event.register(ResourceLocation.parse("aeroscapes_reimagine:oil_rocks"), TYPE);
  }

  @Override
  public SpriteSourceType type() {
    return TYPE;
  }

  @Override
  public void run(ResourceManager resources, Output output) {
    for (var host : OilRockBlock.Host.values())
      for (boolean top :
          host.hasDistinctTop() ? new boolean[] {false, true} : new boolean[] {false}) {
        ResourceLocation sprite =
            ResourceLocation.parse(
                "aeroscapes_reimagine:block/oil_" + host.getSerializedName() + (top ? "_top" : ""));
        output.add(sprite, loader -> compose(resources, host.texture(top), sprite));
      }
  }

  private static SpriteContents compose(
      ResourceManager resources, ResourceLocation texture, ResourceLocation sprite) {
    ResourceLocation source = texture.withPath("textures/" + texture.getPath() + ".png");
    try (NativeImage stone = read(resources, source);
        NativeImage stain =
            read(
                resources,
                ResourceLocation.parse("aeroscapes_reimagine:textures/block/oil_stain.png"))) {
      if (stone.getWidth() != stone.getHeight() || stain.getWidth() != stain.getHeight())
        throw new IOException("Oil rock composition requires square host and stain textures");
      int size = Math.max(stone.getWidth(), stain.getWidth());
      NativeImage image = new NativeImage(size, size, false);
      for (int y = 0; y < size; y++)
        for (int x = 0; x < size; x++)
          image.setPixelRGBA(
              x,
              y,
              blend(
                  stone.getPixelRGBA(x * stone.getWidth() / size, y * stone.getHeight() / size),
                  stain.getPixelRGBA(x * stain.getWidth() / size, y * stain.getHeight() / size)));
      return new SpriteContents(sprite, new FrameSize(size, size), image, ResourceMetadata.EMPTY);
    } catch (IOException failure) {
      LogUtils.getLogger()
          .error("Cannot compose oil-bearing rock {} from {}", sprite, source, failure);
      return null;
    }
  }

  public static int blend(int stone, int stain) {
    int alpha = FastColor.ABGR32.alpha(stain);
    return FastColor.ABGR32.color(
        255,
        channel(FastColor.ABGR32.blue(stone), FastColor.ABGR32.blue(stain), alpha),
        channel(FastColor.ABGR32.green(stone), FastColor.ABGR32.green(stain), alpha),
        channel(FastColor.ABGR32.red(stone), FastColor.ABGR32.red(stain), alpha));
  }

  private static int channel(int base, int stain, int alpha) {
    return (base * (255 - alpha) + stain * alpha + 127) / 255;
  }

  private static NativeImage read(ResourceManager resources, ResourceLocation id)
      throws IOException {
    try (InputStream input = resources.getResourceOrThrow(id).open()) {
      return NativeImage.read(input);
    }
  }
}
