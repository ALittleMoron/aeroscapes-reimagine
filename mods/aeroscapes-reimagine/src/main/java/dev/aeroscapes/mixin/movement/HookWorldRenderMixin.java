package dev.aeroscapes.mixin.movement;

import dev.aeroscapes.movement.HookLineAttachment;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import punchy.client.render.FishingLineAttachment;

@Mixin(value = FishingLineAttachment.class, remap = false)
public abstract class HookWorldRenderMixin {
  @Inject(method = "beginWorld", at = @At("TAIL"), require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Snapshot the same world matrices as native rod lines.
  private static void aeroscapes$worldProjection(
      Camera camera, Matrix4f view, Matrix4f projection, CallbackInfo callback) {
    HookLineAttachment.beginWorld(camera, view, projection);
  }

  @Inject(method = "reset", at = @At("TAIL"), require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Release captures and their old world on native reset.
  private static void aeroscapes$resetProjection(CallbackInfo callback) {
    HookLineAttachment.reset();
  }
}
