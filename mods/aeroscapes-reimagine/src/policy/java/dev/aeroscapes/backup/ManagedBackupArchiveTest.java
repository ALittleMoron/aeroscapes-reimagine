package dev.aeroscapes.backup;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Pure filesystem behavior cases; root may add this to the existing policy JavaExec task. */
public final class ManagedBackupArchiveTest {
  private ManagedBackupArchiveTest() {}

  public static void main(String[] arguments) throws Exception {
    Path test = Files.createTempDirectory("aeroscapes-backup-test-");
    try {
      Path world = test.resolve("world");
      Path output = test.resolve("backups");
      Files.createDirectories(world.resolve("sublevels"));
      Files.writeString(world.resolve("level.dat"), "first saved world");
      Files.writeString(world.resolve("sublevels/ship.data"), "ship cargo at capture");
      Path first;
      try (var transaction = ManagedBackupArchive.capture(world, output, relative -> true)) {
        Files.writeString(world.resolve("level.dat"), "later game mutation");
        first = transaction.publish(1, 5, 2L * 1024 * 1024 * 1024).archive();
      }
      try (var zip = new java.util.zip.ZipFile(first.toFile())) {
        String captured =
            new String(
                zip.getInputStream(zip.getEntry("world/level.dat")).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        require(captured.equals("first saved world"), "Worker archived changing live world");
        require(zip.getEntry("world/sublevels/ship.data") != null, "Ship file missing");
      }
      publicationFailurePreservesPrevious(world, output, first);
      interruptedArchivePreservesPrevious(world, output);
      singleWriter(world, output);
      fatalCaptureReleasesOwnership(world, output);
      capacityFailures();
      crcFailure(test.resolve("corrupt.zip"));
      corruptHistoricalNewestPreservesValidCopy(test);
      malformedArchiveCommentDoesNotFailPublication(test);
      globalRetentionPreservesLatestWorlds(test, world, output);
      equalFileTimesKeepNewest(test);
      incompleteStageRecovery(world, output);
    } finally {
      try (var files = Files.walk(test)) {
        for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList())
          Files.delete(path);
      }
    }
  }

  private static void publicationFailurePreservesPrevious(Path world, Path output, Path first)
      throws Exception {
    Set<Path> before = archives(output);
    try (var transaction =
        ManagedBackupArchive.capture(
            world,
            output,
            relative -> true,
            (temporary, destination) -> {
              throw new AtomicMoveNotSupportedException(
                  temporary.toString(), destination.toString(), "test");
            })) {
      expectIOException(() -> transaction.publish(1, 1, 1));
    }
    require(
        Files.exists(first) && archives(output).equals(before), "Failed attempt deleted history");
    try (var transaction = ManagedBackupArchive.capture(world, output, relative -> true)) {
      transaction.publish(1, 1, Long.MAX_VALUE);
    }
    require(archives(output).size() == 1, "Rotation did not occur after a successful publication");
  }

  private static void singleWriter(Path world, Path output) throws Exception {
    try (var first = ManagedBackupArchive.capture(world, output, relative -> true)) {
      expectIOException(
          () -> {
            try (var second = ManagedBackupArchive.capture(world, output, relative -> true)) {
              throw new AssertionError("Second output owner was admitted: " + second);
            }
          });
      first.publish(1, 5, Long.MAX_VALUE);
    }
  }

  private static void interruptedArchivePreservesPrevious(Path world, Path output)
      throws Exception {
    Set<Path> before = archives(output);
    try (var transaction = ManagedBackupArchive.capture(world, output, relative -> true)) {
      Path captured;
      try (var files = Files.walk(output)) {
        captured =
            files
                .filter(path -> path.getFileName().toString().equals("ship.data"))
                .findFirst()
                .orElseThrow();
      }
      Files.delete(captured);
      expectIOException(() -> transaction.publish(1, 1, 1));
    }
    require(archives(output).equals(before), "Incomplete ZIP appeared complete or deleted history");
    try (var next = ManagedBackupArchive.capture(world, output, relative -> true)) {
      next.publish(1, 5, Long.MAX_VALUE);
    }
  }

  private static void fatalCaptureReleasesOwnership(Path world, Path output) throws Exception {
    AssertionError expected = new AssertionError("Injected file selection failure");
    try {
      ManagedBackupArchive.capture(
          world,
          output,
          relative -> {
            throw expected;
          });
      throw new AssertionError("Fatal capture should propagate its original error");
    } catch (AssertionError failure) {
      require(failure == expected, "The original fatal error is preserved");
    }
    try (var next = ManagedBackupArchive.capture(world, output, relative -> true)) {
      require(
          Files.exists(next.publish(1, 5, Long.MAX_VALUE).archive()),
          "A later backup can acquire ownership after fatal capture cleanup");
    }
  }

