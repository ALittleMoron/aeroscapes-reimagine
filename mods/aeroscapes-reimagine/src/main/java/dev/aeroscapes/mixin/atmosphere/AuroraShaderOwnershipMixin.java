package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.AuroraOwnership;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auroras 1.6.2 also draws under shaders; Complementary owns its sky aurora, MakeUp has none. */
@Pseudo
@Mixin(targets = "auroras.util.AuroraRenderer", remap = false)
public abstract class AuroraShaderOwnershipMixin {
  @Inject(method = "render", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned Auroras sky hooks.
  private void aeroscapes$shaderSky(CallbackInfo callback) {
    if (AuroraOwnership.shaderPackDrawsAurora()) callback.cancel();
  }
}
