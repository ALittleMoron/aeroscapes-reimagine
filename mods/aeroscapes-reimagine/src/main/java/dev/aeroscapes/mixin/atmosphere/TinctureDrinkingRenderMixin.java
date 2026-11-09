package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.atmosphere.TinctureDrinkingPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntityRenderer.class)
abstract class TinctureDrinkingRenderMixin {
  @WrapMethod(
      method =
          "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Renderer-local lifetime includes the lazy EMF pass.
  private void aeroscapes$drinkingFrame(
      LivingEntity entity,
      float yaw,
      float delta,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      Operation<Void> original) {
    if (entity instanceof AbstractClientPlayer player && TinctureDrinkingPose.active(player)) {
      TinctureDrinkingPose.render(
          player, () -> original.call(entity, yaw, delta, pose, buffers, light));
    } else {
      original.call(entity, yaw, delta, pose, buffers, light);
    }
  }
}
