package dev.aeroscapes.atmosphere;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Anchors Illumina's three-dimensional handle to Punchy's existing pendulum pivot. */
public final class LanternGrip {
  private LanternGrip() {}

  public static Vector3f translation(
      Matrix4fc display, boolean left, float offsetX, float offsetY, float offsetZ) {
    // Vanilla template_lantern's handle centre is (8, 10, 8), after the mesh's -0.5 shift.
    Vector3f handle = display.transformPosition(new Vector3f(0, 0.125f, 0));
    return new Vector3f(left ? 0.04f : -0.04f, -0.14f, 0.11f)
        .sub(handle)
        .add(offsetX, offsetY + 0.49999976f, offsetZ - 0.06999993f);
  }
}
