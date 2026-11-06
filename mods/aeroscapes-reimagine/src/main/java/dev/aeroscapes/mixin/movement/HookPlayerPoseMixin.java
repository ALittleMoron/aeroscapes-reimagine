package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.movement.HookHangingPose;
import dev.aeroscapes.movement.HookPoseSnapshot;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class HookPlayerPoseMixin<T extends LivingEntity> {
  @Unique private final Deque<HookPoseSnapshot> aeroscapes$savedPoses = new ArrayDeque<>();

  @WrapOperation(
      method =
          "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"),
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Apply after native/NEA animation to this render only.
  private void aeroscapes$hangingBody(
      EntityModel<T> animatedModel,
      Entity entity,
      float limbSwing,
      float limbAmount,
      float age,
      float headYaw,
      float headPitch,
      Operation<Void> original) {
    original.call(animatedModel, entity, limbSwing, limbAmount, age, headYaw, headPitch);
    if (entity instanceof AbstractClientPlayer player
        && animatedModel instanceof PlayerModel<?> playerModel
        && HookHangingPose.hasPose(player, Math.clamp(age - player.tickCount, 0, 1))) {
      HookPoseSnapshot saved = aeroscapes$savedPoses.peek();
      if (saved == null) return;
      saved.apply(player, playerModel, Math.clamp(age - player.tickCount, 0, 1));
    }
  }

  @WrapMethod(
      method =
          "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Shared models must be restored even after a failed draw.
  private void aeroscapes$restoreBody(
      T entity,
      float yaw,
      float partialTick,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      Operation<Void> original) {
    if (!(entity instanceof AbstractClientPlayer player)
        || !HookHangingPose.hasPose(player, partialTick)) {
      original.call(entity, yaw, partialTick, pose, buffers, light);
      return;
    }
    HookPoseSnapshot saved = new HookPoseSnapshot();
    aeroscapes$savedPoses.push(saved);
    try {
      original.call(entity, yaw, partialTick, pose, buffers, light);
    } finally {
      saved.restore();
      aeroscapes$savedPoses.pop();
    }
  }
}
