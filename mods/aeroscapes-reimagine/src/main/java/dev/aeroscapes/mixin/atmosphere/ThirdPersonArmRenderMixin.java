package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.atmosphere.ShortBladeAttackPresentation;
import dev.aeroscapes.atmosphere.ThirdPersonArmPose;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
abstract class ThirdPersonArmRenderMixin<T extends LivingEntity> {
  @WrapOperation(
      method =
          "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // PlayerAnimator has applied its clip when setup ends.
  private void aeroscapes$attackArm(
      EntityModel<T> model,
      Entity entity,
      float limbSwing,
      float limbAmount,
      float age,
      float headYaw,
      float headPitch,
      Operation<Void> original) {
    if (entity instanceof AbstractClientPlayer player
        && model instanceof PlayerModel<?> playerModel
        && ThirdPersonArmPose.hasAttackPose(player)) {
      float previousSwing = model.attackTime;
      try {
        model.attackTime = 0;
        original.call(model, entity, limbSwing, limbAmount, age, headYaw, headPitch);
      } finally {
        model.attackTime = previousSwing;
      }
      ThirdPersonArmPose.usedNativePose(player, playerModel, true);
    } else {
      original.call(model, entity, limbSwing, limbAmount, age, headYaw, headPitch);
    }
  }

  @WrapMethod(
      method =
          "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Renderer-local lifetime includes the lazy EMF pass.
  private void aeroscapes$actionFrame(
      LivingEntity entity,
      float yaw,
      float delta,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      Operation<Void> original) {
    if (entity instanceof AbstractClientPlayer player) {
      float attackWeight = ShortBladeAttackPresentation.renderWeight(player, delta);
      var arm = attackWeight > 0 ? player.getMainArm() : ThirdPersonArmPose.drinkingArm(player);
      if (arm != null) {
        ThirdPersonArmPose.render(
            player,
            arm,
            attackWeight,
            delta,
            () -> original.call(entity, yaw, delta, pose, buffers, light));
        return;
      }
    }
    original.call(entity, yaw, delta, pose, buffers, light);
  }
}
