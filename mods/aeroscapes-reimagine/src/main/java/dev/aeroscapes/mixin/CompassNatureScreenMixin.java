package dev.aeroscapes.mixin;

import com.chaosthedude.naturescompass.gui.NaturesCompassScreen;
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

@Mixin(value = NaturesCompassScreen.class, remap = false)
public abstract class CompassNatureScreenMixin extends Screen {
  @Unique private boolean aeroscapes$extended;

  protected CompassNatureScreenMixin(Component title) {
    super(title);
  }

  @Inject(method = "init", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$rangeButton(CallbackInfo callback) {
    addRenderableWidget(
        CompassSearchUi.rangeButton(
            true, aeroscapes$extended, extended -> aeroscapes$extended = extended));
  }

  @Inject(method = "render", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$prices(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
    CompassSearchUi.renderPrices(graphics, font, true);
  }

  @Inject(method = "searchForBiome", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$request(ResourceLocation target, CallbackInfo callback) {
    if (target != null) CompassSearchUi.send(true, target, false, false, aeroscapes$extended);
    callback.cancel();
  }

  @Inject(method = "searchForNextBiome", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$next(CallbackInfo callback) {
    CompassSearchUi.send(
        true, ResourceLocation.withDefaultNamespace("empty"), false, true, aeroscapes$extended);
    callback.cancel();
  }
}
