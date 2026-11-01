package dev.aeroscapes.regression;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Waits for real chunk/entity promotion before the synchronous native workstation suite. */
public final class QolWorkstationReadiness {
  private final ServerLevel level;
  private final Runnable checks;
  private final Set<ChunkPos> chunks = new LinkedHashSet<>();
  private boolean done;
  private int ticks;

  private QolWorkstationReadiness(ServerLevel level, Runnable checks) {
    this.level = level;
    this.checks = checks;
    // These are the exact fixture session and distant-horse locations used by the current suite.
    BlockPos player =
        BlockPos.containing(Vec3.atCenterOf(level.getSharedSpawnPos()).add(32, 32, 32));
    chunks.add(new ChunkPos(player));
    chunks.add(new ChunkPos(player.offset(64, 0, 0)));
    for (ChunkPos chunk : chunks) {
      level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 2, chunk);
    }
  }

  public static void start(ServerLevel level, Runnable checks) {
    if (!level.getServer().isSameThread()) {
      throw new IllegalStateException("Prepare workstation readiness on the server thread");
    }
    QolWorkstationReadiness fixture = new QolWorkstationReadiness(level, checks);
    NeoForge.EVENT_BUS.addListener(fixture::tick);
    NeoForge.EVENT_BUS.addListener(fixture::stopping);
  }

  private void tick(ServerTickEvent.Post event) {
    if (done || event.getServer() != level.getServer()) return;
    ticks++;
    boolean ready =
        chunks.stream()
            .allMatch(
                chunk ->
                    level.areEntitiesLoaded(chunk.toLong())
                        && level.isPositionEntityTicking(
                            new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ())));
    if (!ready && ticks < 600) return;
    try {
      if (!ready) {
        throw new IllegalStateException(
            "Workstation fixture never reached loaded/entity-ticking readiness: " + chunks);
      }
      checks.run();
    } finally {
      cleanup();
    }
  }

  private void stopping(ServerStoppingEvent event) {
    if (!done && event.getServer() == level.getServer()) cleanup();
  }

  private void cleanup() {
    done = true;
    for (ChunkPos chunk : chunks) {
      level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 2, chunk);
    }
  }
}
