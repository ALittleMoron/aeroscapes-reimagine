package dev.aeroscapes.backup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class BackupSaveFailuresTest {
  private BackupSaveFailuresTest() {}

  public static void verify(Path root) throws IOException {
    Path world = root.resolve("saved-world");
    Path other = root.resolve("saved-world-other");
    IOException first = new IOException("failed asynchronous saved-data write");
    try {
      BackupSaveFailures.requireHealthy(world);
      CompletableFuture.runAsync(() -> BackupSaveFailures.record(world.resolve("data"), first))
          .join();
      BackupSaveFailures.record(world.resolve("data/../data"), new IOException("later failure"));
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          BackupSaveFailures.requireHealthy(world.resolve("region/.."));
          throw new AssertionError("A later backup attempt forgot the earlier save failure");
        } catch (IOException failure) {
          if (failure.getCause() != first)
            throw new AssertionError("The original failure was replaced", failure);
        }
      }
      BackupSaveFailures.requireHealthy(other);
      BackupSaveFailures.record(other.resolve("region"), new IOException("other world failure"));
      BackupSaveFailures.clear(world);
      BackupSaveFailures.requireHealthy(world);
      try {
        BackupSaveFailures.requireHealthy(other);
        throw new AssertionError("Stopping one world cleared a different world's failure");
      } catch (IOException expected) {
        if (!expected.getMessage().contains("saved-world-other")) throw expected;
      }
    } finally {
      BackupSaveFailures.clear(world);
      BackupSaveFailures.clear(other);
    }
  }
}
