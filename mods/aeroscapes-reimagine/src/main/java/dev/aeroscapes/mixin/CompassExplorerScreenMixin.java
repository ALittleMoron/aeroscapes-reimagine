package dev.aeroscapes.mixin;

import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import dev.aeroscapes.qualityoflife.client.CompassSearchUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ExplorersCompassScreen.class, remap = false)
public abstract class CompassExplorerScreenMixin extends Screen {
  @Unique private boolean aeroscapes$extended;

  protected CompassExplorerScreenMixin(Component title) {
    super(title);
  }

  @Inject(method = "init", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$rangeButton(CallbackInfo callback) {
    addRenderableWidget(
        CompassSearchUi.rangeButton(
            false, aeroscapes$extended, extended -> aeroscapes$extended = extended));
  }

  @Inject(method = "render", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$prices(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
    CompassSearchUi.renderPrices(graphics, font, false);
  }

  @Inject(method = "searchForStructure", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$request(ResourceLocation target, CallbackInfo callback) {
    if (target != null) CompassSearchUi.send(false, target, false, false, aeroscapes$extended);
    callback.cancel();
  }

  @Inject(method = "searchForGroup", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$group(ResourceLocation target, CallbackInfo callback) {
    if (target != null) CompassSearchUi.send(false, target, true, false, aeroscapes$extended);
    callback.cancel();
  }

  @Inject(method = "searchForNext", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$next(CallbackInfo callback) {
    CompassSearchUi.send(
        false, ResourceLocation.withDefaultNamespace("empty"), false, true, aeroscapes$extended);
    callback.cancel();
  }
}
