package dev.aeroscapes.mixin.interfaceart;

import dev.aeroscapes.interfaceart.ContainerArtwork;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CreativeModeInventoryScreen.class)
abstract class CreativeLabelArtworkMixin {
  @Shadow private static CreativeModeTab selectedTab;

  @ModifyArg(
      method = "renderLabels",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),
      index = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$creativeLabel(int original) {
    return original == 0x404040 && ContainerArtwork.usesPanel(selectedTab.getBackgroundTexture())
        ? ContainerArtwork.LABEL_COLOR
        : original;
  }
}
