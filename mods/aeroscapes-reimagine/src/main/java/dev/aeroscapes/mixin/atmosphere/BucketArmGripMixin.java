package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.atmosphere.BucketHeldRendering;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import punchy.client.render.PunchyArmRenderer;
import punchy.client.renderer.layer.ArmToolTuning;
import punchy.client.renderer.layer.PlayerArmModelLayer.ToolKind;

@Mixin(value = PunchyArmRenderer.class, remap = false)
public abstract class BucketArmGripMixin {
  @WrapOperation(
      method =
          "applyArmOffsets(Lorg/joml/Matrix4f;Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/entity/HumanoidArm;F)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lpunchy/client/renderer/layer/ArmToolTuning;hasSpecificProfile(Lnet/minecraft/world/item/ItemStack;Z)Z"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static boolean aeroscapes$bucketCarryOverride(
      ItemStack stack, boolean left, Operation<Boolean> original) {
    return original.call(stack, left) || BucketHeldRendering.needsVolume(stack);
  }

  @WrapOperation(
      method =
          "applyArmOffsets(Lorg/joml/Matrix4f;Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/entity/HumanoidArm;F)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lpunchy/client/renderer/layer/ArmToolTuning;resolveProfile(Lnet/minecraft/world/item/ItemStack;Lpunchy/client/renderer/layer/PlayerArmModelLayer$ToolKind;Z)Lpunchy/client/renderer/layer/ArmToolTuning$Profile;"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static ArmToolTuning.Profile aeroscapes$bucketCarryProfile(
      ItemStack stack, ToolKind kind, boolean left, Operation<ArmToolTuning.Profile> original) {
    var profile = original.call(stack, kind, left);
    return !ArmToolTuning.hasSpecificProfile(stack, left) && BucketHeldRendering.needsVolume(stack)
        ? BucketHeldRendering.armProfile(profile)
        : profile;
  }
}
