package dev.aeroscapes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.herobrot.tieredneo.reforge.ReforgeScreen", remap = false)
abstract class ReforgeCatalystHintMixin {
  @Inject(method = "render", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$showCatalystHint(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
    var screen = (AbstractContainerScreen<?>) (Object) this;
    var menu = screen.getMenu();
    var slot = menu.getSlot(2);
    int left = screen.getGuiLeft() + slot.x;
    int top = screen.getGuiTop() + slot.y;
    if (slot.hasItem()
        || !menu.getCarried().isEmpty()
        || mouseX < left
        || mouseX >= left + 16
        || mouseY < top
        || mouseY >= top + 16) return;
    var lapis = Items.LAPIS_LAZULI.getDefaultInstance();
    if (slot.mayPlace(lapis)) {
      graphics.renderTooltip(
          Minecraft.getInstance().font,
          Component.translatable(
              "aeroscapes_reimagine.equipment.reforge_catalyst", lapis.getHoverName()),
          mouseX,
          mouseY);
    }
  }
}
