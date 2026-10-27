package dev.aeroscapes.mixin.sable;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.diagnostics.SableDiagnostics;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.PacketReceiveMode;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe native missing and accepted snapshots without changing handling or packet order. */
@Mixin(value = ClientboundSableSnapshotDualPacket.class, remap = false)
public abstract class SableSnapshotDiagnosticMixin {
  @Shadow @Final private int interpolationTick;

  @Inject(
      method =
          "handleClient(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V",
      at =
          @At(
              value = "INVOKE",
              target = "Lorg/slf4j/Logger;error(Ljava/lang/String;)V",
              ordinal = 1))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this callback in Sable.
  private void aeroscapesReimagine$missing(
      Level level,
      PacketReceiveMode mode,
      CallbackInfo callback,
      @Local ClientboundSableSnapshotDualPacket.Entry entry) {
    SableDiagnostics.record(
        level,
        "snapshot-missing",
        entry.plotCoordinate(),
        interpolationTick,
        null,
        mode.name(),
        true);
  }

  @Inject(
      method =
          "handleClient(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/ryanhcode/sable/network/client/ClientSableInterpolationState;receiveSnapshot(Ldev/ryanhcode/sable/sublevel/ClientSubLevel;ILdev/ryanhcode/sable/companion/math/Pose3dc;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this callback in Sable.
  private void aeroscapesReimagine$received(
      Level level,
      PacketReceiveMode mode,
      CallbackInfo callback,
      @Local ClientboundSableSnapshotDualPacket.Entry entry) {
    SableDiagnostics.record(
        level,
        "snapshot-before-apply",
        entry.plotCoordinate(),
        interpolationTick,
        null,
        mode.name(),
        false);
  }
}
