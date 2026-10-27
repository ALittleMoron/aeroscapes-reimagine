package dev.aeroscapes.monitor;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/** Serializes monitor requests and bounds each recording without touching the render thread. */
public final class MonitorController {
  @FunctionalInterface
  public interface Sender {
    void send(String command) throws IOException, InterruptedException;
  }

  private final ScheduledExecutorService worker;
  private final Sender sender;
  private final BiConsumer<String, Integer> feedback;
  private ScheduledFuture<?> automaticStop;

  public MonitorController(
      ScheduledExecutorService worker, Sender sender, BiConsumer<String, Integer> feedback) {
    this.worker = worker;
    this.sender = sender;
    this.feedback = feedback;
  }

  public void toggle() {
    worker.execute(() -> send(":hud;", "toggle", 0));
  }

  public void record(int seconds) {
    worker.execute(
        () -> {
          if (!send(":logging=1;", "record", seconds)) return;
          if (automaticStop != null) automaticStop.cancel(false);
          automaticStop =
              worker.schedule(() -> send(":logging=0;", "stopped", 0), seconds, TimeUnit.SECONDS);
        });
  }

  public void stop() {
    worker.execute(
        () -> {
          if (send(":logging=0;", "stopped", 0) && automaticStop != null) {
            automaticStop.cancel(false);
            automaticStop = null;
          }
        });
  }

  private boolean send(String command, String event, int seconds) {
    try {
      sender.send(command);
      feedback.accept(event, seconds);
      return true;
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
      feedback.accept("unavailable", 0);
      return false;
    }
  }
}
