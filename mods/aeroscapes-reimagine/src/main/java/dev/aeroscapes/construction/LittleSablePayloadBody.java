package dev.aeroscapes.construction;

import net.minecraft.core.BlockPos;

/**
 * Payload changes are coalesced on their native body before physics consumes its mass and shape.
 */
public interface LittleSablePayloadBody {
  void aeroscapes$payloadChanged(BlockPos position);

  void aeroscapes$flushPayloads();
}
