package dev.aeroscapes.diagnostics;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.TreeMap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Observes existing holders and sender state without requesting chunks. */
@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ServerChunkFlow {
  private static final ChunkFlowWindow WINDOW = new ChunkFlowWindow();

  private ServerChunkFlow() {}

  @SubscribeEvent
  public static void started(ServerStartedEvent event) {
    WINDOW.reset();
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    if (!WINDOW.sample(System.nanoTime())) return;
    var server = event.getServer();
    for (var level : server.getAllLevels()) {
      for (var player : level.players()) {
        int[] counts = new int[4];
        Map<String, Integer> statuses = new TreeMap<>();
        var chunks = level.getChunkSource().chunkMap;
        player
            .getChunkTrackingView()
            .forEach(
                position -> {
                  counts[0]++;
                  long key = position.toLong();
                  boolean available = chunks.getChunkToSend(key) != null;
                  if (available) counts[1]++;
                  if (player.connection.chunkSender.isPending(key)) {
                    counts[2]++;
                    if (available) counts[3]++;
                  }
                  var holder = chunks.getVisibleChunkIfPresent(key);
                  statuses.merge(
                      holder == null ? "absent" : String.valueOf(holder.getLatestStatus()),
                      1,
                      Integer::sum);
                });
        LogUtils.getLogger()
            .info(
                "[Aeroscapes chunk flow] server tick={} dimension={} pos={},{},{} view={} getterAvailable={} pendingInView={} readyPending={} meanTickMs={} statuses={}",
                server.getTickCount(),
                level.dimension().location(),
                player.getX(),
                player.getY(),
                player.getZ(),
                counts[0],
                counts[1],
                counts[2],
                counts[3],
                server.getAverageTickTimeNanos() / 1_000_000.0,
                statuses);
      }
    }
  }
}
