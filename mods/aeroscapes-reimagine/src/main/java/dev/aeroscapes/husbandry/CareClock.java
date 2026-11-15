package dev.aeroscapes.husbandry;

public final class CareClock {
  private CareClock() {}

  public static long elapsed(
      long previousElapsed,
      long previousDay,
      long currentDay,
      long gameTicks,
      boolean ownerOnline) {
    long retained = Math.max(0, previousElapsed);
    if (!ownerOnline || gameTicks < 0 || gameTicks > 1 || currentDay < previousDay) return retained;
    return retained + currentDay - previousDay;
  }
}
