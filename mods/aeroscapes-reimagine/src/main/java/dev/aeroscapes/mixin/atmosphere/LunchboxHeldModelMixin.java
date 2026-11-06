package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.HeldEquipmentRendering;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "com.dannyandson.nutritionalbalance.lunchbox.LunchBoxItemRenderer", remap = false)
public abstract class LunchboxHeldModelMixin {
  @WrapOperation(
      method = "renderByItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/dannyandson/nutritionalbalance/lunchbox/LunchBoxItemRenderer;getBaseModel()Lnet/minecraft/client/resources/model/BakedModel;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private BakedModel aeroscapes$heldModel(
      @org.spongepowered.asm.mixin.injection.Coerce Object renderer,
      Operation<BakedModel> original,
      @Local(argsOnly = true) ItemDisplayContext context) {
    return HeldEquipmentRendering.lunchbox(context, original.call(renderer));
  }

  @ModifyArg(
      method = "renderByItem",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"),
      index = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private double aeroscapes$foodSurface(
      double z, @Local(argsOnly = true) ItemDisplayContext context) {
    if (!HeldEquipmentRendering.held(context)) return z;
    if (z == 0.55) return 0.75;
    if (z == 0.45) return 0.25;
    return z;
  }
}
