package dev.aeroscapes.mixin.sky;

import dev.aeroscapes.sky.WhaleLifecycle;
import dev.dacite.skywhales.entity.SkyWhale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkyWhale.class, remap = false)
public abstract class WhaleLifecycleMixin {
  @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native code discards the encounter immediately.
  private void aeroscapes$distantGrace(CallbackInfo callback) {
    WhaleLifecycle.checkDespawn((SkyWhale) (Object) this);
    callback.cancel();
  }
}
