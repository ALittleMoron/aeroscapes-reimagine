package dev.aeroscapes.camera;

import dev.aeroscapes.mixin.camera.CameraRotationAccess;
import net.minecraft.client.Camera;

public final class ShoulderCameraSway {
  private ShoulderCameraSway() {}

  public static void apply(Camera camera, float yaw, float pitch, float swayX, float swayY) {
    // Rebuilding an unchanged rotation would apply Sable's ship orientation twice.
    if (swayX == 0.0F && swayY == 0.0F) return;
    ((CameraRotationAccess) camera).aeroscapes$setRotation(yaw, pitch);
  }
}
