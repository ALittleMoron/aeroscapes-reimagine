package dev.aeroscapes.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** A synchronous file snapshot followed by worker-owned archival and post-publication retention. */
public final class ManagedBackupArchive implements AutoCloseable {
  private static final String MARKER = "aeroscapes_reimagine:full-backup:1:";
  private static final Pattern ARCHIVE_NAME =
      Pattern.compile("aeroscapes-[0-9a-f]{16}-[0-9]+-[0-9a-f-]{36}\\.zip");
  private static final long FREE_SPACE_RESERVE = 128L * 1024 * 1024;
  private static final Set<Path> OWNED_OUTPUTS = ConcurrentHashMap.newKeySet();
  private final Path output;
  private final Path staging;
  private final String worldName;
  private final String worldKey;
  private final Map<Path, Long> manifest;
  private final FileChannel lockChannel;
  private final FileLock lock;
  private final AtomicPublisher publisher;
  private final AtomicBoolean closed = new AtomicBoolean();

  @FunctionalInterface
  interface AtomicPublisher {
    void publish(Path source, Path destination) throws IOException;
  }

  public record Publication(Path archive, List<String> retentionWarnings) {}

  private record ExistingArchive(Path path, String worldKey, long size, long modified) {}

  private ManagedBackupArchive(
      Path output,
      Path staging,
      String worldName,
      String worldKey,
      Map<Path, Long> manifest,
      FileChannel lockChannel,
      FileLock lock,
      AtomicPublisher publisher) {
    this.output = output;
    this.staging = staging;
    this.worldName = worldName;
    this.worldKey = worldKey;
    this.manifest = Map.copyOf(manifest);
    this.lockChannel = lockChannel;
    this.lock = lock;
    this.publisher = publisher;
  }

