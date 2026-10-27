package dev.aeroscapes.mixin.sable;

import dev.aeroscapes.diagnostics.SableDiagnostics;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopMovingSubLevelPacket;
import foundry.veil.api.network.handler.PacketContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientboundStopMovingSubLevelPacket.class, remap = false)
public abstract class SableStopMovingDiagnosticMixin {
  @Shadow @Final private long plotCoordinate;

  @Inject(method = "handle", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this callback in Sable.
  private void aeroscapesReimagine$received(PacketContext context, CallbackInfo callback) {
    SableDiagnostics.record(
        context.level(), "stop-moving-received", plotCoordinate, null, null, "TCP", false);
  }

  @Inject(
      method = "handle",
      at =
          @At(
              value = "INVOKE",
              target = "Ldev/ryanhcode/sable/sublevel/ClientSubLevel;receiveServerMovementStop()V",
              shift = At.Shift.AFTER))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this callback in Sable.
  private void aeroscapesReimagine$applied(PacketContext context, CallbackInfo callback) {
    SableDiagnostics.record(
        context.level(), "stop-moving-applied", plotCoordinate, null, null, "TCP", false);
  }
}
