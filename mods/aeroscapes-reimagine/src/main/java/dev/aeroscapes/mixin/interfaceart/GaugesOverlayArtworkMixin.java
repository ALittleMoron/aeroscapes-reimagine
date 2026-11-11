package dev.aeroscapes.mixin.interfaceart;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.interfaceart.ContainerArtwork;
import dev.aeroscapes.interfaceart.InterfaceArt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "wile.rsgauges.libmc.detail.Overlay$TextOverlayGui", remap = false)
abstract class GaugesOverlayArtworkMixin {
  @ModifyArgs(
      method = "renderOverlay",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/gui/GuiGraphics;fillGradient(IIIIII)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$panelFill(Args arguments) {
    if (InterfaceArt.available(InterfaceArt.ATLAS)) {
      arguments.set(4, 0xf0233245);
      arguments.set(5, 0xf0101722);
    }
  }

  @ModifyExpressionValue(
      method = "renderOverlay",
      at =
          @At(
              value = "FIELD",
              target = "Lwile/rsgauges/libmc/detail/Overlay$TextOverlayGui;border_color_:I"),
      require = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static int aeroscapes$panelBorder(int original) {
    return InterfaceArt.available(InterfaceArt.ATLAS) ? 0xffdbad6f : original;
  }

  @ModifyExpressionValue(
      method = "renderOverlay",
      at =
          @At(
              value = "FIELD",
              target = "Lwile/rsgauges/libmc/detail/Overlay$TextOverlayGui;text_color_:I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static int aeroscapes$labelColor(int original) {
    return InterfaceArt.available(InterfaceArt.ATLAS) ? ContainerArtwork.LABEL_COLOR : original;
  }

  @ModifyArg(
      method = "renderOverlay",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Keep link errors readable on the dark panel.
  private static Component aeroscapes$errorLabel(Component original) {
    return InterfaceArt.available(InterfaceArt.ATLAS)
            && TextColor.fromLegacyFormat(ChatFormatting.DARK_RED)
                .equals(original.getStyle().getColor())
        ? original.copy().withColor(0xff7777)
        : original;
  }
}
