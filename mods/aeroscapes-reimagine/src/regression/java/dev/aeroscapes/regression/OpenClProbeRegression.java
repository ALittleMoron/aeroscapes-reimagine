package dev.aeroscapes.regression;

import com.ishland.c2me.opts.accel.opencl.common.ducks.MinecraftServerExtension;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.diagnostics.LivenessProbe;
import dev.aeroscapes.worldgen.opencl.OpenClQueueProbe;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL11;
import org.lwjgl.system.MemoryStack;

/** Uses an owned pending native event, never a game's production command or event. */
public final class OpenClProbeRegression {
  private OpenClProbeRegression() {}

  public static void verify(ServerLevel level) {
    require(LivenessProbe.enabled(), "Enable the diagnostic marker before this dedicated test");
    var global = ((MinecraftServerExtension) level.getServer()).c2me$getCLContext();
    var device = global.getOpenDevices().iterator().next();
    try (var stack = MemoryStack.stackPush()) {
      var error = stack.mallocInt(1);
      long event = CL11.clCreateUserEvent(device.getContext(), error);
      require(error.get(0) == CL10.CL_SUCCESS && event != 0, "Native user event creation failed");
      var callback = new CountDownLatch(1);
      var status = new AtomicInteger(Integer.MAX_VALUE);
      try {
        device
            .getEventCallbackManager()
            .registerCallback(
                event,
                CL10.CL_COMPLETE,
                (handle, result, data) -> {
                  status.set(result);
                  callback.countDown();
                });
        long wait = LivenessProbe.beginAwait("controlled native-event regression");
        try {
          TimeUnit.MILLISECONDS.sleep(2200);
          require(
              OpenClQueueProbe.sample().contains("SUBMITTED"), "Pending GPU event not observed");
        } finally {
          LivenessProbe.endAwait(wait);
        }
        require(
            CL11.clSetUserEventStatus(event, CL10.CL_COMPLETE) == CL10.CL_SUCCESS,
            "Native event completion failed");
        require(callback.await(2, TimeUnit.SECONDS), "Provider callback was lost");
        require(status.get() == CL10.CL_COMPLETE, "Provider callback status changed");
        for (int attempt = 0;
            attempt < 40 && !OpenClQueueProbe.sample().contains("retained=0");
            attempt++) TimeUnit.MILLISECONDS.sleep(50);
        require(
            OpenClQueueProbe.sample().contains("retained=0"), "Diagnostic references not drained");
        var count = stack.mallocInt(1);
        require(
            CL10.clGetEventInfo(event, CL10.CL_EVENT_REFERENCE_COUNT, count, null)
                == CL10.CL_SUCCESS,
            "Owner native event was released by diagnostics");
        require(count.get(0) >= 1, "Owner native event reference missing");
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Diagnostic event test interrupted", failure);
      } finally {
        var state = stack.mallocInt(1);
        if (CL10.clGetEventInfo(event, CL10.CL_EVENT_COMMAND_EXECUTION_STATUS, state, null)
                == CL10.CL_SUCCESS
            && state.get(0) > CL10.CL_COMPLETE) CL11.clSetUserEventStatus(event, CL10.CL_COMPLETE);
        OpenClQueueProbe.close();
        CL10.clReleaseEvent(event);
      }
    }
    LogUtils.getLogger()
        .info("OpenCL diagnostic regression passed: pending state, callback and reference cleanup");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
