package dev.aeroscapes.mixin.sky;

import dev.aeroscapes.sky.WhaleVariantAccess;
import dev.aeroscapes.sky.client.WhaleBlockTextures;
import dev.dacite.skywhales.client.SkyWhaleModel;
import dev.dacite.skywhales.entity.SkyWhale;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SkyWhaleModel.class, remap = false)
public abstract class WhaleVarietyModelMixin {
  @Inject(
      method =
          "getTextureResource(Ldev/dacite/skywhales/entity/SkyWhale;)Lnet/minecraft/resources/ResourceLocation;",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // GeckoLib's actual typed dispatch, not its bridge.
  private void aeroscapes$texture(
      SkyWhale whale, CallbackInfoReturnable<ResourceLocation> callback) {
    callback.setReturnValue(
        WhaleBlockTextures.texture(((WhaleVariantAccess) whale).aeroscapes$variety()));
  }

  @Inject(
      method =
          "getModelResource(Ldev/dacite/skywhales/entity/SkyWhale;)Lnet/minecraft/resources/ResourceLocation;",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The native typed model dispatch selects the form.
  private void aeroscapes$model(SkyWhale whale, CallbackInfoReturnable<ResourceLocation> callback) {
    callback.setReturnValue(((WhaleVariantAccess) whale).aeroscapes$variety().model());
  }

  @Inject(
      method =
          "getAnimationResource(Ldev/dacite/skywhales/entity/SkyWhale;)Lnet/minecraft/resources/ResourceLocation;",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native controllers retain their animation names.
  private void aeroscapes$animation(
      SkyWhale whale, CallbackInfoReturnable<ResourceLocation> callback) {
    callback.setReturnValue(((WhaleVariantAccess) whale).aeroscapes$variety().animation());
  }
}
