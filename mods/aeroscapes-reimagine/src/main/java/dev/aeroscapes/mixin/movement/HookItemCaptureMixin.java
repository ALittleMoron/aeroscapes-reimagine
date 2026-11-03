package dev.aeroscapes.mixin.movement;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.movement.HookLineAttachment;
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
public abstract class HookItemCaptureMixin {
  @Inject(
      method =
          "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"),
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Also precedes Flerovium's empty-model cancellation.
  private void aeroscapes$captureHandle(
      ItemStack stack,
      ItemDisplayContext context,
      boolean leftHand,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay,
      BakedModel model,
      CallbackInfo callback) {
    HookLineAttachment.capture(stack, context, leftHand, pose);
  }
}
