package dev.aeroscapes.diagnostics;

/** Verifies hitch accounting and excludes inactive gaps without starting Minecraft. */
public final class FrameIntervalStatsTest {
  private FrameIntervalStatsTest() {}

  public static void main(String[] arguments) {
    var stats = new FrameIntervalStats();
    stats.frame(0, true);
    require(stats.take().intervals() == 0, "First frame only establishes a timestamp");
    stats.frame(16_000_000L, true);
    stats.frame(36_000_000L, true);
    stats.frame(156_000_000L, true);
    var hitch = stats.take();
    require(hitch.intervals() == 3, "Three intervals");
    require(hitch.measuredMs() == 156 && hitch.meanMs() == 52, "Active interval duration");
    require(hitch.maximumMs() == 120, "Worst interval");
    require(hitch.maximumEndNanos() == 156_000_000L, "Worst interval clock anchor");
    require(hitch.over50Ms() == 1 && hitch.over100Ms() == 1, "Hitch counts overlap");
    stats.frame(172_000_000L, true);
    stats.frame(200_000_000L, false);
    stats.frame(10_000_000_000L, true);
    stats.frame(10_017_000_000L, true);
    var resumed = stats.take();
    require(resumed.intervals() == 2 && resumed.measuredMs() == 33, "Inactive gap excluded");
    require(resumed.maximumMs() == 17, "Reporting retains timestamp; resume replaces it");
    require(resumed.maximumEndNanos() == 10_017_000_000L, "Inactive gap has no clock anchor");
    stats.reset();
    stats.frame(0, true);
    stats.frame(50_000_000L, true);
    stats.frame(150_000_000L, true);
    var boundaries = stats.take();
    require(boundaries.over50Ms() == 1 && boundaries.over100Ms() == 0, "Strict thresholds");
    stats.frame(3_150_000_000L, true);
    require(stats.take().maximumMs() == 3000, "Long hitch survives a reporting boundary");
    stats.reset();
    var empty = stats.take();
    require(empty.intervals() == 0 && empty.meanMs() == 0, "Empty report is unavailable, not NaN");
    require(empty.maximumEndNanos() == 0, "Reset clears clock anchor");
    stats.frame(60_000_000_000L, true);
    require(stats.take().intervals() == 0, "Reset excludes world transition gap");
    System.out.println("Frame intervals: hitch, report boundary and inactive gap checks passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
