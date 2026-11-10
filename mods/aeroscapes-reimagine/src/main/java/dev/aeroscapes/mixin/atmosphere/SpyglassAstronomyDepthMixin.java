package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.aeroscapes.atmosphere.AstronomySkyDepth;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.nettakrim.spyglass_astronomy.SpaceRenderingManager", remap = false)
abstract class SpyglassAstronomyDepthMixin {
  @WrapOperation(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"),
      require = 4,
      allow = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$maskLateSky(
      VertexBuffer buffer,
      Matrix4f modelView,
      Matrix4f projection,
      ShaderInstance originalShader,
      Operation<Void> draw) {
    var shader = AstronomySkyDepth.prepare();
    if (shader == null) {
      draw.call(buffer, modelView, projection, originalShader);
      return;
    }
    // Iris leaves the main target bound after composing color. Its depth still holds near terrain.
    var previousState = new GlStateBackup();
    RenderSystem.backupGlState(previousState);
    try {
      RenderSystem.enableDepthTest();
      RenderSystem.depthFunc(GL11.GL_LEQUAL);
      RenderSystem.depthMask(false);
      draw.call(buffer, modelView, projection, shader);
    } finally {
      RenderSystem.restoreGlState(previousState);
    }
  }
}
