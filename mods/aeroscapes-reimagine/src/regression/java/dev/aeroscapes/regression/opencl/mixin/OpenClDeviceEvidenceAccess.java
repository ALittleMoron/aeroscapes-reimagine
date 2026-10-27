package dev.aeroscapes.regression.opencl.mixin;

import com.ishland.c2me.opts.accel.opencl.common.gen.OpenCLDevice;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads outstanding native compilations without joining on the server tick. */
@Mixin(value = OpenCLDevice.class, remap = false)
public interface OpenClDeviceEvidenceAccess {
  @Accessor("pendingActions")
  AtomicReference<CompletableFuture<Void>> aeroscapesRegression$pendingActions();
}
