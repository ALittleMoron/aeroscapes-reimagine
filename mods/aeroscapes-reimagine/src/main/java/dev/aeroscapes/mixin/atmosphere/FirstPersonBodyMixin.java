package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aeroscapes.atmosphere.FirstPersonBody;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
abstract class FirstPersonBodyMixin {
  @Unique
  private static final String AEROSCAPES_RENDER =
      "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

  @WrapOperation(
      method = AEROSCAPES_RENDER,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Preserve the native setup and its animation hooks.
  private void aeroscapes$drawCameraBody(
      EntityModel<?> model,
      PoseStack poses,
      VertexConsumer skin,
      int light,
      int overlay,
      int color,
      Operation<Void> original,
      @Local(argsOnly = true) LivingEntity entity) {
    if (FirstPersonBody.owns(this, entity, poses) && model instanceof PlayerModel<?> playerModel) {
      FirstPersonBody.renderBody(playerModel, skin, light, overlay, color);
      return;
    }
    original.call(model, poses, skin, light, overlay, color);
  }

  @WrapOperation(
      method = AEROSCAPES_RENDER,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Only the torso and leg armor is in the camera pass.
  private void aeroscapes$drawCameraArmor(
      RenderLayer<?, ?> layer,
      PoseStack poses,
      MultiBufferSource buffers,
      int light,
      Entity entity,
      float phase,
      float speed,
      float partial,
      float age,
      float headYaw,
      float headPitch,
      Operation<Void> original) {
    if (FirstPersonBody.owns(this, entity, poses)) {
      if (layer instanceof HumanoidArmorLayer<?, ?, ?>
          && entity instanceof AbstractClientPlayer player)
        FirstPersonBody.renderArmor(
            buffers, light, player, phase, speed, partial, age, headYaw, headPitch);
      return;
    }
    original.call(
        layer, poses, buffers, light, entity, phase, speed, partial, age, headYaw, headPitch);
  }

  @WrapOperation(
      method = AEROSCAPES_RENDER,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The base renderer draws the name tag.
  private void aeroscapes$skipCameraName(
      LivingEntityRenderer<?, ?> renderer,
      Entity entity,
      float yaw,
      float partial,
      PoseStack poses,
      MultiBufferSource buffers,
      int light,
      Operation<Void> original) {
    if (!FirstPersonBody.owns(this, entity, poses))
      original.call(renderer, entity, yaw, partial, poses, buffers, light);
  }
}
