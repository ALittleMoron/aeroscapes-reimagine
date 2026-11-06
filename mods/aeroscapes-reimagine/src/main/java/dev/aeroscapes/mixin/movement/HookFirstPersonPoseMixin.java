package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.movement.HookHangingPose;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import punchy.client.render.PunchyArmRenderer;

@Mixin(value = PunchyArmRenderer.class, remap = false)
public abstract class HookFirstPersonPoseMixin {
  @WrapOperation(
      method = "applyGeoArmToPlayer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lpunchy/client/render/PunchyArmRenderer;cacheArmWorld(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/entity/HumanoidArm;Lorg/joml/Matrix4f;)V"),
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Both the arm and translateToHand consume this pose.
  private static void aeroscapes$hangingArm(
      LocalPlayer player,
      HumanoidArm arm,
      Matrix4f matrix,
      Operation<Void> original,
      @Local(argsOnly = true) float partialTick) {
    HookHangingPose.firstPerson(player, arm, matrix, partialTick);
    original.call(player, arm, matrix);
  }
}
