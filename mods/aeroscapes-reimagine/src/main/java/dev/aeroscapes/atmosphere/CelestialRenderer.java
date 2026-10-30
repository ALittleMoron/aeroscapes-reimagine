package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;

/** Drawn inside vanilla sky, while Iris can still select its textured-sky program. */
public final class CelestialRenderer {
  private static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath(
          "aeroscapes_reimagine", "textures/environment/celestials.png");

  private CelestialRenderer() {}

  public static void render(
      ClientLevel level, Matrix4f modelView, float partialTick, Camera camera) {
    if (!CelestialConfig.CELESTIAL_SKY.get()
        || level == null
        || !level.dimension().equals(Level.OVERWORLD)
        || camera.getFluidInCamera() != FogType.NONE) return;
    float brightness = level.getStarBrightness(partialTick);
    if (brightness <= 0) return;
    double skyAngle = level.getTimeOfDay(partialTick);
    float rain = level.getRainLevel(partialTick);
    float thunder = level.getThunderLevel(partialTick);
    ShaderInstance previousShader = RenderSystem.getShader();
    int previousTexture = RenderSystem.getShaderTexture(0);
    BufferBuilder buffer =
        Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    int vertices = 0;
    for (var body : CelestialLayout.BODIES) {
      var direction = CelestialLayout.direction(body, skyAngle, level.getDayTime(), partialTick);
      float opacity = CelestialLayout.opacity(direction, skyAngle, brightness, rain, thunder);
      if (opacity < 1.0F / 255) continue;
      emit(buffer, modelView, body, direction, (int) (opacity * 255));
      vertices += 4;
    }
    var mesh = buffer.build();
    if (vertices == 0 || mesh == null) return;
    try {
      RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
      RenderSystem.setShaderTexture(0, TEXTURE);
      BufferUploader.drawWithShader(mesh);
    } finally {
      RenderSystem.setShader(() -> previousShader);
      RenderSystem.setShaderTexture(0, previousTexture);
    }
  }

  private static void emit(
      BufferBuilder buffer,
      Matrix4f modelView,
      CelestialLayout.Body body,
      CelestialLayout.Direction direction,
      int alpha) {
    double horizontal = Math.hypot(direction.x(), direction.z());
    double rightX = horizontal > 1.0E-6 ? -direction.z() / horizontal : 1;
    double rightZ = horizontal > 1.0E-6 ? direction.x() / horizontal : 0;
    double upX = -rightZ * direction.y();
    double upY = rightZ * direction.x() - rightX * direction.z();
    double upZ = rightX * direction.y();
    float left = body.sprite() / 4.0F;
    float right = left + 0.25F;
    for (int corner = 0; corner < 4; corner++) {
      float side = corner == 0 || corner == 3 ? -body.halfSize() : body.halfSize();
      float height = corner < 2 ? -body.halfSize() : body.halfSize();
      buffer
          .addVertex(
              modelView,
              (float) (direction.x() * 100 + rightX * side + upX * height),
              (float) (direction.y() * 100 + upY * height),
              (float) (direction.z() * 100 + rightZ * side + upZ * height))
          .setUv(side < 0 ? left : right, corner < 2 ? 1 : 0)
          .setColor(255, 255, 255, alpha);
    }
  }
}
