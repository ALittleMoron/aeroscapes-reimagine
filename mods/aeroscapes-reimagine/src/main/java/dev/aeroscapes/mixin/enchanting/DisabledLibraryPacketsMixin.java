package dev.aeroscapes.mixin.enchanting;

import me.alfie.immersiveenchanting.networking.RemoveEnchantmentPacket;
import me.alfie.immersiveenchanting.networking.TransmutePacket;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {RemoveEnchantmentPacket.class, TransmutePacket.class},
    remap = false)
abstract class DisabledLibraryPacketsMixin {
  @Inject(method = "exec", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$disableUnpricedOperation(IPayloadContext context, CallbackInfo callback) {
    callback.cancel();
  }
}
