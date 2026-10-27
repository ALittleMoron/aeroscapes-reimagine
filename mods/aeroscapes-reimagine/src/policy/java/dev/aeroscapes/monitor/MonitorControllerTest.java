package dev.aeroscapes.monitor;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Checks recording duration, replacement and cancellation without a Minecraft client. */
public final class MonitorControllerTest {
  private MonitorControllerTest() {}

  public static void main(String[] arguments) throws Exception {
    ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    BlockingQueue<String> commands = new LinkedBlockingQueue<>();
    BlockingQueue<String> feedback = new LinkedBlockingQueue<>();
    try {
      var controller =
          new MonitorController(worker, commands::add, (event, seconds) -> feedback.add(event));
      controller.toggle();
      require(":hud;".equals(commands.poll(2, TimeUnit.SECONDS)), "toggle request");
      controller.record(1);
      require(":logging=1;".equals(commands.poll(2, TimeUnit.SECONDS)), "record start");
      require(":logging=0;".equals(commands.poll(2, TimeUnit.SECONDS)), "automatic stop");
      controller.record(1);
      require(":logging=1;".equals(commands.poll(2, TimeUnit.SECONDS)), "second record start");
      controller.stop();
      require(":logging=0;".equals(commands.poll(2, TimeUnit.SECONDS)), "explicit stop");
      require(commands.poll(1200, TimeUnit.MILLISECONDS) == null, "explicit stop cancels timer");
      controller.record(1);
      require(
          ":logging=1;".equals(commands.poll(2, TimeUnit.SECONDS)), "record before replacement");
      controller.record(2);
      require(
          ":logging=1;".equals(commands.poll(2, TimeUnit.SECONDS)), "replacement start request");
      require(commands.poll(1300, TimeUnit.MILLISECONDS) == null, "old timer is cancelled");
      require(":logging=0;".equals(commands.poll(2, TimeUnit.SECONDS)), "replacement timer stops");
      BlockingQueue<String> failedFeedback = new LinkedBlockingQueue<>();
      var failing =
          new MonitorController(
              worker,
              command -> {
                throw new IOException("offline");
              },
              (event, seconds) -> failedFeedback.add(event));
      failing.record(1);
      require(
          "unavailable".equals(failedFeedback.poll(2, TimeUnit.SECONDS)),
          "unavailable monitor reported");
      require(
          failedFeedback.poll(1200, TimeUnit.MILLISECONDS) == null,
          "failed start schedules no stop");
      System.out.println(
          "Monitor: recording timer, replacement, stop and unavailable control passed");
    } finally {
      worker.shutdownNow();
      worker.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
