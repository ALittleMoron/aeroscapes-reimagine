package dev.aeroscapes.mixin.survival;

import dev.aeroscapes.interfaceart.InterfaceArt;
import dev.aeroscapes.survival.NutritionPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.dannyandson.nutritionalbalance.gui.NutrientGUI", remap = false)
public abstract class NutritionScreenMixin extends Screen {
  protected NutritionScreenMixin(Component title) {
    super(title);
  }

  @Inject(method = "render", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$renderPanel(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo callback) {
    renderBackground(graphics, mouseX, mouseY, partialTicks);
  }

  @Inject(method = "render", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$renderHelp(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo callback) {
    NutritionPanel.renderTooltip(
        graphics, Math.max(0, (width - 250) / 2), Math.max(0, (height - 155) / 2), mouseX, mouseY);
  }

  @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$nutritionBackground(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo callback) {
    graphics.fill(0, 0, width, height, 0xa0202428);
    int left = Math.max(0, (width - 250) / 2);
    int top = Math.max(0, (height - 155) / 2);
    int frameColor = InterfaceArt.available(InterfaceArt.ATLAS) ? 0xff111b29 : 0xff292d30;
    graphics.fill(left, top, left + 250, top + 155, frameColor);
    NutritionPanel.render(graphics, left, top);
    callback.cancel();
  }
}
