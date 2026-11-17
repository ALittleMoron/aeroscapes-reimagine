package dev.aeroscapes.mixin.progression;

import harmonised.pmmo.client.events.ScreenHandler;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Project MMO's screen-edge skills panel with the one attached to the inventory. */
@Mixin(value = ScreenHandler.class, remap = false)
abstract class SkillPanelScreenHandlerMixin {
  @Inject(method = "onScreenInit", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned event handler injection.
  private static void aeroscapes$skipEdgePanel(ScreenEvent.Init.Post event, CallbackInfo info) {
    info.cancel();
  }
}
