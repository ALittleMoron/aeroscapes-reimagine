package dev.aeroscapes.mixin;

import com.chaosthedude.explorerscompass.network.SearchPacket;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.netty.handler.codec.DecoderException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SearchPacket.class, remap = false)
public abstract class CompassLegacyPacketMixin {
  @ModifyExpressionValue(
      method = "read",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readInt()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private static int aeroscapes$boundLegacyMembers(int count) {
    if (count < 0 || count > 512) throw new DecoderException("Invalid legacy compass member count");
    return count;
  }
}
