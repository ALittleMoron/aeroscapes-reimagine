package dev.aeroscapes.mixin.opencl;

import com.ishland.c2me.opts.accel.opencl.common.Config;
import com.ishland.c2me.opts.accel.opencl.common.util.CLUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.logging.LogUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL12;
import org.lwjgl.opencl.KHRICD;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Treats an installed ICD loader without platforms as an expected optional-acceleration case. */
@Mixin(
    targets = "com.ishland.c2me.opts.accel.opencl.common.enumeration.OpenCLDeviceLocator",
    remap = false)
public abstract class OpenClPlatformMixin {
  @ModifyExpressionValue(
      method = "enumerateAll",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/ishland/c2me/opts/accel/opencl/common/enumeration/OpenCLDeviceLocator;isAvailable()Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // MixinExtras observes successful loader initialization.
  private static boolean aeroscapesReimagine$platformsAvailable(boolean loaded) {
    if (!loaded) return false;
    try (MemoryStack stack = MemoryStack.stackPush()) {
      int status = CL12.clGetPlatformIDs((PointerBuffer) null, stack.callocInt(1));
      if (status == KHRICD.CL_PLATFORM_NOT_FOUND_KHR && Config.allowIncompatibilityFallback) {
        LogUtils.getLogger().warn("No OpenCL platforms installed; using CPU world generation");
        return false;
      }
      CLUtil.checkCLError(status);
      return true;
    }
  }
}
