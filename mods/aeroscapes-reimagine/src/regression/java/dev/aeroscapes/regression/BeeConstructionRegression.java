package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Native, unspawned bee construction on the server thread and one owned worker. */
public final class BeeConstructionRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Set<String> GOALS =
      Set.of(
          "net.minecraft.world.entity.animal.Bee$BeeGoToHiveGoal",
          "net.minecraft.world.entity.animal.Bee$BeeGoToKnownFlowerGoal");
  private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<>();
  private static final long WORKER_TIMEOUT_NANOS = 15_000_000_000L;

  private BeeConstructionRegression() {}

  public static void start(ServerLevel level, Path output) {
    if (!level.getServer().isDedicatedServer() || !level.getServer().isSameThread())
      throw new IllegalArgumentException(
          "Bee construction fixture needs the dedicated server thread");
    Audit audit = new Audit(level, output.toAbsolutePath().normalize());
    NeoForge.EVENT_BUS.addListener(audit.tickListener);
    NeoForge.EVENT_BUS.addListener(audit.stopListener);
    audit.begin();
  }

  public static Call before(Bee bee, String goal, RandomSource receiver, int bound) {
    Capture capture = CURRENT.get();
    return capture == null ? null : capture.before(bee, goal, receiver, bound);
  }

  public static void returned(Call call, int value) {
    if (call != null) call.capture.returned(call, value);
  }

  public static void failed(Call call, Throwable failure) {
    if (call != null) call.capture.failed(call, failure);
  }

  public static final class Call {
    private final Capture capture;
    private final String goal;
    private final String thread;
    private final int bound;
    private final boolean beeRandom;
    private final boolean worldRandom;
    private Integer value;
    private Throwable failure;

    private Call(Capture capture, Bee bee, String goal, RandomSource receiver, int bound) {
      this.capture = capture;
      this.goal = goal;
      this.thread = Thread.currentThread().getName();
      this.bound = bound;
      beeRandom = receiver == bee.getRandom();
      worldRandom = receiver == bee.level().random;
    }
  }

  private static final class Capture {
    private final String phase;
    private final List<Call> calls = new ArrayList<>();

    private Capture(String phase) {
      this.phase = phase;
    }

    private synchronized Call before(Bee bee, String goal, RandomSource receiver, int bound) {
      Call call = new Call(this, bee, goal, receiver, bound);
      calls.add(call);
      return call;
    }

    private synchronized void returned(Call call, int value) {
      call.value = value;
    }

    private synchronized void failed(Call call, Throwable failure) {
      call.failure = failure;
    }

    private synchronized JsonObject report() {
      JsonObject result = new JsonObject();
      result.addProperty("phase", phase);
      JsonArray rows = new JsonArray();
      for (Call call : calls) {
        JsonObject row = new JsonObject();
        row.addProperty("goal", call.goal);
        row.addProperty("thread", call.thread);
        row.addProperty("bound", call.bound);
        row.addProperty("receiver_is_bee_random", call.beeRandom);
        row.addProperty("receiver_is_world_random", call.worldRandom);
        row.addProperty("native_returned", call.value != null);
        if (call.value != null) row.addProperty("returned_value", call.value);
        if (call.failure != null) row.addProperty("failure", describe(call.failure));
        rows.add(row);
      }
      result.add("goal_calls", rows);
      return result;
    }

    private synchronized boolean valid(boolean worker) {
      if (calls.size() != 2
          || !calls.stream().map(call -> call.goal).collect(Collectors.toSet()).equals(GOALS))
        return false;
      return calls.stream()
          .allMatch(
              call ->
                  call.bound == 10
                      && call.failure == null
                      && call.value != null
                      && call.value >= 0
                      && call.value < 10
                      && (worker
                          ? call.beeRandom && !call.worldRandom
                          : call.worldRandom && !call.beeRandom));
    }
  }

  private static final class Attempt {
    private final Capture capture;
    private volatile Bee bee;
    private volatile Throwable failure;
    private volatile boolean finished;

    private Attempt(String phase) {
      capture = new Capture(phase);
    }

    private void construct(ServerLevel level) {
      Capture previous = CURRENT.get();
      CURRENT.set(capture);
      try {
        bee = EntityType.BEE.create(level);
      } catch (RuntimeException | Error nativeFailure) {
        failure = nativeFailure;
        throw nativeFailure;
      } finally {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
        finished = true;
      }
    }

    private JsonObject report() {
      JsonObject result = capture.report();
      result.addProperty("construction_finished", finished);
      result.addProperty("native_bee_nonnull", bee != null);
      result.addProperty("entity_added_to_world", false);
      if (failure != null) result.addProperty("failure", describe(failure));
      return result;
    }

    private boolean valid(boolean worker) {
      return finished && bee != null && failure == null && capture.valid(worker);
    }
  }

  private static final class Audit {
    private final ServerLevel level;
    private final Path output;
    private final Attempt main = new Attempt("server_thread");
    private final Attempt worker = new Attempt("worker_thread");
    private final ExecutorService executor =
        Executors.newSingleThreadExecutor(
            task -> {
              Thread thread = new Thread(task, "aeroscapes-bee-construction-probe");
              thread.setDaemon(true);
              return thread;
            });
    private final Consumer<ServerTickEvent.Post> tickListener = this::tick;
    private final Consumer<ServerStoppingEvent> stopListener = this::stopping;
    private CompletableFuture<Void> task;
    private long workerStarted;
    private boolean done;

    private Audit(ServerLevel level, Path output) {
      this.level = level;
      this.output = output;
    }

    private void begin() {
      LOGGER.info("Bee construction regression started: output={}", output);
      try {
        main.construct(level);
      } catch (RuntimeException | Error nativeFailure) {
        main.failure = nativeFailure;
      }
      try {
        workerStarted = System.nanoTime();
        task = CompletableFuture.runAsync(() -> worker.construct(level), executor);
      } catch (RuntimeException failure) {
        finish("worker_submission_failed", failure);
      }
    }

    private void tick(ServerTickEvent.Post event) {
      if (done || event.getServer() != level.getServer()) return;
      if (task != null && task.isDone()) finish("native_constructions_finished", null);
      else if (System.nanoTime() - workerStarted >= WORKER_TIMEOUT_NANOS)
        finish("worker_timeout", new IllegalStateException("Bee worker exceeded 15 seconds"));
    }

    private void stopping(ServerStoppingEvent event) {
      if (!done && event.getServer() == level.getServer())
        finish("server_stopping", new IllegalStateException("Bee fixture interrupted by stop"));
    }

    private void finish(String termination, Throwable extraFailure) {
      if (done) return;
      done = true;
      NeoForge.EVENT_BUS.unregister(tickListener);
      NeoForge.EVENT_BUS.unregister(stopListener);
      if (task != null && !task.isDone()) task.cancel(true);
      executor.shutdownNow();
      JsonArray errors = new JsonArray();
      if (main.failure != null) errors.add("server_thread: " + describe(main.failure));
      if (worker.failure != null) errors.add("worker_thread: " + describe(worker.failure));
      if (extraFailure != null) errors.add(describe(extraFailure));
      boolean passed = main.valid(false) && worker.valid(true) && errors.isEmpty();
      if (!passed && errors.isEmpty())
        errors.add("Native bee constructor goal count, receiver, bound or result contract failed");
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("status", passed ? "passed" : "error");
      report.addProperty("termination", termination);
      report.addProperty("dimension", level.dimension().location().toString());
      report.addProperty("seed", level.getSeed());
      report.addProperty("worker_timeout_seconds", 15);
      report.addProperty("executor_shutdown", executor.isShutdown());
      report.addProperty("worker_task_finished", worker.finished);
      report.addProperty("native_construction_only_no_world_insertion", true);
      report.add("server_thread", main.report());
      report.add("worker_thread", worker.report());
      report.add("errors", errors);
      report.addProperty(
          "scope",
          "Actual EntityType.BEE.create on main and owned worker threads; no entity is added."
              + " The probe observes both native goal constructors' nextInt receivers, bounds,"
              + " returns and exceptions; it does not count the production field-read wrapper."
              + " This checks construction and RNG receivers, not client visuals or natural spawning.");
      try {
        write(output, report);
      } catch (IOException failure) {
        LOGGER.error("Cannot write bee construction regression report: output={}", output, failure);
        passed = false;
      }
      if (!passed) {
        Throwable failure =
            extraFailure != null
                ? extraFailure
                : worker.failure != null ? worker.failure : main.failure;
        LOGGER.error(
            "Bee construction regression failed: output={} errors={}", output, errors, failure);
      }
      LOGGER.info(
          "Bee construction regression completed: status={} output={}",
          passed ? "passed" : "error",
          output);
    }
  }

  private static String describe(Throwable failure) {
    return failure.getClass().getName() + ": " + failure.getMessage();
  }

  private static void write(Path output, JsonObject report) throws IOException {
    Path parent = output.getParent();
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, output.getFileName() + ".", ".tmp");
    try {
      Files.writeString(
          temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      Files.move(
          temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException failure) {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }
}
