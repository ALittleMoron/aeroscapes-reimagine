package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.visitors.CollectToTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/** Exercises native serialized I/O, including pending writes, rather than a storage mock. */
final class RegionScanRegression {
  private static final ChunkPos ORIGIN = new ChunkPos(0, 0);

  private RegionScanRegression() {}

  static void verify(ServerLevel level) {
    require(level.getServer().isDedicatedServer(), "Region scans require a disposable server");
    try (Directory directory =
        new Directory(Files.createTempDirectory("aeroscapes-region-scan-"))) {
      Path root = directory.path();
      writeAfterScan(root.resolve("missing/region"), false);
      writeAfterScan(root.resolve("empty"), true);
      pendingWrites(root.resolve("pending"));
      cachedFile(root.resolve("cached"));
      symlinks(root.resolve("symlinks"));
      nativeFallbacks(root.resolve("fallbacks"));
      eviction(root.resolve("eviction"));
    } catch (IOException exception) {
      throw new IllegalStateException("Region scan fixture I/O failed", exception);
    }
    LogUtils.getLogger()
        .info(
            "Region scan regression passed: pending, stored, cached, absent and native fallbacks");
  }

  private static void writeAfterScan(Path folder, boolean empty) throws IOException {
    if (empty) {
      Files.createDirectories(folder);
      Files.createFile(folder.resolve("r.0.0.mca"));
    }
    CompoundTag expected = marker("after-empty-scan");
    try (Worker worker = new Worker(folder)) {
      require(scan(worker, ORIGIN) == null, "Empty region returned data");
      if (empty) require(Files.size(folder.resolve("r.0.0.mca")) == 0, "Empty scan wrote a region");
      else require(!Files.exists(folder), "Missing scan created directories or a region");
      complete(worker.store(ORIGIN, expected));
      complete(worker.synchronize(true));
      require(expected.equals(scan(worker, ORIGIN)), "Write after empty scan was hidden");
    }
    try (Worker reopened = new Worker(folder)) {
      require(expected.equals(scan(reopened, ORIGIN)), "Reopened region changed stored data");
    }
  }

  private static void pendingWrites(Path folder) throws IOException {
    ChunkPos pending = new ChunkPos(64, 0);
    try (Worker worker = new Worker(folder)) {
      complete(worker.store(ORIGIN, marker("anchor")));
      CompoundTag data = marker("pending");
      require(data.equals(scanPending(worker, pending, data)), "Pending write was bypassed");
      complete(worker.synchronize(true));
      require(data.equals(scan(worker, pending)), "Flushed pending write changed");
      require(
          scanPending(worker, pending, null) == null, "Pending deletion returned old disk data");
      complete(worker.synchronize(true));
      require(scan(worker, pending) == null, "Flushed deletion returned old disk data");
      ChunkPos neighbour = new ChunkPos(65, 0);
      CompoundTag neighbourData = marker("same-region-neighbour");
      require(
          neighbourData.equals(scanPending(worker, neighbour, neighbourData)),
          "Pending neighbouring chunk was bypassed");
      complete(worker.synchronize(true));
      require(neighbourData.equals(scan(worker, neighbour)), "Stored neighbour changed");
    }
  }

  @Nullable
  private static Tag scanPending(Worker worker, ChunkPos chunk, @Nullable CompoundTag data) {
    BlockingCollector barrier = new BlockingCollector();
    CompletableFuture<Void> blocked = worker.scanChunk(ORIGIN, barrier);
    try {
      await(barrier.entered);
      // Both foreground operations are queued before releasing the single mailbox thread.
      // The scan therefore runs before the background store, including a null deletion.
      CompletableFuture<Void> stored = worker.store(chunk, data);
      CollectToTag visitor = new CollectToTag();
      CompletableFuture<Void> scanned = worker.scanChunk(chunk, visitor);
      barrier.release.countDown();
      complete(blocked);
      complete(scanned);
      complete(stored);
      return visitor.getResult();
    } finally {
      barrier.release.countDown();
    }
  }

  private static void cachedFile(Path folder) throws IOException {
    CompoundTag expected = marker("cached-open-file");
    try (Worker worker = new Worker(folder)) {
      complete(worker.store(ORIGIN, expected));
      complete(worker.synchronize(true));
      Files.move(folder.resolve("r.0.0.mca"), folder.resolve("cached-original.mca"));
      Files.createFile(folder.resolve("r.0.0.mca"));
      require(
          expected.equals(scan(worker, ORIGIN)), "Filesystem check overrode a cached open region");
    }
  }

