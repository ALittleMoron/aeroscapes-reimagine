package dev.aeroscapes.movement;

/** The glider's empty reserve must not cancel movement whose stamina cost is disabled. */
public final class ParagliderExhaustion {
  private ParagliderExhaustion() {}

  public static boolean shouldStopMovement(
      boolean runningConsumesStamina, boolean paragliding, boolean wasParagliding) {
    return runningConsumesStamina || paragliding || wasParagliding;
  }
}
