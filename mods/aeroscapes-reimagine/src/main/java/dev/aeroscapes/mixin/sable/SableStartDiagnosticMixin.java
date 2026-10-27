package dev.aeroscapes.mixin.sable;

import dev.aeroscapes.diagnostics.SableDiagnostics;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import foundry.veil.api.network.handler.PacketContext;
import java.util.UUID;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientboundStartTrackingSubLevelPacket.class, remap = false)
public abstract class SableStartDiagnosticMixin {
  @Shadow @Final private long plotCoordinate;
  @Shadow @Final private UUID subLevelID;
  @Shadow @Final private int gameTick;

  @Inject(method = "handle", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this callback in Sable.
  private void aeroscapesReimagine$start(PacketContext context, CallbackInfo callback) {
    SableDiagnostics.record(
        context.level(), "start-received", plotCoordinate, gameTick, subLevelID, "TCP", false);
  }

  @Inject(
      method = "handle",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/ryanhcode/sable/api/sublevel/ClientSubLevelContainer;allocateSubLevel(Ljava/util/UUID;IILdev/ryanhcode/sable/companion/math/Pose3d;)Ldev/ryanhcode/sable/sublevel/SubLevel;",
              shift = At.Shift.AFTER))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this callback in Sable.
  private void aeroscapesReimagine$allocated(PacketContext context, CallbackInfo callback) {
    SableDiagnostics.record(
        context.level(), "start-allocated", plotCoordinate, gameTick, subLevelID, "TCP", false);
  }
}
