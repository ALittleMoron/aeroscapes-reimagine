package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import punchy.client.render.PunchyArmRenderer;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_texture_features.features.state.ETFState;

@Mixin(value = PunchyArmRenderer.class, remap = false)
public abstract class PunchyEmfMixin {
  @WrapOperation(
      method = "renderFirstPerson",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lpunchy/client/render/PunchyArmRenderer;renderFirstPersonInternal(Lnet/minecraft/client/renderer/ItemInHandRenderer;Lnet/minecraft/client/player/LocalPlayer;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Punchy 3.0's native bridge targets the retired EMF API.
  private static void aeroscapes$firstPersonState(
      ItemInHandRenderer renderer,
      LocalPlayer player,
      float partialTick,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      Operation<Void> original) {
    EMFEntityRenderState state = EMFEntityRenderState.manualPlayerState();
    if (state == null) {
      original.call(renderer, player, partialTick, pose, buffers, light);
      return;
    }
    state.setIsFirstPersonHand(true);
    ETFState.mount(state);
    try {
      original.call(renderer, player, partialTick, pose, buffers, light);
    } finally {
      ETFState.unMount();
    }
  }
}
