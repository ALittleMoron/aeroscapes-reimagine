package dev.aeroscapes.mixin.interfaceart;

import dev.aeroscapes.interfaceart.InterfaceArt;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@SuppressWarnings("PMD.UnusedPrivateMethod")
@Mixin(InventoryScreen.class)
public abstract class InventoryArtworkMixin {
  @ModifyArg(
      method = "renderBg",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"),
      index = 0)
  private ResourceLocation aeroscapes$inventory(ResourceLocation original) {
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
  private int aeroscapes$labelColor(int original) {
    return InterfaceArt.available(InterfaceArt.INVENTORY) ? 0xebeef5 : original;
  }
}