  public static ManagedBackupArchive capture(
      Path source, Path output, Predicate<Path> includeRelativeFile) throws IOException {
    return capture(
        source,
        output,
        includeRelativeFile,
        (temporary, destination) ->
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE));
  }

  static ManagedBackupArchive capture(
      Path source, Path output, Predicate<Path> includeRelativeFile, AtomicPublisher publisher)
      throws IOException {
    Path world = source.toRealPath();
    Files.createDirectories(output);
    Path root = output.toRealPath();
    if (root.startsWith(world)) throw new IOException("Backup output is inside the saved world");
    // Some filesystems release a process's locks when any same-file channel closes.
    if (!OWNED_OUTPUTS.add(root)) throw new IOException("Another backup owns the output folder");
    FileChannel channel;
    try {
      channel =
          FileChannel.open(
              root.resolve(".aeroscapes-backup.lock"),
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE);
    } catch (IOException | RuntimeException | Error exception) {
      OWNED_OUTPUTS.remove(root);
      throw exception;
    }
    FileLock acquired;
    try {
      acquired = channel.tryLock();
      if (acquired == null) throw new IOException("Another backup owns the output folder");
    } catch (IOException | OverlappingFileLockException | Error exception) {
      try {
        channel.close();
      } catch (IOException | RuntimeException | Error cleanup) {
        if (exception != cleanup) exception.addSuppressed(cleanup);
      }
      OWNED_OUTPUTS.remove(root);
      if (exception instanceof Error fatal) throw fatal;
      throw new IOException("Cannot acquire backup output lock", exception);
    }
    Path stage = null;
    try {
      recoverIncompleteStages(root);
      Map<Path, Long> files = new HashMap<>();
      Files.walkFileTree(
          world,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                throws IOException {
              Path relative = world.relativize(file);
              if (includeRelativeFile.test(relative)) {
                if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                  throw new IOException("Unsupported world file: " + relative);
                }
                files.put(relative, attributes.size());
              }
              return FileVisitResult.CONTINUE;
            }
          });
      if (files.isEmpty()) throw new IOException("Saved world has no files");
      long size = 0;
      try {
        for (long fileSize : files.values()) size = Math.addExact(size, fileSize);
        requireCapacity(size, Files.getFileStore(root).getUsableSpace());
      } catch (ArithmeticException exception) {
        throw new IOException("Saved world is too large for backup capacity accounting", exception);
      }
      stage = Files.createTempDirectory(root, ".aeroscapes-stage-");
      Files.writeString(stage.resolve(".owner"), MARKER, StandardOpenOption.CREATE_NEW);
      for (Map.Entry<Path, Long> file : files.entrySet()) {
        Path original = world.resolve(file.getKey());
        Path target = stage.resolve("snapshot").resolve(file.getKey());
        BasicFileAttributes before =
            Files.readAttributes(original, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.isSymbolicLink()) {
          throw new IOException("World file changed type: " + file.getKey());
        }
        Files.createDirectories(target.getParent());
        Files.copy(original, target, StandardCopyOption.COPY_ATTRIBUTES);
        BasicFileAttributes after =
            Files.readAttributes(original, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (before.size() != file.getValue()
            || after.size() != before.size()
            || !before.lastModifiedTime().equals(after.lastModifiedTime())
            || Files.size(target) != before.size()) {
          throw new IOException("World file changed during snapshot: " + file.getKey());
        }
      }
      return new ManagedBackupArchive(
          root,
          stage,
          world.getFileName().toString(),
          worldKey(world),
          files,
          channel,
          acquired,
          publisher);
    } catch (IOException | RuntimeException | Error exception) {
      if (stage != null) {
        try {
          deleteTree(stage);
        } catch (IOException | RuntimeException | Error cleanup) {
          if (exception != cleanup) exception.addSuppressed(cleanup);
        }
      }
      try {
        acquired.release();
      } catch (IOException | RuntimeException | Error cleanup) {
        if (exception != cleanup) exception.addSuppressed(cleanup);
      }
      try {
        channel.close();
      } catch (IOException | RuntimeException | Error cleanup) {
        if (exception != cleanup) exception.addSuppressed(cleanup);
      }
      OWNED_OUTPUTS.remove(root);
      throw exception;
    }
  }

  public Publication publish(int compressionLevel, int copiesPerWorld, long globalSoftLimit)
      throws IOException {
    if (!lock.isValid()) throw new IOException("Backup transaction is already closed");
    if (compressionLevel < -1
        || compressionLevel > 9
        || copiesPerWorld < 1
        || globalSoftLimit < 1) {
      throw new IllegalArgumentException("Invalid backup compression or retention settings");
    }
    Path temporary = staging.resolve("archive.part");
    Map<String, Long> entries = new HashMap<>();
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
      zip.setLevel(compressionLevel);
      zip.setComment(MARKER + worldKey);
      for (Map.Entry<Path, Long> file : manifest.entrySet()) {
        String name = worldName + "/" + file.getKey().toString().replace('\\', '/');
        entries.put(name, file.getValue());
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream input =
            Files.newInputStream(staging.resolve("snapshot").resolve(file.getKey()))) {
          input.transferTo(zip);
        }
        zip.closeEntry();
      }
    }
    verify(temporary, entries, MARKER + worldKey);
    try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
      file.force(true);
    }
    Path destination =
        output.resolve(
            "aeroscapes-"
                + worldKey
                + "-"
                + System.currentTimeMillis()
                + "-"
                + UUID.randomUUID()
                + ".zip");
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Refusing to overwrite an existing backup");
    }
    publisher.publish(temporary, destination);
    List<String> warnings = new ArrayList<>();
    try {
      rotate(output, copiesPerWorld, globalSoftLimit, destination, warnings);
    } catch (IOException | UncheckedIOException exception) {
      warnings.add("Backup is complete; retention scan failed: " + exception.getMessage());
    }
    return new Publication(destination, List.copyOf(warnings));
  }

  static void requireCapacity(long worldBytes, long usableBytes) throws IOException {
    try {
      long required = Math.addExact(Math.multiplyExact(worldBytes, 2), FREE_SPACE_RESERVE);
      if (worldBytes < 0 || usableBytes < required) {
        throw new IOException("Insufficient space for snapshot, archive and reserve");
      }
    } catch (ArithmeticException exception) {
      throw new IOException("Saved world is too large for backup capacity accounting", exception);
    }
  }

  private static void recoverIncompleteStages(Path root) throws IOException {
    try (var paths = Files.list(root)) {
      for (Path path : paths.toList()) {
        Path marker = path.resolve(".owner");
        if (path.getFileName().toString().startsWith(".aeroscapes-stage-")
            && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
            && Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
            && Files.size(marker) == MARKER.length()
            && MARKER.equals(Files.readString(marker))) {
          deleteTree(path);
        }
      }
    }
  }

  static void verify(Path archive, Map<String, Long> expected, String marker) throws IOException {
    Set<String> found = new HashSet<>();
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      if (!marker.equals(zip.getComment())) throw new IOException("Backup marker is missing");
      var entries = zip.entries();
      byte[] buffer = new byte[8192];
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        Long expectedSize = expected.get(entry.getName());
        if (entry.isDirectory() || expectedSize == null || !found.add(entry.getName())) {
          throw new IOException("Unexpected or duplicate ZIP entry: " + entry.getName());
        }
        CRC32 crc = new CRC32();
        long read = 0;
        try (InputStream input = zip.getInputStream(entry)) {
          int count;
          while ((count = input.read(buffer)) != -1) {
            crc.update(buffer, 0, count);
            read += count;
          }
        }
        if (read != expectedSize || read != entry.getSize() || crc.getValue() != entry.getCrc()) {
          throw new IOException("ZIP content verification failed: " + entry.getName());
        }
      }
    }
    if (!found.equals(expected.keySet())) throw new IOException("ZIP is missing snapshot files");
  }

  private static void rotate(
      Path root, int copiesPerWorld, long limit, Path published, List<String> warnings)
      throws IOException {
    List<ExistingArchive> archives = new ArrayList<>();
    try (var paths = Files.list(root)) {
      for (Path path : paths.toList()) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            || !ARCHIVE_NAME.matcher(path.getFileName().toString()).matches()) continue;
        try (ZipFile zip = new ZipFile(path.toFile())) {
          String comment = zip.getComment();
          if (comment != null && comment.matches(Pattern.quote(MARKER) + "[0-9a-f]{16}")) {
            if (!path.equals(published)) verifyExistingArchive(path, zip, comment);
            archives.add(
                new ExistingArchive(
                    path,
                    comment.substring(MARKER.length()),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toMillis()));
          }
        } catch (IOException exception) {
          warnings.add("Unverified archive preserved: " + path.getFileName());
        }
      }
    }
    archives.sort(
        Comparator.comparing((ExistingArchive archive) -> !archive.path().equals(published))
            .thenComparing(Comparator.comparingLong(ExistingArchive::modified).reversed())
            .thenComparing(
                archive -> archive.path().getFileName().toString(), Comparator.reverseOrder()));
    Set<Path> protectedNewest = new HashSet<>();
    protectedNewest.add(published);
    Map<String, Integer> counts = new HashMap<>();
    Set<Path> removed = new HashSet<>();
    for (ExistingArchive archive : archives) {
      int count = counts.merge(archive.worldKey(), 1, Integer::sum);
      if (count == 1) protectedNewest.add(archive.path());
      if (count > copiesPerWorld && !protectedNewest.contains(archive.path())) {
        if (deleteArchive(archive.path(), warnings)) removed.add(archive.path());
      }
    }
    long total = 0;
    for (ExistingArchive archive : archives) {
      if (!removed.contains(archive.path())) total += archive.size();
    }
    for (int index = archives.size() - 1; index >= 0 && total > limit; index--) {
      ExistingArchive archive = archives.get(index);
      if (!removed.contains(archive.path())
          && !protectedNewest.contains(archive.path())
          && deleteArchive(archive.path(), warnings)) {
        total -= archive.size();
      }
    }
    if (total > limit) warnings.add("Newest world copies exceed the soft retention limit");
  }

  private static void verifyExistingArchive(Path path, ZipFile zip, String marker)
      throws IOException {
    Map<String, Long> expected = new HashMap<>();
    var entries = zip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if (entry.isDirectory()
          || entry.getSize() < 0
          || expected.putIfAbsent(entry.getName(), entry.getSize()) != null) {
        throw new IOException("Invalid historical backup entry: " + entry.getName());
      }
    }
    if (expected.isEmpty()) throw new IOException("Historical backup has no snapshot files");
    verify(path, expected, marker);
  }

  private static boolean deleteArchive(Path path, List<String> warnings) {
    try {
      Files.delete(path);
      return true;
    } catch (IOException exception) {
      warnings.add("Old backup retained after deletion failure: " + path.getFileName());
      return false;
    }
  }

  private static String worldKey(Path world) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(world.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
          .substring(0, 16);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime lacks SHA-256", exception);
    }
  }

  private static void deleteTree(Path root) throws IOException {
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path directory, IOException failure)
              throws IOException {
            if (failure != null) throw failure;
            Files.delete(directory);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) return;
    Throwable failure = null;
    try {
      if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) deleteTree(staging);
    } catch (IOException | RuntimeException | Error exception) {
      failure = exception;
    }
    try {
      if (lock.isValid()) lock.release();
    } catch (IOException | RuntimeException | Error exception) {
      if (failure == null) failure = exception;
      else if (failure != exception) failure.addSuppressed(exception);
    }
    try {
      lockChannel.close();
    } catch (IOException | RuntimeException | Error exception) {
      if (failure == null) failure = exception;
      else if (failure != exception) failure.addSuppressed(exception);
    }
    OWNED_OUTPUTS.remove(output);
    if (failure instanceof IOException io) throw io;
    if (failure instanceof RuntimeException runtime) throw runtime;
    if (failure instanceof Error fatal) throw fatal;
  }
}
