package dev.aeroscapes.mixin.interfaceart;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.interfaceart.InterfaceArt;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@SuppressWarnings("PMD.UnusedPrivateMethod")
@Mixin(TitleScreen.class)
public abstract class TitleArtworkMixin {
  @WrapOperation(
      method = "renderPanorama",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/PanoramaRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;IIFF)V"))
  private void aeroscapes$atlas(
      PanoramaRenderer panorama,
      GuiGraphics graphics,
      int width,
      int height,
      float fade,
      float partialTick,
      Operation<Void> original) {
    if (InterfaceArt.available(InterfaceArt.ATLAS)) {
      InterfaceArt.drawAtlas(graphics, width, height, fade);
    } else {
      original.call(panorama, graphics, width, height, fade, partialTick);
    }
  }

  @WrapOperation(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
  private void aeroscapes$title(
      LogoRenderer logo, GuiGraphics graphics, int width, float fade, Operation<Void> original) {
    if (InterfaceArt.available(InterfaceArt.WORDMARK)) {
      InterfaceArt.drawTitle(graphics, width, graphics.guiHeight(), fade);
    } else {
      original.call(logo, graphics, width, fade);
    }
  }

  @WrapWithCondition(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"))
  private boolean aeroscapes$keepVanillaSplash(
      SplashRenderer splash, GuiGraphics graphics, int width, Font font, int color) {
    return !InterfaceArt.available(InterfaceArt.WORDMARK);
  }
}
