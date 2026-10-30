package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aeroscapes.atmosphere.RodTrace;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingHookRenderer.class)
public abstract class RodLineTraceMixin {
  @WrapOperation(
      method =
          "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;stringVertex(FFFLcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FF)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the declared Mixin injection.
  private static void aeroscapes$endpoint(
      float x,
      float y,
      float z,
      VertexConsumer consumer,
      PoseStack.Pose pose,
      float fraction,
      float next,
      Operation<Void> original,
      @Local(argsOnly = true) FishingHook hook) {
    original.call(x, y, z, consumer, pose, fraction, next);
    if (fraction == 1) RodTrace.endpoint(hook, x, y, z, pose);
  }
}
