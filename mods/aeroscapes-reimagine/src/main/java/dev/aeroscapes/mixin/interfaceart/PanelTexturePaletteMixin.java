package dev.aeroscapes.mixin.interfaceart;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aeroscapes.interfaceart.AtlasPanelPalette;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.renderer.texture.SimpleTexture$TextureImage")
abstract class PanelTexturePaletteMixin {
  @Redirect(
      method = "load",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/platform/NativeImage;read(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static NativeImage aeroscapes$loadPanel(
      InputStream stream, ResourceManager resources, ResourceLocation texture) throws IOException {
    return AtlasPanelPalette.read(texture, stream);
  }
}
