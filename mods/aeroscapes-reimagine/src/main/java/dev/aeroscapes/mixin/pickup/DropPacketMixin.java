package dev.aeroscapes.mixin.pickup;

import dev.aeroscapes.pickup.PickupRequests;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.creative.itemphysic.common.packet.DropPacket;

@Mixin(value = DropPacket.class, remap = false)
public abstract class DropPacketMixin {
  @Shadow public int power;

  @Inject(method = "executeServer", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Validate before the native inventory debit.
  private void aeroscapes$validateThrow(ServerPlayer player, CallbackInfo callback) {
    if (!PickupRequests.canThrow(player, power)) callback.cancel();
  }
}
