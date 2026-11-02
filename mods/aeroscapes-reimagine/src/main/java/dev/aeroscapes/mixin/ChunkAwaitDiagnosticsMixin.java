package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.diagnostics.LivenessProbe;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Observes the actual synchronous wait, leaving cached and already-complete reads alone. */
@Mixin(ServerChunkCache.class)
public abstract class ChunkAwaitDiagnosticsMixin {
  @Shadow @Final private ServerLevel level;

  @WrapOperation(
      method =
          "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;managedBlock(Ljava/util/function/BooleanSupplier;)V"),
      require = 1,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Exact native wait invocation, chained with C2ME.
  private void aeroscapes$observeChunkWait(
      @Coerce Object executor,
      BooleanSupplier complete,
      Operation<Void> original,
      int chunkX,
      int chunkZ,
      ChunkStatus status,
      boolean create) {
    if (!LivenessProbe.enabled() || complete.getAsBoolean()) {
      original.call(executor, complete);
      return;
    }
    long token =
        LivenessProbe.beginAwait(
            "dimension="
                + level.dimension().location()
                + " chunk="
                + chunkX
                + ","
                + chunkZ
                + " status="
                + status
                + " create="
                + create
                + " thread="
                + Thread.currentThread().getName());
    try {
      original.call(executor, complete);
    } finally {
      LivenessProbe.endAwait(token);
    }
  }
}
