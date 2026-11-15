package dev.aeroscapes.atmosphere;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PresentationConfig {
  public static final ModConfigSpec SPEC;
  public static final ModConfigSpec.BooleanValue CELESTIAL_SKY;
  public static final ModConfigSpec.BooleanValue ITEM_MODELS_3D;

  static {
    var builder = new ModConfigSpec.Builder();
    CELESTIAL_SKY =
        builder
            .comment("Show decorative planets, accent stars and the fixed northern guide star.")
            .define("celestialSky", true);
    ITEM_MODELS_3D =
        builder
            .comment("Use the pack's 3D item models. Changing this reloads client resources.")
            .define("itemModels3d", true);
    SPEC = builder.build();
  }

  private PresentationConfig() {}
}
