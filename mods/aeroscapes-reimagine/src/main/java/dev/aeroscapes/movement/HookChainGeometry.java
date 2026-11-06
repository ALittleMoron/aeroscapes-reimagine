package dev.aeroscapes.movement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Keeps native longitudinal placement while sizing and lighting the two crossed chain faces. */
public final class HookChainGeometry {
  private static final float WIDTH_SCALE = .25f;

  private HookChainGeometry() {}

  public static void vertex(
      VertexConsumer consumer,
      PoseStack.Pose pose,
      float x,
      float y,
      float z,
      float u,
      float v,
      int light) {
    consumer
        .addVertex(pose.pose(), x * WIDTH_SCALE, y, z * WIDTH_SCALE)
        .setColor(255, 255, 255, 255)
        .setUv(u, (v + 1) / WIDTH_SCALE - 1)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(light)
        .setNormal(pose, x != 0 ? 0 : 1, 0, x != 0 ? 1 : 0);
  }
}
