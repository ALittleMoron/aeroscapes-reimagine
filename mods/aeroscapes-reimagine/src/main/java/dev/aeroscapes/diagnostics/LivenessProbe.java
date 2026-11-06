package dev.aeroscapes.diagnostics;

import com.mojang.logging.LogUtils;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in wall-clock observations that remain available while the server awaits a chunk. */
@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class LivenessProbe {
  private static final Object LIFECYCLE = new Object();
  private static final AwaitTracker AWAITS = new AwaitTracker();
  private static final long WINDOW_NANOS = TimeUnit.MINUTES.toNanos(5);
  @Nullable private static ScheduledExecutorService worker;
  private static volatile long generation;
  private static volatile long deadline;
  private static volatile long tickPreNanos;
  private static volatile long tickPostNanos;
  private static volatile long tickPreCount;
  private static volatile long tickPostCount;
  private static volatile long frameNanos;
  private static volatile long frameCount;

  private LivenessProbe() {}

  public static boolean enabled() {
    long until = deadline;
    return ChunkFlowWindow.ENABLED && until != 0 && System.nanoTime() - until < 0;
  }

  public static long beginAwait(String detail) {
    if (!enabled()) return 0;
    synchronized (LIFECYCLE) {
      return enabled() ? AWAITS.begin(detail, System.nanoTime()) : 0;
    }
  }

  public static void endAwait(long token) {
    if (token != 0) AWAITS.end(token);
  }

  public static void clientFrame(long nanos) {
    if (!enabled()) return;
    frameNanos = nanos;
    frameCount++;
  }

  @SubscribeEvent
  public static void started(ServerStartedEvent event) {
    if (!ChunkFlowWindow.ENABLED) return;
    stop();
    List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
    synchronized (LIFECYCLE) {
      long session = ++generation;
      tickPreNanos = 0;
      tickPostNanos = 0;
      tickPreCount = 0;
      tickPostCount = 0;
      frameNanos = 0;
      frameCount = 0;
      deadline = System.nanoTime() + WINDOW_NANOS;
      worker =
          Executors.newSingleThreadScheduledExecutor(
              task -> {
                Thread thread = new Thread(task, "Aeroscapes liveness");
                thread.setDaemon(true);
                return thread;
              });
      worker.scheduleWithFixedDelay(() -> safeSample(collectors, session), 1, 1, TimeUnit.SECONDS);
    }
    LogUtils.getLogger().info("[Aeroscapes liveness] started five-minute observation window");
  }

  @SubscribeEvent
  public static void tickPre(ServerTickEvent.Pre event) {
    if (!enabled()) return;
    tickPreNanos = System.nanoTime();
    tickPreCount++;
  }

  @SubscribeEvent
  public static void tickPost(ServerTickEvent.Post event) {
    if (!enabled()) return;
    tickPostNanos = System.nanoTime();
    tickPostCount++;
  }

  @SubscribeEvent
  public static void stopping(ServerStoppingEvent event) {
    if (ChunkFlowWindow.ENABLED) stop();
  }

  private static void safeSample(List<GarbageCollectorMXBean> collectors, long session) {
    try {
      sample(collectors, session);
    } catch (RuntimeException | LinkageError failure) {
      LogUtils.getLogger()
          .warn(
              "[Aeroscapes liveness] observation failed; stopping window: {}", failure.toString());
      stop(session);
    }
  }

  private static void sample(List<GarbageCollectorMXBean> collectors, long session) {
    if (generation != session) return;
    if (!enabled()) {
      stop(session);
      return;
    }
    long now = System.nanoTime();
    StringJoiner waits = new StringJoiner("; ", "[", "]");
    for (AwaitTracker.Await awaiting : AWAITS.snapshot()) {
      waits.add(
          "id="
              + awaiting.token()
              + " ageMs="
              + age(now, awaiting.startedNanos())
              + " "
              + awaiting.detail());
    }
    long collections = 0;
    long collectionMillis = 0;
    for (GarbageCollectorMXBean collector : collectors) {
      collections += Math.max(0, collector.getCollectionCount());
      collectionMillis += Math.max(0, collector.getCollectionTime());
    }
    if (!enabled() || generation != session) return;
    LogUtils.getLogger()
        .info(
            "[Aeroscapes liveness] nowNanos={} tickPre={} tickPost={} tickPreAgeMs={}"
                + " tickPostAgeMs={} frames={} frameAgeMs={} awaits={} gcCount={} gcTimeMs={}"
                + " awaitDropped={}",
            now,
            tickPreCount,
            tickPostCount,
            age(now, tickPreNanos),
            age(now, tickPostNanos),
            frameCount,
            age(now, frameNanos),
            waits,
            collections,
            collectionMillis,
            AWAITS.dropped());
  }

  private static long age(long now, long timestamp) {
    return timestamp == 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(Math.max(0, now - timestamp));
  }

  private static void stop() {
    stop(0);
  }

  private static void stop(long session) {
    ScheduledExecutorService previous;
    synchronized (LIFECYCLE) {
      if (session != 0 && generation != session) return;
      generation++;
      deadline = 0;
      previous = worker;
      worker = null;
      AWAITS.clear();
    }
    if (previous != null) previous.shutdownNow();
  }
}
