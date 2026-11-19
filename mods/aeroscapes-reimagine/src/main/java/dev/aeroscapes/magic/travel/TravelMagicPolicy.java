package dev.aeroscapes.magic.travel;

public final class TravelMagicPolicy {
  public static final int WINGS_DURATION_TICKS = 50 * 20;
  public static final int WINGS_COOLDOWN_TICKS = 120 * 20;
  public static final int PORTAL_DURATION_TICKS = 60 * 20;
  public static final int PORTAL_COOLDOWN_TICKS = 180 * 20;
  public static final int PORTAL_DISTANCE = 128;

  private TravelMagicPolicy() {}

  public static int wingsDuration(float power) {
    if (Float.isNaN(power) || power <= 0) return 0;
    return (int) Math.min(power, 50) * 20;
  }

  public static int portalDuration(int level, float powerMultiplier) {
    if (Float.isNaN(powerMultiplier) || powerMultiplier <= 0) return 0;
    // Each rank retains its own duration ceiling, including with power equipment.
    return (int) (Math.clamp(level, 1, 3) * 20 * 20 * Math.min(powerMultiplier, 1));
  }

  public static boolean portalLinkAllowed(boolean sameDimension, double distanceSquared) {
    return sameDimension
        && Double.isFinite(distanceSquared)
        && distanceSquared >= 0
        && distanceSquared <= PORTAL_DISTANCE * PORTAL_DISTANCE;
  }

  public static int remainingTicks(long now, long readyAt) {
    if (readyAt <= now) return 0;
    long remaining = readyAt - now;
    return remaining < 0 || remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
  }
}
