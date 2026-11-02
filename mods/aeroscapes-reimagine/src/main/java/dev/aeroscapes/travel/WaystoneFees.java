package dev.aeroscapes.travel;

/** Fixed experience points; horizontal distance uses the stones' visible world positions. */
public final class WaystoneFees {
  private WaystoneFees() {}

  public static int distanceSteps(double horizontalDistance) {
    if (!Double.isFinite(horizontalDistance) || horizontalDistance < 0) {
      throw new IllegalArgumentException("Invalid waystone distance");
    }
    return (int) Math.min(13, Math.ceil(horizontalDistance / 1000));
  }

  public static int points(boolean dimensional, double horizontalDistance) {
    return dimensional ? 360 : Math.min(360, 100 + 20 * distanceSteps(horizontalDistance));
  }

  public static long cumulativePoints(int level) {
    long value = level;
    return value <= 16
        ? value * value + 6 * value
        : value <= 31
            ? (5 * value * value - 81 * value + 720) / 2
            : (9 * value * value - 325 * value + 4440) / 2;
  }

  public static int levelAtPoints(long points, int maximumLevel) {
    int lower = 0;
    int upper = maximumLevel;
    while (lower < upper) {
      int middle = (int) (((long) lower + upper + 1) / 2);
      if (cumulativePoints(middle) <= points) lower = middle;
      else upper = middle - 1;
    }
    return lower;
  }
}
