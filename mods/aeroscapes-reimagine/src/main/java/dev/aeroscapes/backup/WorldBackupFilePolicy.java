package dev.aeroscapes.backup;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Pure file selection shared by native configuration and archive policy checks. */
final class WorldBackupFilePolicy {
  private WorldBackupFilePolicy() {}

  static Predicate<Path> create(
      List<Path> excludedDirectories, List<Path> excludedFiles, String expression) {
    List<Path> ignoredDirectories = List.copyOf(excludedDirectories);
    List<Path> ignoredFiles = List.copyOf(excludedFiles);
    Pattern ignored = expression.isEmpty() ? null : Pattern.compile(expression);
    return relative -> {
      String name = relative.getFileName().toString();
      return !relative.equals(Path.of("session.lock"))
          && !name.endsWith(".neoforge-tmp")
          && !ignoredFiles.contains(relative)
          && ignoredDirectories.stream().noneMatch(relative::startsWith)
          && (ignored == null
              || !ignored.matcher(relative.toString().replace('\\', '/')).matches());
    };
  }
}
