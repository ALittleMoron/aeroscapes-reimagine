package dev.aeroscapes.mixin.camera;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.camera.ClimbableCameraFade;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets =
        "com.github.exopandora.shouldersurfing.plugin.callbacks.CameraEntityTransparencyCallback",
    remap = false)
abstract class ClimbableCameraFadeMixin {
  @ModifyReturnValue(method = "shouldRenderCameraEntityTransparent", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by MixinExtras.
  private static boolean aeroscapes$keepClimbersOpaque(
      boolean original, @Local(argsOnly = true) Entity entity) {
    return ClimbableCameraFade.shouldFade(original, entity);
  }
}
