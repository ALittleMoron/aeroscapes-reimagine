package dev.aeroscapes.mixin.interfaceart;

import dev.aeroscapes.interfaceart.ContainerArtwork;
import dev.aeroscapes.interfaceart.InterfaceArt;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "top.theillusivec4.curios.client.gui.CuriosScreen", remap = false)
abstract class CuriosInventoryArtworkMixin {
  @ModifyArg(
      method = "renderBg",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ResourceLocation aeroscapes$inventoryPanel(ResourceLocation original) {
    return InterfaceArt.available(InterfaceArt.INVENTORY) ? InterfaceArt.INVENTORY : original;
  }

  @ModifyArg(
      method = "renderLabels",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),
      index = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$labelColor(int original) {
    return InterfaceArt.available(InterfaceArt.INVENTORY) ? ContainerArtwork.LABEL_COLOR : original;
  }
}
