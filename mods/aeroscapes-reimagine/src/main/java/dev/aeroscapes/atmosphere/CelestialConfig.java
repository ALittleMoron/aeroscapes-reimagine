package dev.aeroscapes.atmosphere;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CelestialConfig {
  public static final ModConfigSpec SPEC;
  public static final ModConfigSpec.BooleanValue CELESTIAL_SKY;

  static {
    var builder = new ModConfigSpec.Builder();
    CELESTIAL_SKY =
        builder
            .comment("Show decorative planets, accent stars and the fixed northern guide star.")
            .define("celestialSky", true);
    SPEC = builder.build();
  }

  private CelestialConfig() {}
}
