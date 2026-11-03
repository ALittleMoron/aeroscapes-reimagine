package dev.aeroscapes.mixin.survival;

import dev.aeroscapes.survival.NutritionPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.dannyandson.nutritionalbalance.lunchbox.LunchBoxScreen", remap = false)
public abstract class NutritionLunchboxMixin<T extends AbstractContainerMenu>
    extends AbstractContainerScreen<T> {
  protected NutritionLunchboxMixin(T menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
  }

  @Inject(method = "renderBg", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$nutritionBackground(
      GuiGraphics graphics, float partialTicks, int mouseX, int mouseY, CallbackInfo callback) {
    NutritionPanel.render(graphics, leftPos, topPos);
  }

  @Inject(method = "render", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$renderHelp(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo callback) {
    NutritionPanel.renderTooltip(graphics, leftPos, topPos, mouseX, mouseY);
  }
}
