package dev.aeroscapes.mixin.opencl;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.diagnostics.LivenessProbe;
import dev.aeroscapes.worldgen.opencl.OpenClQueueProbe;
import org.lwjgl.opencl.CLEventCallbackI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    targets = "com.ishland.c2me.opts.accel.opencl.common.gen.CLEventCallbackManager",
    remap = false)
public abstract class OpenClEventDiagnosticsMixin {
  @Unique private volatile boolean aeroscapes$observing;

  @WrapOperation(
      method = "registerCallback",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/lwjgl/opencl/CL12;clSetEventCallback(JILorg/lwjgl/opencl/CLEventCallbackI;J)I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Preserve the native callback object and return value.
  private int aeroscapes$observeRegistrationResult(
      long event, int type, CLEventCallbackI callback, long data, Operation<Integer> original) {
    if (!LivenessProbe.enabled()) return original.call(event, type, callback, data);
    var tracked = OpenClQueueProbe.track(event);
    if (tracked == null) return original.call(event, type, callback, data);
    aeroscapes$observing = true;
    try {
      int result = original.call(event, type, callback, data);
      tracked.registered(result);
      return result;
    } catch (RuntimeException | Error failure) {
      tracked.registered(Integer.MIN_VALUE);
      throw failure;
    }
  }

  @Inject(method = "invokeInternal", at = @At("HEAD"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Observe the existing dispatcher without replacing its upcall.
  private void aeroscapes$observeCallback(
      long event, int status, long data, CallbackInfo callback) {
    if (aeroscapes$observing) OpenClQueueProbe.completed(event, status);
  }
}
