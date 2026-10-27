package dev.aeroscapes.mixin.opencl;

import com.ishland.c2me.opts.accel.opencl.common.gen.CLExecutors;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Releases devices after queued native builds and their synchronous result consumers. */
@Mixin(
    targets = "com.ishland.c2me.opts.accel.opencl.common.gen.CLServerGlobalContext",
    remap = false)
public abstract class OpenClShutdownMixin {
  @Unique private boolean aeroscapesReimagine$closing;

  @WrapMethod(method = "closeAllDevices")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras wraps the global shutdown boundary.
  private void aeroscapesReimagine$releaseAfterCompiler(Operation<Void> original) {
    if (aeroscapesReimagine$closing) return;
    aeroscapesReimagine$closing = true;
    // In the pinned module this is a single-worker FIFO pool. Its existing tasks also run
    // the world-program consumers, so the barrier includes their constant-buffer cleanup.
    // Return immediately: the synchronized outer method must release its monitor first.
    CompletableFuture<Void> release =
        CompletableFuture.runAsync(() -> original.call(), CLExecutors.COMPILE_POOL);
    release.whenComplete(
        (ignored, failure) -> {
          if (failure == null)
            LogUtils.getLogger().info("OpenCL resources released after compiler work");
          else LogUtils.getLogger().error("Failed to release deferred OpenCL resources", failure);
        });
    CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
        .execute(
            () -> {
              if (!release.isDone())
                LogUtils.getLogger().warn("OpenCL shutdown is waiting for queued compiler work");
            });
  }
}
