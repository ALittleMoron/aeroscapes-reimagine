package dev.aeroscapes.diagnostics;

import java.nio.file.Files;
import net.neoforged.fml.loading.FMLPaths;

/** Five minutes of opt-in samples, with no retained world references or background tasks. */
final class ChunkFlowWindow {
  static final boolean ENABLED =
      Files.isRegularFile(FMLPaths.CONFIGDIR.get().resolve("aeroscapes-chunk-diagnostics.enabled"));
  private long nextSample;
  private int samples;

  boolean active() {
    return ENABLED && samples < 300;
  }

  boolean sample(long now) {
    if (!active() || now < nextSample) return false;
    nextSample = now + 1_000_000_000L;
    samples++;
    return true;
  }

  void reset() {
    nextSample = 0;
    samples = 0;
  }
}
