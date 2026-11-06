package dev.aeroscapes.backup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Failed writes can clear native dirty flags; a later flush cannot prove recovery. */
public final class BackupSaveFailures {
  private static final Map<Path, Throwable> FAILURES = new ConcurrentHashMap<>();

  private BackupSaveFailures() {}

  public static void record(Path directory, Throwable cause) {
    FAILURES.putIfAbsent(directory.toAbsolutePath().normalize(), cause);
  }

  public static void requireHealthy(Path worldRoot) throws IOException {
    Path root = worldRoot.toAbsolutePath().normalize();
    for (var failure : FAILURES.entrySet()) {
      if (failure.getKey().startsWith(root))
        throw new IOException(
            "Backup blocked after a world save failure in this session: "
                + failure.getKey()
                + ". Resolve the storage error and reopen the world before backing up again.",
            failure.getValue());
    }
  }

  public static boolean blocked(Path worldRoot) {
    Path root = worldRoot.toAbsolutePath().normalize();
    return FAILURES.keySet().stream().anyMatch(directory -> directory.startsWith(root));
  }

  public static void clear(Path worldRoot) {
    Path root = worldRoot.toAbsolutePath().normalize();
    FAILURES.keySet().removeIf(directory -> directory.startsWith(root));
  }
}
