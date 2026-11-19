package dev.aeroscapes.magic.travel;

public final class TravelMagicPolicyTest {
  private TravelMagicPolicyTest() {}

  public static void main(String[] args) {
    for (int level = 1; level <= 5; level++) {
      require(TravelMagicPolicy.wingsDuration(level * 10) == level * 200, "Native wing ranks");
      require(TravelMagicPolicy.wingsDuration(level * 10_000) == 1000, "Boosted wings cap");
    }
    require(TravelMagicPolicy.wingsDuration(Float.POSITIVE_INFINITY) == 1000, "Huge power cap");
    require(TravelMagicPolicy.wingsDuration(Float.NaN) == 0, "Invalid wing power");
    require(TravelMagicPolicy.wingsDuration(15.75F) == 300, "Native whole-second duration");
    for (int level = 1; level <= 3; level++) {
      require(TravelMagicPolicy.portalDuration(level, 1) == level * 400, "Distinct portal ranks");
      require(TravelMagicPolicy.portalDuration(level, 100) == level * 400, "Rank power ceiling");
      require(TravelMagicPolicy.portalDuration(level, 0.5F) == level * 200, "Power debuff");
    }
    require(
        TravelMagicPolicy.portalDuration(100, Float.POSITIVE_INFINITY) == 1200, "Overlevel cap");
    require(TravelMagicPolicy.portalDuration(3, Float.NaN) == 0, "Invalid portal power");
    require(TravelMagicPolicy.portalLinkAllowed(true, 128 * 128), "Exact endpoint distance");
    require(
        !TravelMagicPolicy.portalLinkAllowed(true, 128 * 128 + 0.001), "Outside endpoint distance");
    require(!TravelMagicPolicy.portalLinkAllowed(false, 0), "Cross dimension");
    require(!TravelMagicPolicy.portalLinkAllowed(true, Double.NaN), "Invalid endpoint distance");
    long now = (long) Integer.MAX_VALUE + 1_000_000;
    require(TravelMagicPolicy.remainingTicks(now, now + 2400) == 2400, "Long tick deadlines");
    require(TravelMagicPolicy.remainingTicks(now, now) == 0, "Exact deadline boundary");
    require(TravelMagicPolicy.remainingTicks(now, now - 1) == 0, "Expired deadline");
    require(TravelMagicPolicy.remainingTicks(0, Long.MAX_VALUE) == Integer.MAX_VALUE, "HUD clamp");
    System.out.println("Travel magic policy: duration, power, distance and long deadlines passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
