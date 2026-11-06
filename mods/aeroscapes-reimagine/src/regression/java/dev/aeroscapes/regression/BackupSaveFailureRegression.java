package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import de.melanx.simplebackups.BackupData;
import de.melanx.simplebackups.config.CommonConfig;
import dev.aeroscapes.backup.BackupSaveFailures;
import dev.aeroscapes.backup.SimpleBackupsCoordinator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Expected native I/O errors must prevent publication and rotation on a disposable server. */
public final class BackupSaveFailureRegression {
  private static final String ID = "aeroscapes_regression_backup_save_failure";
  private final ServerLevel level;
  private final MinecraftServer server;
  private final String mode;
  private final Path world;
  private final Consumer<ServerTickEvent.Post> listener = this::tick;
  private Map<Path, byte[]> baseline;
  private long savedTime;
  private int ticks;
  private int stableTicks;
  private int finishedAt;

  private static final class ProbeData extends SavedData {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("value", 42);
      return tag;
    }
  }

  private BackupSaveFailureRegression(ServerLevel level, String mode) {
    this.level = level;
    server = level.getServer();
    this.mode = mode;
    world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    require(
        server.isDedicatedServer() && server.getPlayerCount() == 0, "Empty disposable server only");
    require(
        mode.equals("before") || mode.equals("during") || mode.equals("level"),
        "Expected before/during/level save mode");
    require(world.toString().contains("/.runtime/"), "Fixture world must be disposable");
    Path output = CommonConfig.getOutputPath(null).toAbsolutePath().normalize();
    require(
        output.startsWith(world.getParent()), "Fixture backups must stay inside its installation");
  }

  public static void start(ServerLevel level, String mode) {
    var fixture = new BackupSaveFailureRegression(level, mode);
    NeoForge.EVENT_BUS.addListener(fixture.listener);
  }

  private void tick(ServerTickEvent.Post event) {
    if (event.getServer() != server) return;
    require(++ticks < 1200, "Backup save-failure fixture timed out");
    try {
      if (ticks == 1) {
        require(archives().isEmpty(), "Use a fresh backup output");
        SimpleBackupsCoordinator.manual(server, true);
        return;
      }
      if (finishedAt != 0) {
        unchanged();
        if (ticks < finishedAt + 40) return;
        NeoForge.EVENT_BUS.unregister(listener);
        LogUtils.getLogger()
            .info(
                "Backup save failure regression passed: mode={} nativeFailure=true repairedWrite=true publication=false rotation=false; expected error logs remain visible",
                mode);
        return;
      }
      var current = archives();
      if (current.size() != 1 || ++stableTicks < 5) return;
      baseline = current;
      savedTime = BackupData.get(server).getLastSaved();
      failure();
      finishedAt = ticks;
    } catch (IOException exception) {
      throw new IllegalStateException("Backup save-failure fixture failed", exception);
    }
  }

  private void failure() throws IOException {
    if (mode.equals("level")) {
      levelFailure();
      return;
    }
    var data = new ProbeData();
    level.getDataStorage().set(ID, data);
    data.setDirty();
    Path target = world.resolve("data").resolve(ID + ".dat");
    Path obstruction = target.resolve("nonempty-directory");
    Files.createDirectories(obstruction);
    try {
      if (mode.equals("before")) {
        data.save(target.toFile(), level.registryAccess());
        IOUtilities.waitUntilIOWorkerComplete();
        blocked();
      }
      SimpleBackupsCoordinator.manual(server, true);
      IOUtilities.waitUntilIOWorkerComplete();
      require(!data.isDirty(), "Native failed write must reproduce its cleared dirty flag");
      blocked();
      unchanged();
    } finally {
      Files.delete(obstruction);
      Files.delete(target);
    }
    data.setDirty();
    data.save(target.toFile(), level.registryAccess());
    IOUtilities.waitUntilIOWorkerComplete();
    CompoundTag saved = NbtIo.readCompressed(target, NbtAccounter.create(1024 * 1024));
    require(
        saved.getCompound("data").getInt("value") == 42,
        "The repaired native write did not persist");
    blocked();
    SimpleBackupsCoordinator.manual(server, true);
    unchanged();
  }

  private void levelFailure() throws IOException {
    var permissions = Files.getPosixFilePermissions(world);
    var readOnly = new java.util.HashSet<>(permissions);
    readOnly.removeAll(
        java.util.Set.of(
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.OTHERS_WRITE));
    Files.setPosixFilePermissions(world, readOnly);
    try {
      SimpleBackupsCoordinator.manual(server, true);
      blocked();
      unchanged();
    } finally {
      Files.setPosixFilePermissions(world, permissions);
    }
    server.saveEverything(true, true, true);
    require(Files.isRegularFile(world.resolve("level.dat")), "Restored world metadata is missing");
    blocked();
    SimpleBackupsCoordinator.manual(server, true);
    unchanged();
  }

  private void blocked() {
    try {
      BackupSaveFailures.requireHealthy(world);
      throw new IllegalStateException("Swallowed native save error was not latched");
    } catch (IOException expected) {
      require(expected.getCause() != null, "Original native failure must be preserved");
    }
  }

  private void unchanged() throws IOException {
    Map<Path, byte[]> current = archives();
    require(
        current.keySet().equals(baseline.keySet()), "Failed backup published or rotated archives");
    for (var entry : baseline.entrySet())
      require(
          MessageDigest.isEqual(entry.getValue(), current.get(entry.getKey())),
          "Previous backup bytes changed");
    require(
        BackupData.get(server).getLastSaved() == savedTime,
        "Failed backup advanced its success timestamp");
  }

  private Map<Path, byte[]> archives() throws IOException {
    Path output = CommonConfig.getOutputPath(null);
    if (!Files.isDirectory(output)) return Map.of();
    Map<Path, byte[]> result = new HashMap<>();
    try (var files = Files.list(output)) {
      for (Path path :
          files.filter(file -> file.getFileName().toString().endsWith(".zip")).toList())
        result.put(path, Files.readAllBytes(path));
    }
    return result;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
