package dev.aeroscapes.regression.opencl;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.RandomState;

/** Counts successful native launches and consumed GPU block buffers for the measured world. */
public final class OpenClBatchEvidence {
  private static final AtomicLong LAUNCHES = new AtomicLong();
  private static final AtomicLong READBACKS = new AtomicLong();
  private static volatile RandomState tracked;

  private OpenClBatchEvidence() {}

  public static void arm(RandomState state) {
    LAUNCHES.set(0);
    READBACKS.set(0);
    tracked = state;
  }

  public static boolean launched(RandomState state) {
    if (state != tracked) return false;
    LAUNCHES.incrementAndGet();
    return true;
  }

  public static void consumed(RandomState state) {
    if (state == tracked) READBACKS.incrementAndGet();
  }

  public static void verifyAndLog() {
    long readbacks = READBACKS.get();
    long launches = LAUNCHES.get();
    if (launches <= 0 || readbacks <= 0 || readbacks > launches)
      throw new IllegalStateException(
          "GPU execution evidence missing: noise launches="
              + launches
              + ", readbacks="
              + readbacks);
    LogUtils.getLogger()
        .info(
            "OpenCL execution evidence passed: noise launches={}, readbacks={}",
            launches,
            readbacks);
    tracked = null;
  }

  public static void verifyUnusedAndLog() {
    if (LAUNCHES.get() != 0 || READBACKS.get() != 0)
      throw new IllegalStateException("CPU fallback unexpectedly used GPU batches");
    LogUtils.getLogger().info("OpenCL CPU fallback evidence passed: noise launches=0, readbacks=0");
    tracked = null;
  }
}
