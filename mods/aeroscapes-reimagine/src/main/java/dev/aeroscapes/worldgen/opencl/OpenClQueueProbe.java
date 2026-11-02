package dev.aeroscapes.worldgen.opencl;

import dev.aeroscapes.diagnostics.LivenessProbe;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;

/**
 * A separate native observer owns queries/releases; snapshots and cleanup requests never wait on
 * it.
 */
public final class OpenClQueueProbe {
  private record Snapshot(long epoch, String text) {}

  private static final ConcurrentHashMap<Long, Event> EVENTS = new ConcurrentHashMap<>();
  private static final AtomicLong EPOCH = new AtomicLong();
  private static final AtomicLong SEQUENCE = new AtomicLong();
  private static final AtomicLong CALLBACKS = new AtomicLong();
  private static final AtomicLong DROPPED = new AtomicLong();
  private static final AtomicLong FAILURES = new AtomicLong();
  private static final AtomicLong MAX_CALLBACK_NANOS = new AtomicLong();
  private static final AtomicInteger SLOTS = new AtomicInteger();
  private static final ConcurrentLinkedQueue<Event> RELEASES = new ConcurrentLinkedQueue<>();
  private static final Object WAKEUP = new Object();
  @Nullable private static Thread worker;
  private static volatile Snapshot cached = new Snapshot(0, "pending=[]");
  private static volatile String nativeOperation = "none";
  private static volatile long nativeStarted;

  static {
    LivenessProbe.registerGpuSampler(OpenClQueueProbe::sample, OpenClQueueProbe::close);
  }

  private OpenClQueueProbe() {}

  @Nullable
  public static Event track(long handle) {
    if (!LivenessProbe.enabled()) return null;
    if (SLOTS.incrementAndGet() > 64) {
      SLOTS.decrementAndGet();
      DROPPED.incrementAndGet();
      return null;
    }
    long epoch = EPOCH.get();
    String origin;
    try {
      origin =
          StackWalker.getInstance()
              .walk(
                  frames ->
                      frames
                          .limit(16)
                          .map(StackWalker.StackFrame::getClassName)
                          .filter(
                              name ->
                                  name.contains("Stage1Cache") || name.contains("CLServerBatched"))
                          .findFirst()
                          .orElse("other"));
    } catch (RuntimeException | LinkageError failure) {
      SLOTS.decrementAndGet();
      FAILURES.incrementAndGet();
      return null;
    }
    int retained;
    try {
      retained = CL10.clRetainEvent(handle);
    } catch (RuntimeException | LinkageError failure) {
      // An unknown native result keeps its slot reserved rather than weakening the bound.
      FAILURES.incrementAndGet();
      return null;
    }
    if (retained != CL10.CL_SUCCESS) {
      SLOTS.decrementAndGet();
      FAILURES.incrementAndGet();
      return null;
    }
    Event event = new Event(handle, SEQUENCE.incrementAndGet(), origin, System.nanoTime());
    Event previous = EVENTS.putIfAbsent(handle, event);
    if (previous != null || !LivenessProbe.enabled() || EPOCH.get() != epoch) {
      EVENTS.remove(handle, event);
      releaseLater(event);
      return null;
    }
    wakeWorker();
    return event;
  }

  public static void completed(long handle, int status) {
    Event event = EVENTS.get(handle);
    if (event != null) event.completed(status);
  }

  public static String sample() {
    long started = nativeStarted;
    Snapshot snapshot = cached;
    return "registered="
        + SEQUENCE.get()
        + " callbacks="
        + CALLBACKS.get()
        + " retained="
        + SLOTS.get()
        + " maxCallbackMs="
        + MAX_CALLBACK_NANOS.get() / 1_000_000.0
        + " dropped="
        + DROPPED.get()
        + " failures="
        + FAILURES.get()
        + " nativeOperation="
        + nativeOperation
        + " nativeAgeMs="
        + (started == 0 ? 0 : Math.max(0, System.nanoTime() - started) / 1_000_000.0)
        + " "
        + (snapshot.epoch() == EPOCH.get() ? snapshot.text() : "pending=[]");
  }

  private static void poll() {
    long now = System.nanoTime();
    long epoch = EPOCH.get();
    var pending = new ArrayList<String>();
    for (Event event : EVENTS.values()) {
      if (event.callbackAt != 0
          || event.registration != CL10.CL_SUCCESS && event.registration != Integer.MAX_VALUE) {
        if (event.registration != CL10.CL_SUCCESS && event.registration != Integer.MAX_VALUE)
          FAILURES.incrementAndGet();
        if (EVENTS.remove(event.handle, event)) releaseLater(event);
      } else if (now - event.started >= 1_000_000_000L) {
        pending.add(event.describe(now));
      }
    }
    cached = new Snapshot(epoch, "sampleNanos=" + now + " pending=" + pending);
  }

