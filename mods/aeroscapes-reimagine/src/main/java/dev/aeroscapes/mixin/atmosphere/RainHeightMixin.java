package dev.aeroscapes.mixin.atmosphere;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pigcart.particlerain.VersionUtil;

@Mixin(value = VersionUtil.class, remap = false)
public abstract class RainHeightMixin {
  @Inject(method = "getMinY", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin replaces PR's fixed 1.21.1 scan floor.
  private static void aeroscapes$minimum(Level level, CallbackInfoReturnable<Integer> callback) {
    callback.setReturnValue(level.getMinBuildHeight());
  }

  @Inject(method = "getMaxY", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // PR's height fallback expects an inclusive ceiling.
  private static void aeroscapes$maximum(Level level, CallbackInfoReturnable<Integer> callback) {
    callback.setReturnValue(level.getMaxBuildHeight() - 1);
  }
}
