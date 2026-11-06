package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.movement.HookLineAttachment;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandRenderer.class)
public abstract class HookItemOwnerMixin {
  @WrapMethod(
      method =
          "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Includes the third-person ItemInHandLayer.
  private void aeroscapes$heldOwner(
      LivingEntity owner,
      ItemStack stack,
      ItemDisplayContext context,
      boolean left,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      Operation<Void> original) {
    LivingEntity previous = HookLineAttachment.owner(owner);
    try {
      original.call(owner, stack, context, left, pose, buffers, light);
    } finally {
      HookLineAttachment.owner(previous);
    }
  }
}
