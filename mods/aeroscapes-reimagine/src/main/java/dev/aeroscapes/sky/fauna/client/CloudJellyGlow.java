package dev.aeroscapes.sky.fauna.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aeroscapes.sky.fauna.CloudJelly;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy.Form;
import dev.aeroscapes.sky.fauna.CloudJellyMotion;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

final class CloudJellyGlow extends RenderLayer<CloudJelly, CloudJellyModel> {
  static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath(
          "aeroscapes_reimagine", "textures/entity/cloud_jelly_glow.png");
  private final EntityRenderDispatcher dispatcher;

  CloudJellyGlow(
      RenderLayerParent<CloudJelly, CloudJellyModel> parent, EntityRenderDispatcher dispatcher) {
    super(parent);
    this.dispatcher = dispatcher;
  }

  @Override
  public void render(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      CloudJelly jelly,
      float stride,
      float speed,
      float tick,
      float age,
      float yaw,
      float pitch) {
    if (!jelly.isAlive() || jelly.isInvisible()) return;
    draw(
        pose,
        buffers.getBuffer(RenderType.eyes(TEXTURE)),
        getParentModel().swim(),
        jelly.form(),
        jelly.isPulsing(),
        dispatcher.cameraOrientation());
  }

  static void draw(
      PoseStack pose,
      VertexConsumer vertex,
      CloudJellyMotion swim,
      Form form,
      boolean pulse,
      Quaternionf camera) {
    Vec3 center = center(swim);
    pose.pushPose();
    pose.translate(center.x, 1.501 - center.y, -center.z);
    // Undo the living renderer's inverted Y/Z axes after anchoring to the posed bell.
    pose.mulPose(Axis.XP.rotationDegrees(180));
    pose.mulPose(camera);
    pose.translate(0, 0, .14);
    var uv = sprite(form, pulse);
    float radius = radius(swim.phase());
    corner(pose.last(), vertex, -radius, -radius, uv.u0(), uv.v1());
    corner(pose.last(), vertex, radius, -radius, uv.u1(), uv.v1());
    corner(pose.last(), vertex, radius, radius, uv.u1(), uv.v0());
    corner(pose.last(), vertex, -radius, radius, uv.u0(), uv.v0());
    pose.popPose();
  }

  static Vec3 center(CloudJellyMotion swim) {
    double units = CloudJellyAnatomy.SIZE_UNITS;
    return swim.basis
        .y
        .scale(-.52 * swim.bellDepth() / units)
        .add(0, swim.shape().height() / units, 0);
  }

  static float radius(double phase) {
    return .85F + .075F * Mth.cos((float) ((phase - .23) * Math.TAU));
  }

  static Sprite sprite(Form form, boolean pulse) {
    if (pulse) return new Sprite(0, .5F, .25F, 1);
    float u = .25F * form.ordinal();
    return new Sprite(u, 0, u + .25F, .5F);
  }

  private static void corner(
      PoseStack.Pose pose, VertexConsumer vertex, float x, float y, float u, float v) {
    vertex
        .addVertex(pose, x, y, 0)
        .setColor(-1)
        .setUv(u, v)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(LightTexture.FULL_BRIGHT)
        .setNormal(pose, 0, 0, 1);
  }

  record Sprite(float u0, float v0, float u1, float v1) {}
}
