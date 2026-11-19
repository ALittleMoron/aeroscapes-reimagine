package dev.aeroscapes.magic.client;

/** Eight stable sectors, starting at twelve o'clock, with a neutral center. */
public final class AbilityWheelGeometry {
  private AbilityWheelGeometry() {}

  public static int sector(double x, double y) {
    if (x * x + y * y < 18 * 18) return -1;
    double angle = Math.atan2(x, -y) + Math.PI / 8;
    if (angle < 0) angle += Math.PI * 2;
    return ((int) Math.floor(angle / (Math.PI / 4))) % 8;
  }
}
