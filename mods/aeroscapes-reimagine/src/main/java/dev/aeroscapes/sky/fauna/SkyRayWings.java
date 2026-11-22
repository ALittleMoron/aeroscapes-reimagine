package dev.aeroscapes.sky.fauna;

import net.minecraft.util.Mth;

public final class SkyRayWings {
  public static final float CALF_SCALE = 0.56F;
  public static final float CALF_WING_SPAN = 0.83F;
  public static final float CALF_TAIL_LENGTH = 0.72F;

  private SkyRayWings() {}

  static float beatRate(double speed) {
    return (float) (0.055 + 0.36 * Mth.clamp(speed, 0, 0.60));
  }

  /** Angles in radians. The outer fin reverses after the shoulder, even in a fast stroke. */
  public record Pose(float phase, float power, float bank, float pitch) {
    public float shoulder() {
      return -0.05F + power * 0.31F * stroke(phase);
    }

    public float middle() {
      return -0.025F + power * 0.36F * stroke(phase - 0.85F);
    }

    public float tip() {
      return 0.075F + power * 0.38F * stroke(phase - 1.65F);
    }

    private static float stroke(float angle) {
      return Mth.sin(angle) - 0.12F * Mth.sin(2 * angle);
    }
  }
}