  public static void close() {
    EPOCH.incrementAndGet();
    for (Event event : EVENTS.values()) if (EVENTS.remove(event.handle, event)) releaseLater(event);
  }

  private static void releaseLater(Event event) {
    if (event.releaseQueued.compareAndSet(false, true)) RELEASES.add(event);
    wakeWorker();
  }

  private static void wakeWorker() {
    synchronized (WAKEUP) {
      if (EVENTS.isEmpty() && RELEASES.isEmpty()) return;
      if (worker == null || !worker.isAlive()) {
        worker = new Thread(OpenClQueueProbe::run, "Aeroscapes GPU probe");
        worker.setDaemon(true);
        worker.start();
      }
      WAKEUP.notifyAll();
    }
  }

  private static void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        synchronized (WAKEUP) {
          while (EVENTS.isEmpty() && RELEASES.isEmpty()) WAKEUP.wait();
        }
        try {
          if (!LivenessProbe.enabled()) close();
          drainReleases();
          poll();
          drainReleases();
        } catch (RuntimeException | LinkageError failure) {
          FAILURES.incrementAndGet();
          nativeStarted = 0;
          nativeOperation = "failed";
          close();
          cached = new Snapshot(EPOCH.get(), "unavailable=" + failure.getClass().getSimpleName());
        }
        TimeUnit.SECONDS.sleep(1);
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private static void drainReleases() {
    Event event;
    while ((event = RELEASES.poll()) != null) event.release();
  }

  public static final class Event {
    private final long handle;
    private final long id;
    private final String origin;
    private final long started;
    private volatile long callbackAt;
    private volatile int callbackStatus = Integer.MIN_VALUE;
    private volatile int registration = Integer.MAX_VALUE;
    private final AtomicBoolean releaseQueued = new AtomicBoolean();
    private boolean released;

    private Event(long handle, long id, String origin, long started) {
      this.handle = handle;
      this.id = id;
      this.origin = origin;
      this.started = started;
    }

    /** Native callback threads only publish primitives; they never wait on diagnostic locks. */
    private void completed(int status) {
      callbackStatus = status;
      callbackAt = System.nanoTime();
      MAX_CALLBACK_NANOS.accumulateAndGet(callbackAt - started, Math::max);
      CALLBACKS.incrementAndGet();
    }

    public void registered(int result) {
      registration = result;
    }

    private String describe(long now) {
      if (released) return id + ":released";
      try (var stack = MemoryStack.stackPush()) {
        var value = stack.mallocInt(1);
        long queryStarted = System.nanoTime();
        nativeOperation = "query#" + id;
        nativeStarted = queryStarted;
        int result =
            CL10.clGetEventInfo(handle, CL10.CL_EVENT_COMMAND_EXECUTION_STATUS, value, null);
        long queryEnded = System.nanoTime();
        nativeStarted = 0;
        nativeOperation = "none";
        String state =
            result == CL10.CL_SUCCESS ? statusName(value.get(0)) : "queryError=" + result;
        return id
            + ":"
            + origin
            + ":"
            + state
            + ":ageMs="
            + (now - started) / 1_000_000.0
            + ":queryStartNanos="
            + queryStarted
            + ":queryMs="
            + (queryEnded - queryStarted) / 1_000_000.0
            + ":callbackNanos="
            + callbackAt
            + ":callbackStatus="
            + callbackStatus
            + ":registration="
            + registration;
      }
    }

    private void release() {
      if (released) return;
      released = true;
      nativeOperation = "release#" + id;
      nativeStarted = System.nanoTime();
      if (CL10.clReleaseEvent(handle) == CL10.CL_SUCCESS) SLOTS.decrementAndGet();
      else FAILURES.incrementAndGet();
      nativeStarted = 0;
      nativeOperation = "none";
    }
  }

  private static String statusName(int status) {
    return switch (status) {
      case CL10.CL_QUEUED -> "QUEUED";
      case CL10.CL_SUBMITTED -> "SUBMITTED";
      case CL10.CL_RUNNING -> "RUNNING";
      case CL10.CL_COMPLETE -> "COMPLETE";
      default -> "executionError=" + status;
    };
  }
}
