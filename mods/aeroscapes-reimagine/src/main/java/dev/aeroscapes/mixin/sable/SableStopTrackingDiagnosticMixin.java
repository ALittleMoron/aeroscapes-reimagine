package dev.aeroscapes.mixin.sable;

import dev.aeroscapes.diagnostics.SableDiagnostics;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket;
import foundry.veil.api.network.handler.PacketContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientboundStopTrackingSubLevelPacket.class, remap = false)
public abstract class SableStopTrackingDiagnosticMixin {
  @Shadow @Final private long plotCoordinate;

  @Inject(method = "handle", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this callback in Sable.
  private void aeroscapesReimagine$received(PacketContext context, CallbackInfo callback) {
    SableDiagnostics.record(
        context.level(), "stop-tracking-received", plotCoordinate, null, null, "TCP", false);
  }

  @Inject(
      method = "handle",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;removeSubLevel(IILdev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V",
              shift = At.Shift.AFTER))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this callback in Sable.
  private void aeroscapesReimagine$applied(PacketContext context, CallbackInfo callback) {
    SableDiagnostics.record(
        context.level(), "stop-tracking-removed", plotCoordinate, null, null, "TCP", false);
  }
}
