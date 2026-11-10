package dev.aeroscapes.mixin.interfaceart;

import dev.aeroscapes.interfaceart.ContainerArtwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MerchantScreen.class)
abstract class MerchantLabelArtworkMixin {
  @ModifyArg(
      method = "renderLabels",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),
      index = 4,
      require = 4,
      allow = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$merchantLabel(int original) {
    return ContainerArtwork.usesPanel((Screen) (Object) this)
        ? ContainerArtwork.LABEL_COLOR
        : original;
  }
}
