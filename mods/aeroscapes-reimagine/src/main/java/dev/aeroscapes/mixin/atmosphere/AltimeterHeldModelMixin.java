package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.atmosphere.HeldEquipmentRendering;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets = "net.mehvahdjukaar.supplementaries.client.renderers.items.AltimeterItemRenderer",
    remap = false)
public abstract class AltimeterHeldModelMixin {
  @WrapOperation(
      method = "renderByItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/ItemRenderer;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$heldHousing(
      ItemRenderer renderer,
      ItemStack stack,
      ItemDisplayContext context,
      boolean leftHand,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay,
      BakedModel model,
      Operation<Void> original) {
    original.call(
        renderer,
        stack,
        context,
        leftHand,
        pose,
        buffers,
        light,
        overlay,
        HeldEquipmentRendering.altimeter(context, model));
  }
}
