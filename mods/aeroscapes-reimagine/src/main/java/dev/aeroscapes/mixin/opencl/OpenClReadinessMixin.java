package dev.aeroscapes.mixin.opencl;

import com.ishland.c2me.opts.accel.opencl.common.gen.CLServerWorldContext;
import dev.aeroscapes.worldgen.opencl.OpenClReadiness;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = CLServerWorldContext.class, remap = false)
public abstract class OpenClReadinessMixin implements OpenClReadiness {
  @Shadow @Final private ArrayList<CLServerWorldContext.DeviceWithProgram> openDevices;

  @Override
  public boolean aeroscapesReimagine$hasCompiledDevice() {
    // The pinned module publishes programs under this same monitor. Inspecting readiness
    // must not borrow a queue: a temporarily saturated device is still ready.
    synchronized (openDevices) {
      return !openDevices.isEmpty();
    }
  }
}
