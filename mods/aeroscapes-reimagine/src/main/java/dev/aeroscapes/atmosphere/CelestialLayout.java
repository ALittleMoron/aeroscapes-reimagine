package dev.aeroscapes.atmosphere;

import java.util.List;

/** Decorative sky directions; no world seed or client state is retained. */
public final class CelestialLayout {
  public record Body(
      int sprite,
      double azimuth,
      double altitude,
      float halfSize,
      int driftDays,
      boolean fixedNorth) {}

  public record Direction(double x, double y, double z) {}

  public static final List<Body> BODIES =
      List.of(
          new Body(0, 60, 36, 0.95F, 37, false),
          new Body(1, 173, 52, 1.05F, 61, false),
          new Body(2, 286, 26, 0.85F, 97, false),
          new Body(3, 35, 28, 0.58F, 0, false),
          new Body(3, 92, 56, 0.66F, 0, false),
          new Body(3, 148, 24, 0.52F, 0, false),
          new Body(3, 205, 61, 0.62F, 0, false),
          new Body(3, 259, 34, 0.55F, 0, false),
          new Body(3, 314, 68, 0.60F, 0, false),
          new Body(3, 0, 42, 0.85F, 0, true));

  private CelestialLayout() {}

  public static Direction direction(Body body, double skyAngle, long dayTime, double partialTick) {
    double azimuth = Math.toRadians(body.azimuth());
    double altitude = Math.toRadians(body.altitude());
    double x = Math.sin(azimuth) * Math.cos(altitude);
    double y = Math.sin(altitude);
    double z = -Math.cos(azimuth) * Math.cos(altitude);
    if (body.fixedNorth()) return new Direction(x, y, z);
    double drift = 0;
    if (body.driftDays() > 0) {
      long period = body.driftDays() * 24000L;
      drift = (Math.floorMod(dayTime, period) + partialTick) / period;
    }
    // Vanilla's Y(-90) * X(time) sky transform rotates around world +Z.
    double angle = (skyAngle - 0.5 + drift) * Math.TAU;
    double cosine = Math.cos(angle);
    double sine = Math.sin(angle);
    return new Direction(x * cosine - y * sine, x * sine + y * cosine, z);
  }

  public static float opacity(
      Direction direction, double skyAngle, float starBrightness, float rain, float thunder) {
    double night = clamp(starBrightness * 2);
    double weather = 1 - Math.max(clamp(rain), clamp(thunder));
    double horizon = smooth(direction.y() / 0.12);
    double sunAngle = skyAngle * Math.TAU;
    double sunDot = -direction.x() * Math.sin(sunAngle) + direction.y() * Math.cos(sunAngle);
    double sun = discFade(sunDot, 20, 25);
    double moon = discFade(-sunDot, 14, 19);
    return (float) (night * weather * horizon * sun * moon);
  }

  private static double discFade(double cosine, double innerDegrees, double outerDegrees) {
    double inner = Math.cos(Math.toRadians(innerDegrees));
    double outer = Math.cos(Math.toRadians(outerDegrees));
    return smooth((inner - cosine) / (inner - outer));
  }

  private static double smooth(double value) {
    double bounded = clamp(value);
    return bounded * bounded * (3 - 2 * bounded);
  }

  private static double clamp(double value) {
    return Math.clamp(value, 0, 1);
  }
}
