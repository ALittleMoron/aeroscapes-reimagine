package dev.aeroscapes.movement;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import punchy.client.render.FishingLineProjection;

/** Project the attachment on the owned handle after the selected item transforms. */
public final class HookHandleProjection {
  private HookHandleProjection() {}

  public static Vector3f project(Matrix4f modelView, Matrix4f projection) {
    return FishingLineProjection.projectTip(
        modelView, projection, new Vector3f(8 / 16f, 9.8f / 16f, 8 / 16f));
  }

  public static Vector3f worldOffset(Matrix4f worldView, Matrix4f itemView) {
    return new Matrix4f(worldView)
        .invert()
        .mul(itemView)
        .transformPosition(new Vector3f(8 / 16f, 9.8f / 16f, 8 / 16f));
  }
}
