package dev.aeroscapes.regression.opencl.mixin;

import com.ishland.c2me.opts.accel.opencl.common.gen.CLServerGlobalContext;
import com.ishland.c2me.opts.accel.opencl.common.gen.CLServerWorldContext;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Includes temporary world contexts that are absent from MinecraftServer.getAllLevels(). */
@Mixin(value = CLServerGlobalContext.class, remap = false)
public interface OpenClGlobalEvidenceAccess {
  @Accessor("registeredWorlds")
  ReferenceArrayList<CLServerWorldContext> aeroscapesRegression$registeredWorlds();
}