  private static void capacityFailures() throws Exception {
    expectIOException(() -> ManagedBackupArchive.requireCapacity(1024, 1024));
    expectIOException(() -> ManagedBackupArchive.requireCapacity(Long.MAX_VALUE, Long.MAX_VALUE));
    ManagedBackupArchive.requireCapacity(1024, 128L * 1024 * 1024 + 2048);
  }

  private static void crcFailure(Path archive) throws Exception {
    byte[] content = "unique backup CRC payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    CRC32 crc = new CRC32();
    crc.update(content);
    String marker = "aeroscapes_reimagine:full-backup:1:test";
    try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      zip.setComment(marker);
      ZipEntry entry = new ZipEntry("world/level.dat");
      entry.setMethod(ZipEntry.STORED);
      entry.setSize(content.length);
      entry.setCompressedSize(content.length);
      entry.setCrc(crc.getValue());
      zip.putNextEntry(entry);
      zip.write(content);
      zip.closeEntry();
    }
    byte[] corrupt = Files.readAllBytes(archive);
    int offset = -1;
    for (int index = 0; index <= corrupt.length - content.length; index++) {
      if (Arrays.equals(Arrays.copyOfRange(corrupt, index, index + content.length), content)) {
        offset = index;
        break;
      }
    }
    require(offset >= 0, "Test ZIP payload was not found");
    corrupt[offset] ^= 1;
    Files.write(archive, corrupt);
    expectIOException(
        () ->
            ManagedBackupArchive.verify(
                archive, Map.of("world/level.dat", (long) content.length), marker));
  }

  private static void corruptHistoricalNewestPreservesValidCopy(Path test) throws Exception {
    Path fixture = Files.createDirectory(test.resolve("historical-crc"));
    Path firstWorld = Files.createDirectory(fixture.resolve("first-world"));
    Path secondWorld = Files.createDirectory(fixture.resolve("second-world"));
    Path output = fixture.resolve("backups");
    byte[] payload =
        "historical-archive-unique-CRC-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(firstWorld.resolve("level.dat"), payload);
    Files.writeString(secondWorld.resolve("level.dat"), "another world");
    Path valid;
    Path corrupt;
    try (var snapshot = ManagedBackupArchive.capture(firstWorld, output, relative -> true)) {
      valid = snapshot.publish(0, 5, Long.MAX_VALUE).archive();
    }
    try (var snapshot = ManagedBackupArchive.capture(firstWorld, output, relative -> true)) {
      corrupt = snapshot.publish(0, 5, Long.MAX_VALUE).archive();
    }
    byte[] bytes = Files.readAllBytes(corrupt);
    int offset = -1;
    for (int index = 0; index <= bytes.length - payload.length; index++) {
      if (Arrays.equals(Arrays.copyOfRange(bytes, index, index + payload.length), payload)) {
        offset = index;
        break;
      }
    }
    require(offset >= 0, "Uncompressed historical ZIP payload is present");
    bytes[offset] ^= 1;
    Files.write(corrupt, bytes);
    Files.setLastModifiedTime(valid, java.nio.file.attribute.FileTime.fromMillis(1));
    Files.setLastModifiedTime(corrupt, java.nio.file.attribute.FileTime.fromMillis(2));
    String marker;
    try (var zip = new java.util.zip.ZipFile(valid.toFile())) {
      marker = zip.getComment();
    }
    try (var zip = new java.util.zip.ZipFile(corrupt.toFile())) {
      require(
          marker.equals(zip.getComment()), "Corrupt payload retains a readable ownership marker");
    }
    expectIOException(
        () ->
            ManagedBackupArchive.verify(
                corrupt, Map.of("first-world/level.dat", (long) payload.length), marker));
    ManagedBackupArchive.Publication publication;
    try (var snapshot = ManagedBackupArchive.capture(secondWorld, output, relative -> true)) {
      publication = snapshot.publish(1, 5, 1);
    }
    require(Files.exists(valid), "Corrupt newest ZIP must not displace a world's valid older copy");
    ManagedBackupArchive.verify(
        valid, Map.of("first-world/level.dat", (long) payload.length), marker);
    require(Files.exists(publication.archive()), "The newly published world remains protected");
    require(Arrays.equals(Files.readAllBytes(corrupt), bytes), "Unverified archive is preserved");
    require(
        publication.retentionWarnings().stream()
            .anyMatch(warning -> warning.contains(corrupt.getFileName().toString())),
        "Corrupt historical archive produces a retention warning");
  }

  private static void malformedArchiveCommentDoesNotFailPublication(Path test) throws Exception {
    Path fixture = Files.createDirectory(test.resolve("malformed-comment"));
    Path world = Files.createDirectory(fixture.resolve("world"));
    Path output = fixture.resolve("backups");
    Files.writeString(world.resolve("level.dat"), "saved world");
    Path malformed;
    try (var snapshot = ManagedBackupArchive.capture(world, output, relative -> true)) {
      malformed = snapshot.publish(1, 5, Long.MAX_VALUE).archive();
    }
    byte[] bytes = Files.readAllBytes(malformed);
    bytes[bytes.length - 1] = (byte) 0xff;
    Files.write(malformed, bytes);
    try (var snapshot = ManagedBackupArchive.capture(world, output, relative -> true)) {
      var publication = snapshot.publish(1, 5, 1);
      require(Files.exists(publication.archive()), "A complete new archive remains published");
      require(Files.exists(malformed), "Malformed historical input remains untouched");
      require(
          Arrays.equals(Files.readAllBytes(malformed), bytes),
          "Malformed historical metadata is preserved without blocking a new publication");
    }
  }

  private static void globalRetentionPreservesLatestWorlds(Path test, Path world, Path output)
      throws Exception {
    Path second = test.resolve("second-world");
    Files.createDirectories(second);
    Files.writeString(second.resolve("level.dat"), "second world saved state");
    Path unrelated = output.resolve("owner-copy.zip");
    Files.writeString(unrelated, "owner file");
    for (Path current : List.of(world, second, world, second)) {
      try (var transaction = ManagedBackupArchive.capture(current, output, relative -> true)) {
        transaction.publish(1, 5, 1);
      }
    }
    require(
        archives(output).size() == 3,
        "Soft cap should keep each world's newest copy and owner file");
    require(Files.readString(unrelated).equals("owner file"), "Unrelated file changed");
  }

  private static void equalFileTimesKeepNewest(Path test) throws Exception {
    Path fixture = Files.createDirectory(test.resolve("equal-times"));
    Path firstWorld = Files.createDirectory(fixture.resolve("first-world"));
    Path secondWorld = Files.createDirectory(fixture.resolve("second-world"));
    Path output = fixture.resolve("backups");
    Files.writeString(firstWorld.resolve("level.dat"), "first world");
    Files.writeString(secondWorld.resolve("level.dat"), "second world");
    Path newest = null;
    for (String timestamp : List.of("1000000000000", "2000000000000")) {
      try (var transaction = ManagedBackupArchive.capture(firstWorld, output, relative -> true)) {
        Path published = transaction.publish(1, 5, Long.MAX_VALUE).archive();
        String[] parts = published.getFileName().toString().split("-", 4);
        Path renamed = output.resolve(parts[0] + "-" + parts[1] + "-" + timestamp + "-" + parts[3]);
        Files.move(published, renamed);
        Files.setLastModifiedTime(renamed, java.nio.file.attribute.FileTime.fromMillis(1));
        newest = renamed;
      }
    }
    try (var transaction = ManagedBackupArchive.capture(secondWorld, output, relative -> true)) {
      transaction.publish(1, 5, 1);
    }
    require(
        newest != null && Files.exists(newest),
        "Equal filesystem times retain the newer encoded backup timestamp");
  }

  private static void incompleteStageRecovery(Path world, Path output) throws Exception {
    Path old = Files.createDirectory(output.resolve(".aeroscapes-stage-crash-test"));
    Files.writeString(old.resolve(".owner"), "aeroscapes_reimagine:full-backup:1:");
    Files.writeString(old.resolve("archive.part"), "interrupted incomplete write");
    Set<Path> before = archives(output);
    try (var recovered = ManagedBackupArchive.capture(world, output, relative -> true)) {
      require(!Files.exists(old), "Stale owned staging was not recovered under output lock");
      before.add(recovered.publish(1, Integer.MAX_VALUE, Long.MAX_VALUE).archive());
    }
    require(archives(output).equals(before), "Recovery changed published history");
  }

  private static Set<Path> archives(Path output) throws IOException {
    try (var files = Files.list(output)) {
      return files
          .filter(path -> path.getFileName().toString().endsWith(".zip"))
          .collect(java.util.stream.Collectors.toSet());
    }
  }

  @FunctionalInterface
  private interface IoAction {
    void run() throws Exception;
  }

  private static void expectIOException(IoAction action) throws Exception {
    try {
      action.run();
    } catch (IOException expected) {
      return;
    }
    throw new AssertionError("Expected IOException");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
