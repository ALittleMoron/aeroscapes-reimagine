package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.atmosphere.RodTrace;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import punchy.client.render.FishingLineAttachment;

@Mixin(value = FishingLineAttachment.class, remap = false)
public abstract class RodCaptureTraceMixin {
  @Inject(method = "beginWorld", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the declared Mixin injection.
  private static void aeroscapes$frame(
      Camera camera, Matrix4f view, Matrix4f projection, CallbackInfo callback) {
    RodTrace.beginWorld();
  }

  @WrapMethod(method = "capture")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the declared Mixin injection.
  private static void aeroscapes$model(
      BakedModel model, ItemStack stack, Matrix4f pose, Operation<Void> original) {
    if (!RodTrace.active()
        || !punchy.client.render.PunchyArmRenderer.isCapturingItemRenderer()
        || !stack.is(net.minecraft.world.item.Items.FISHING_ROD)
        || net.minecraft.client.Minecraft.getInstance().player.fishing == null) {
      original.call(model, stack, pose);
      return;
    }
    String previous = RodTrace.beginModel(model);
    try {
      original.call(model, stack, pose);
    } finally {
      RodTrace.endModel(previous);
    }
  }

  @Inject(method = "captureModelTip", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the declared Mixin injection.
  private static void aeroscapes$tip(
      LocalPlayer player, HumanoidArm arm, Matrix4f pose, Vector3f tip, CallbackInfo callback) {
    RodTrace.tip(player, arm, pose, tip);
  }
}
