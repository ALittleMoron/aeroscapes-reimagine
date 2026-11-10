package dev.aeroscapes.mixin.interfaceart;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.aeroscapes.interfaceart.InterfaceArt;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@SuppressWarnings("PMD.UnusedPrivateMethod")
@Mixin(LoadingOverlay.class)
public abstract class LoadingArtworkMixin {
  @ModifyArg(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"),
      index = 0)
  private ResourceLocation aeroscapes$loadingLogo(ResourceLocation original) {
    return InterfaceArt.available(InterfaceArt.SPLASH) ? InterfaceArt.SPLASH : original;
  }

  @WrapOperation(
      method = "render",
      at =
          @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;blendFunc(II)V"))
  private void aeroscapes$logoBlend(int source, int destination, Operation<Void> original) {
    if (InterfaceArt.available(InterfaceArt.SPLASH)) {
      RenderSystem.defaultBlendFunc();
    } else {
      original.call(source, destination);
    }
  }

  @ModifyArg(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;replaceAlpha(II)I"),
      index = 0)
  private int aeroscapes$fadeBackground(int original) {
    return InterfaceArt.loadingColor(original, InterfaceArt.LOADING_BACKGROUND);
  }

  @ModifyArgs(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/mojang/blaze3d/platform/GlStateManager;_clearColor(FFFF)V"))
  private void aeroscapes$startupBackground(Args colors) {
    if (InterfaceArt.available(InterfaceArt.SPLASH)) {
      colors.setAll(16 / 255.0F, 23 / 255.0F, 34 / 255.0F, 1.0F);
    }
  }

  @ModifyArg(
      method = "drawProgressBar",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
      index = 4)
  private int aeroscapes$progressColor(int original) {
    return InterfaceArt.loadingColor(original, InterfaceArt.LOADING_ACCENT);
  }
}
