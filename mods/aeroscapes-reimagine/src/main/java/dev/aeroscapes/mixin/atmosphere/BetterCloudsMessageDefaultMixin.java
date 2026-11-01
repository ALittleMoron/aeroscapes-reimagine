package dev.aeroscapes.mixin.atmosphere;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.qendolin.betterclouds.config.Config", remap = false)
public abstract class BetterCloudsMessageDefaultMixin {
  @Shadow public boolean cloudsDisabledMessageEnabled;

  @Inject(method = "<init>()V", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Called by the Better Clouds constructor.
  private void aeroscapes$disableCloudWarningByDefault(CallbackInfo callback) {
    cloudsDisabledMessageEnabled = false;
  }
}
