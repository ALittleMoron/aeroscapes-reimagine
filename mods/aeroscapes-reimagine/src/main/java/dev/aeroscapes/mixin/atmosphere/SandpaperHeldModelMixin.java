package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.HeldEquipmentRendering;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(
    targets = "com.simibubi.create.content.equipment.sandPaper.SandPaperItemRenderer",
    remap = false)
public abstract class SandpaperHeldModelMixin {
  @ModifyArg(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/ItemRenderer;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"),
      index = 7)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private BakedModel aeroscapes$heldBody(
      BakedModel original, @Local(argsOnly = true) ItemDisplayContext context) {
    return HeldEquipmentRendering.sandpaper(context, original);
  }
}
