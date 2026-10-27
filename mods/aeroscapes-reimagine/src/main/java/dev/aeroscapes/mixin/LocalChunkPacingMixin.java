package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.PlayerChunkSender;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply the native client-feedback budget to integrated-server terrain delivery. */
@Mixin(PlayerChunkSender.class)
public abstract class LocalChunkPacingMixin {
  @ModifyExpressionValue(
      method = "collectChunksToSend",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/server/network/PlayerChunkSender;memoryConnection:Z",
              opcode = Opcodes.GETFIELD),
      require = 1,
      allow = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Select native quota without changing transport/ACKs.
  private boolean aeroscapesReimagine$useNativeQuota(boolean memoryConnection) {
    return false;
  }
}
