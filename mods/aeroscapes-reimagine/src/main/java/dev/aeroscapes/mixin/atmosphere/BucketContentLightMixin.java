package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.BucketHeldRendering;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.resource.item.ItemLightSources", remap = false)
public abstract class BucketContentLightMixin {
  @Inject(
      method = "getLuminance(Lnet/minecraft/world/item/ItemStack;Z)I",
      at = @At("RETURN"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$bucketContentsLight(
      ItemStack stack, boolean underwater, CallbackInfoReturnable<Integer> callback) {
    callback.setReturnValue(
        Math.max(callback.getReturnValueI(), BucketHeldRendering.luminance(stack)));
  }
}
