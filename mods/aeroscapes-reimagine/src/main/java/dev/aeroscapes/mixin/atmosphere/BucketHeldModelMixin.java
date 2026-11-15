package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.atmosphere.BucketCreatures;
import dev.aeroscapes.atmosphere.BucketHeldRendering;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class BucketHeldModelMixin {
  @WrapOperation(
      method =
          "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/client/ClientHooks;handleCameraTransforms(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemDisplayContext;Z)Lnet/minecraft/client/resources/model/BakedModel;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private BakedModel aeroscapes$bucketVolume(
      PoseStack pose,
      BakedModel model,
      ItemDisplayContext context,
      boolean leftHand,
      Operation<BakedModel> original,
      @Local(argsOnly = true) ItemStack stack) {
    return original.call(
        pose, BucketHeldRendering.select(model, stack, context), context, leftHand);
  }

  @Inject(
      method =
          "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$bucketCreature(
      ItemStack stack,
      ItemDisplayContext context,
      boolean leftHand,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay,
      BakedModel model,
      CallbackInfo callback) {
    if (BucketHeldRendering.vessel(model))
      BucketCreatures.render(stack, pose, buffers, light, leftHand);
  }
}
