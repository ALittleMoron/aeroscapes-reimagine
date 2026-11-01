package dev.aeroscapes.diagnostics;

/** Active-play wall-clock intervals; reporting preserves the preceding frame timestamp. */
final class FrameIntervalStats {
  record Summary(
      long intervals,
      double measuredMs,
      double meanMs,
      double maximumMs,
      long maximumEndNanos,
      long over50Ms,
      long over100Ms) {}

  private boolean hasPrevious;
  private long previous;
  private long intervals;
  private long total;
  private long maximum;
  private long maximumEnd;
  private long over50;
  private long over100;

  void frame(long now, boolean active) {
    if (!active) {
      hasPrevious = false;
      return;
    }
    if (hasPrevious) {
      long elapsed = now - previous;
      intervals++;
      total += elapsed;
      if (elapsed > maximum) {
        maximum = elapsed;
        maximumEnd = now;
      }
      if (elapsed > 50_000_000L) over50++;
      if (elapsed > 100_000_000L) over100++;
    }
    previous = now;
    hasPrevious = true;
  }

  Summary take() {
    Summary result =
        new Summary(
            intervals,
            total / 1_000_000.0,
            intervals == 0 ? 0 : total / (1_000_000.0 * intervals),
            maximum / 1_000_000.0,
            maximumEnd,
            over50,
            over100);
    clearWindow();
    return result;
  }

  void reset() {
    hasPrevious = false;
    clearWindow();
  }

  private void clearWindow() {
    intervals = 0;
    total = 0;
    maximum = 0;
    maximumEnd = 0;
    over50 = 0;
    over100 = 0;
  }
}
