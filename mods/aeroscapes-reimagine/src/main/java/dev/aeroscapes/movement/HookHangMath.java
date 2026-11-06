package dev.aeroscapes.movement;

import net.minecraft.world.item.UseAnim;

/** Visual reach toward an overhead attachment, including slack and creative hovering. */
public final class HookHangMath {
  private HookHangMath() {}

  public static boolean twoHandedUse(UseAnim animation) {
    return switch (animation) {
      case BOW, CROSSBOW, SPEAR, SPYGLASS, CUSTOM -> true;
      default -> false;
    };
  }

  public static boolean suspended(double distance, double ropeLength, double rise) {
    return Double.isFinite(distance)
        && Double.isFinite(ropeLength)
        && Double.isFinite(rise)
        && ropeLength > 0
        && distance >= rise
        && rise > 0.1;
  }

  public static float approach(float current, boolean hanging, int elapsedTicks) {
    float change = Math.min(1, Math.max(0, elapsedTicks) * 0.25f);
    return hanging ? Math.min(1, current + change) : Math.max(0, current - change);
  }
}
