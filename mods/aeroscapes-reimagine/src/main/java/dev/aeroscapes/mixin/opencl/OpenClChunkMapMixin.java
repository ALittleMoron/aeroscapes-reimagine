package dev.aeroscapes.mixin.opencl;

import com.ishland.c2me.opts.accel.opencl.common.Config;
import com.ishland.c2me.opts.accel.opencl.common.gen.CLServerWorldContext;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.worldgen.opencl.OpenClReadiness;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps native CPU paths available while GPU programs compile or after they fail. */
@Mixin(value = ChunkMap.class, priority = 900)
public abstract class OpenClChunkMapMixin {
  @ModifyReturnValue(method = "c2me$getCLContext", at = @At("RETURN"), remap = false)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Method added by the higher-priority C2ME mixin.
  private CLServerWorldContext aeroscapesReimagine$readyContext(CLServerWorldContext context) {
    if (context != null
        && Config.allowIncompatibilityFallback
        && !((OpenClReadiness) context).aeroscapesReimagine$hasCompiledDevice()) return null;
    // Retain the real backing field so native world/device cleanup still sees it.
    return context;
  }
}
