package dev.aeroscapes.atmosphere;

public final class PrecipitationPolicy {
  private PrecipitationPolicy() {}

  public static boolean isAirbornePrecipitation(String id, boolean sky) {
    return sky
        && ("rain".equals(id)
            || "heavy_rain".equals(id)
            || "snow".equals(id)
            || "heavy_snow".equals(id));
  }
}
