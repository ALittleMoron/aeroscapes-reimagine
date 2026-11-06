package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
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

/** Paints from locally installed artwork during atlas reload, without redistributing its pixels. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public record EquipmentPaintSprites(
    ResourceLocation sprite, ResourceLocation resource, EquipmentStyle profile)
    implements SpriteSource {
  public static final MapCodec<EquipmentPaintSprites> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      ResourceLocation.CODEC
                          .fieldOf("sprite")
                          .forGetter(EquipmentPaintSprites::sprite),
                      ResourceLocation.CODEC
                          .fieldOf("resource")
                          .forGetter(EquipmentPaintSprites::resource),
                      EquipmentStyle.CODEC
                          .fieldOf("profile")
                          .forGetter(EquipmentPaintSprites::profile))
                  .apply(instance, EquipmentPaintSprites::new));
  private static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

  @SubscribeEvent
  public static void register(RegisterSpriteSourceTypesEvent event) {
    event.register(ResourceLocation.parse("aeroscapes_reimagine:equipment_paint"), TYPE);
  }

  @Override
  public SpriteSourceType type() {
    return TYPE;
  }

  @Override
  public void run(ResourceManager resources, Output output) {
    output.add(sprite, loader -> compose(resources));
  }

  private SpriteContents compose(ResourceManager resources) {
    var source =
        ResourceLocation.fromNamespaceAndPath(
            resource.getNamespace(), "textures/" + resource.getPath() + ".png");
    try (var input = resources.getResourceOrThrow(source).open();
        NativeImage original = NativeImage.read(input)) {
      BufferedImage icon =
          new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < original.getHeight(); y++)
        for (int x = 0; x < original.getWidth(); x++)
          icon.setRGB(x, y, FastColor.ABGR32.fromArgb32(original.getPixelRGBA(x, y)));
      BufferedImage paint = EquipmentArtwork.paint(icon, profile);
      NativeImage result = new NativeImage(EquipmentArtwork.SIZE, EquipmentArtwork.SIZE, false);
      for (int y = 0; y < EquipmentArtwork.SIZE; y++)
        for (int x = 0; x < EquipmentArtwork.SIZE; x++)
          result.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(paint.getRGB(x, y)));
      return new SpriteContents(
          sprite,
          new FrameSize(EquipmentArtwork.SIZE, EquipmentArtwork.SIZE),
          result,
          ResourceMetadata.EMPTY);
    } catch (IOException | IllegalArgumentException failure) {
      LogUtils.getLogger()
          .error("Cannot compose equipment paint {} from {}", sprite, source, failure);
      return null;
    }
  }
}
