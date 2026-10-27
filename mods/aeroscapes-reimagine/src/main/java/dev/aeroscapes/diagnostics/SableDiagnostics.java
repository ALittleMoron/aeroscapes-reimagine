package dev.aeroscapes.diagnostics;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Opt-in, bounded history of client-observed Sable lifecycle events. */
public final class SableDiagnostics {
  private static final SableTraceBuffer BUFFER = new SableTraceBuffer(128, 64, 256);

  private SableDiagnostics() {}

  public static void record(
      Level level, String event, long plot, Integer tick, UUID id, String mode, boolean missing) {
    if (level == null) return;
    String details =
        "time-ns="
            + System.nanoTime()
            + " dimension="
            + level.dimension().location()
            + " event="
            + event
            + " plot="
            + ChunkPos.getX(plot)
            + ","
            + ChunkPos.getZ(plot)
            + " tick="
            + tick
            + " uuid="
            + id
            + " receive-mode="
            + mode;
    for (String entry : BUFFER.record(level, details, missing)) {
      LogUtils.getLogger().info("Sable diagnostic {}", entry);
    }
  }
}
