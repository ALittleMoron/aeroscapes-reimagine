package dev.aeroscapes.mixin;

import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerChunkSender.class)
public interface ChunkSenderDiagnosticsAccess {
  @Accessor("desiredChunksPerTick")
  float aeroscapes$desiredChunksPerTick();

  @Accessor("batchQuota")
  float aeroscapes$batchQuota();

  @Accessor("unacknowledgedBatches")
  int aeroscapes$unacknowledgedBatches();

  @Accessor("maxUnacknowledgedBatches")
  int aeroscapes$maximumUnacknowledgedBatches();
}
