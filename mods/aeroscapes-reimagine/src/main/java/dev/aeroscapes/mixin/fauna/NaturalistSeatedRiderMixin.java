package dev.aeroscapes.mixin.fauna;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
import traben.entity_texture_features.features.state.ETFState;
import traben.entity_texture_features.utils.ETFEntity;

@Mixin(targets = "com.crispytwig.naturalist.client.renderer.entity.layers.SeatedRiderLayer")
public abstract class NaturalistSeatedRiderMixin {
  @WrapOperation(
      method =
          "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Mob;FFFFFF)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Naturalist bypasses the dispatcher that mounts ETF state.
  private static void aeroscapes$riderState(
      EntityRenderer<?> renderer,
      Entity rider,
      float yaw,
      float partialTick,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      Operation<Void> original) {
    boolean layer = EMFState.isLayerPhase;
    boolean main = EMFState.isMainPhase;
    boolean features = ETFState.isRenderingFeatures;
    EMFState.isLayerPhase = false;
    EMFState.isMainPhase = true;
    ETFState.isRenderingFeatures = false;
    try {
      ETFState.mount(ETFEntityRenderState.forEntity((ETFEntity) rider));
      try {
        original.call(renderer, rider, yaw, partialTick, pose, buffers, light);
      } finally {
        ETFState.unMount();
      }
    } finally {
      EMFState.isLayerPhase = layer;
      EMFState.isMainPhase = main;
      ETFState.isRenderingFeatures = features;
    }
  }
}
