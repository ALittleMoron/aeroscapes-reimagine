package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.CelestialRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class CelestialSkyMixin {
  @Shadow private ClientLevel level;

  @Inject(
      method = "renderSky",
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableBlend()V"),
      require = 1,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes the checked blended-sky boundary.
  private void aeroscapes$celestials(
      Matrix4f modelView,
      Matrix4f projection,
      float partialTick,
      Camera camera,
      boolean foggy,
      Runnable setupFog,
      CallbackInfo callback) {
    CelestialRenderer.render(level, modelView, partialTick, camera);
  }
}
