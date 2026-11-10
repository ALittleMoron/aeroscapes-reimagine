package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.interfaceart.ContainerArtwork;
import fuzs.easyanvils.client.gui.components.FormattingGuideWidget;
import fuzs.easyanvils.client.gui.screens.inventory.ModAnvilScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ModAnvilScreen.class, remap = false)
abstract class EasyAnvilsFormattingGuideMixin extends AnvilScreen {
  protected EasyAnvilsFormattingGuideMixin(AnvilMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
  }

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
  private int aeroscapes$labelColor(int original) {
    return ContainerArtwork.usesPanel(ContainerArtwork.ANVIL)
        ? ContainerArtwork.LABEL_COLOR
        : original;
  }

  @WrapOperation(
      method = "subInit()V",
      at =
          @At(
              value = "NEW",
              target =
                  "(IILnet/minecraft/client/gui/Font;)Lfuzs/easyanvils/client/gui/components/FormattingGuideWidget;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private FormattingGuideWidget aeroscapes$placeGuideBesideName(
      int x, int y, Font font, Operation<FormattingGuideWidget> original) {
    // The guide's x argument is its right edge. The name-field sprite begins at x = 59.
    return original.call(getGuiLeft() + 58, getGuiTop() + 24, font);
  }
}
