package dev.aeroscapes.mixin.aerial;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep routine world entry free of the provider's unsolicited Telegram advertisement. */
@Pseudo
@Mixin(targets = "com.example.cr_aero_structures.cr_aero_structures", remap = false)
public abstract class VesselLoginMessageMixin {
  @Inject(method = "onPlayerLogin", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned provider's event handler.
  private void aeroscapesReimagine$omitAdvertisement(CallbackInfo callback) {
    callback.cancel();
  }
}
