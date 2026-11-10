package dev.aeroscapes.mixin.interfaceart;

import dev.aeroscapes.interfaceart.ContainerArtwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AbstractContainerScreen.class)
abstract class ContainerLabelArtworkMixin {
  @ModifyArg(
      method = "renderLabels",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),
      index = 4,
      require = 2,
      allow = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$containerLabel(int original) {
    return ContainerArtwork.usesPanel((Screen) (Object) this)
        ? ContainerArtwork.LABEL_COLOR
        : original;
  }
}
