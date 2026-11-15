package dev.aeroscapes.mixin.husbandry;

import dev.architectury.networking.NetworkManager.PacketContext;
import org.athisworkshop.animalhusbandry.network.HighlightAnimalPayload;
import org.athisworkshop.animalhusbandry.network.ModNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModNetworking.class, remap = false)
abstract class HusbandryHighlightMixin {
  @Inject(method = "lambda$init$8", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private static void aeroscapes$disableHighlight(
      PacketContext context, HighlightAnimalPayload payload, CallbackInfo callback) {
    callback.cancel();
  }
}
