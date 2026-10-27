package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.regression.opencl.OpenClBatchEvidence;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;

/**
 * Requires real CPU generation after every GPU program fails, followed by normal server shutdown.
 */
public final class OpenClBuildFailureRegression {
  private static final AtomicInteger FAILURES = new AtomicInteger();

  private OpenClBuildFailureRegression() {}

  public static void recordFailure() {
    FAILURES.incrementAndGet();
  }

  static void verify(ServerLevel level) {
    if (FAILURES.get() == 0)
      throw new IllegalStateException("No forced OpenCL compilation failure");
    OpenClBatchEvidence.arm(level.getChunkSource().randomState());
    NoiseSnapshotRegression.verify(level);
    OpenClBatchEvidence.verifyUnusedAndLog();
    LogUtils.getLogger()
        .info("OpenCL build failure fallback passed: failed compilations={}", FAILURES.get());
  }
}
