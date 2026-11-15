package dev.aeroscapes.preflight;

public final class PreflightReadings {
  public enum Assessment {
    INCOMPLETE,
    LEAKING,
    NO_BALLOONS,
    BELOW_MASS,
    COVERS_MASS
  }

  private PreflightReadings() {}

  public static double supportMass(double nativeLift, double pressure) {
    return nativeLift * pressure;
  }

  public static Assessment assessment(
      double mass,
      double support,
      int balloons,
      boolean leaking,
      boolean partial,
      boolean consistentGravity) {
    if (partial
        || !consistentGravity
        || !Double.isFinite(mass)
        || !Double.isFinite(support)
        || mass <= 0
        || support < 0) return Assessment.INCOMPLETE;
    if (leaking) return Assessment.LEAKING;
    if (balloons == 0) return Assessment.NO_BALLOONS;
    return support < mass ? Assessment.BELOW_MASS : Assessment.COVERS_MASS;
  }
}