  private static void symlinks(Path root) throws IOException {
    Path folder = Files.createDirectories(root.resolve("zero-link"));
    Path target = Files.createFile(folder.resolve("target.mca"));
    Path link = Files.createSymbolicLink(folder.resolve("r.0.0.mca"), target);
    try (Worker worker = new Worker(folder)) {
      require(scan(worker, ORIGIN) == null, "Empty symlink returned data");
      Files.delete(link);
      Files.createDirectory(link);
      require(
          scan(worker, ORIGIN) == null, "Symlink scan did not retain native cached-file authority");
    }
    Path dangling = Files.createDirectories(root.resolve("dangling-link"));
    Path missingTarget = dangling.resolve("target.mca");
    Files.createSymbolicLink(dangling.resolve("r.0.0.mca"), missingTarget);
    try (Worker worker = new Worker(dangling)) {
      require(scan(worker, ORIGIN) == null, "Dangling symlink unexpectedly contained data");
      require(Files.isRegularFile(missingTarget), "Dangling file symlink bypassed native CREATE");
    }
    Path realFolder = Files.createDirectories(root.resolve("real-folder"));
    Path parentLink = Files.createSymbolicLink(root.resolve("parent-link"), realFolder);
    try (Worker worker = new Worker(parentLink)) {
      CompoundTag expected = marker("linked-region-folder");
      complete(worker.store(ORIGIN, expected));
      require(expected.equals(scan(worker, ORIGIN)), "Linked region folder lost data");
    }
    Path brokenParent =
        Files.createSymbolicLink(root.resolve("broken-parent"), root.resolve("absent"));
    try (Worker worker = new Worker(brokenParent)) {
      fails(worker.scanChunk(ORIGIN, new CollectToTag()));
    }
  }

  private static void nativeFallbacks(Path root) throws IOException {
    Path truncated = Files.createDirectories(root.resolve("truncated"));
    Path truncatedFile = truncated.resolve("r.0.0.mca");
    Files.write(truncatedFile, new byte[16]);
    try (Worker worker = new Worker(truncated)) {
      require(scan(worker, ORIGIN) == null, "Truncated zero header contained a chunk");
    }
    require(Files.size(truncatedFile) > 16, "Nonzero truncated file bypassed native open/close");
    Path directory = Files.createDirectories(root.resolve("directory"));
    Files.createDirectory(directory.resolve("r.0.0.mca"));
    try (Worker worker = new Worker(directory)) {
      fails(worker.scanChunk(ORIGIN, new CollectToTag()));
    }
    Path denied = Files.createDirectories(root.resolve("denied"));
    Path deniedFile = Files.createFile(denied.resolve("r.0.0.mca"));
    Files.setPosixFilePermissions(deniedFile, PosixFilePermissions.fromString("r--------"));
    try (Worker worker = new Worker(denied)) {
      require(
          !Files.isWritable(deniedFile), "Permission fixture requires an unprivileged POSIX user");
      fails(worker.scanChunk(ORIGIN, new CollectToTag()));
    } finally {
      Files.setPosixFilePermissions(deniedFile, PosixFilePermissions.fromString("rw-------"));
    }
  }

  private static void eviction(Path folder) throws IOException {
    CompoundTag expected = marker("evicted-region");
    try (Worker worker = new Worker(folder)) {
      complete(worker.store(ORIGIN, expected));
      // The pinned native region LRU holds 256 entries; force the original out with real writes.
      for (int region = 1; region <= 256; region++) {
        complete(worker.store(new ChunkPos(region * 32, 32), marker("other-region")));
      }
      complete(worker.synchronize(true));
      require(expected.equals(scan(worker, ORIGIN)), "Evicted stored region lost NBT");
    }
  }

  private static CompoundTag marker(String value) {
    CompoundTag tag = new CompoundTag();
    tag.putString("aeroscapes_fixture", value);
    return tag;
  }

  @Nullable
  private static Tag scan(Worker worker, ChunkPos chunk) {
    CollectToTag visitor = new CollectToTag();
    complete(worker.scanChunk(chunk, visitor));
    return visitor.getResult();
  }

  private static void complete(CompletableFuture<?> operation) {
    try {
      operation.get(10, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Region scan interrupted", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new IllegalStateException("Region scan did not complete", exception);
    }
  }

  private static void fails(CompletableFuture<?> operation) {
    try {
      operation.get(10, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Native failure fixture interrupted", exception);
    } catch (ExecutionException exception) {
      require(
          exception.getCause() instanceof IOException, "Unexpected native failure " + exception);
      return;
    } catch (TimeoutException exception) {
      throw new IllegalStateException("Native failure fixture timed out", exception);
    }
    throw new IllegalStateException("Native I/O failure was hidden");
  }

  private static void await(CountDownLatch latch) {
    try {
      require(latch.await(10, TimeUnit.SECONDS), "Native mailbox barrier timed out");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Native mailbox barrier interrupted", exception);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class Worker extends IOWorker {
    private Worker(Path folder) {
      super(new RegionStorageInfo("aeroscapes_regression", Level.OVERWORLD, "chunk"), folder, true);
    }
  }

  private static final class BlockingCollector extends CollectToTag {
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    public StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
      entered.countDown();
      await(release);
      return super.visitRootEntry(type);
    }
  }

  private record Directory(Path path) implements AutoCloseable {
    @Override
    public void close() throws IOException {
      try (var entries = Files.walk(path)) {
        for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) Files.delete(entry);
      }
    }
  }
}
