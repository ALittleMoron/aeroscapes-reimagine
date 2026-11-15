package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.atmosphere.CopperBucketHeldRendering;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    targets =
        "com.yision.fluidlogistics.content.fluids.copperBucket.client.CopperBucketModel$Overrides",
    remap = false)
public abstract class CopperBucketHeldModelMixin {
  @ModifyExpressionValue(
      method =
          "createReplacement(Lnet/minecraft/world/item/Item;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/LivingEntity;I)Ljava/util/Optional;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/ItemRenderer;getModel(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/client/resources/model/BakedModel;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static BakedModel aeroscapes$nativeFluidIcon(BakedModel model) {
    return CopperBucketHeldRendering.nativeIcon(model);
  }

  @Inject(
      method =
          "resolve(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/client/resources/model/BakedModel;",
      at = @At("RETURN"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$heldVessel(
      BakedModel original,
      ItemStack stack,
      ClientLevel level,
      LivingEntity entity,
      int seed,
      CallbackInfoReturnable<BakedModel> callback) {
    callback.setReturnValue(CopperBucketHeldRendering.resolve(callback.getReturnValue(), stack));
  }
}
