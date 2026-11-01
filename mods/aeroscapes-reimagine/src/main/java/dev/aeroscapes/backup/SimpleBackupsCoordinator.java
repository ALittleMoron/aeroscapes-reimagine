package dev.aeroscapes.backup;

import de.melanx.simplebackups.BackupData;
import de.melanx.simplebackups.SimpleBackups;
import de.melanx.simplebackups.compat.CherishedWorldsCompat;
import de.melanx.simplebackups.config.BackupType;
import de.melanx.simplebackups.config.CommonConfig;
import de.melanx.simplebackups.config.ServerConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Exact Simple Backups 4.0.21 / Sable 2.0.6 integration; invoke only on the server thread. */
public final class SimpleBackupsCoordinator {
  private static final Map<MinecraftServer, State> SERVERS = new WeakHashMap<>();

  private static final class State {
    private final AtomicBoolean running = new AtomicBoolean();
    private List<String> lastWarnings = List.of();
  }

  private SimpleBackupsCoordinator() {}

  public static void tick(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel level)
        || level != level.getServer().overworld()
        || level.getGameTime() % 20 != 0) return;
    MinecraftServer server = level.getServer();
    if (!eligible(server)
        || (server.getPlayerList().getPlayers().isEmpty() && !CommonConfig.doNoPlayerBackups()))
      return;
    long interval = Math.max(1L, CommonConfig.getTimer() / 50L);
    if (BackupScheduleData.get(server).advance(interval)) start(server, false);
  }

  public static boolean scheduled(MinecraftServer server) {
    // The native scheduler is cancelled; external scheduled requests obey pause/enable/favorite
    // policy.
    return eligible(server)
        && BackupScheduleData.get(server).isDue(Math.max(1L, CommonConfig.getTimer() / 50L))
        && start(server, false);
  }

  public static void manual(MinecraftServer server, boolean quiet) {
    State state = SERVERS.get(server);
    if (state != null && state.running.get()) {
      if (!quiet) notify(server, Component.translatable("aeroscapes_reimagine.backup.busy"));
      return;
    }
    start(server, quiet);
  }

  private static boolean eligible(MinecraftServer server) {
    return CommonConfig.isEnabled()
        && !BackupData.get(server).isPaused()
        && (!CommonConfig.onlyFavorites()
            || !CherishedWorldsCompat.isLoaded()
            || CherishedWorldsCompat.isFavorite(
                server
                    .getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath()
                    .normalize()
                    .getFileName()
                    .toString()));
  }

  private static boolean start(MinecraftServer server, boolean quiet) {
    if (!server.isSameThread())
      throw new IllegalStateException("Backup trigger is off the server thread");
    State state = SERVERS.computeIfAbsent(server, ignored -> new State());
    AtomicBoolean running = state.running;
    if (!running.compareAndSet(false, true)) return false;
    ManagedBackupArchive snapshot;
    int compression;
    int copies;
    long limit;
    try {
      if (CommonConfig.backupType() != BackupType.FULL_BACKUPS || !CommonConfig.saveAll()) {
        throw new IOException("The verified adapter requires FULL_BACKUPS and saveAll=true");
      }
      compression = CommonConfig.getCompressionLevel();
      copies = CommonConfig.getBackupsToKeep();
      limit = CommonConfig.getMaxDiskSize();
      Path output = CommonConfig.getOutputPath(null);
      Predicate<Path> include = inclusionPolicy();
      server.saveEverything(true, true, true);
      for (ServerLevel level : server.getAllLevels()) {
        // Sable's save hook runs before saveLevelData; this final flush propagates I/O failure.
        SubLevelContainer.getContainer(level).getHoldingChunkMap().getStorage().flush();
      }
      snapshot =
          ManagedBackupArchive.capture(server.getWorldPath(LevelResource.ROOT), output, include);
    } catch (IOException | RuntimeException | Error exception) {
      running.set(false);
      BackupScheduleData.get(server).failed();
      failure(server, exception);
      if (exception instanceof Error fatal) throw fatal;
      return false;
    }
    try {
      Thread worker =
          new Thread(
              () -> archive(server, snapshot, compression, copies, limit, quiet, state),
              "Aeroscapes verified backup");
      worker.setDaemon(true);
      worker.start();
    } catch (RuntimeException | Error exception) {
      try {
        snapshot.close();
      } catch (IOException | RuntimeException | Error cleanup) {
        if (exception != cleanup) exception.addSuppressed(cleanup);
      }
      running.set(false);
      BackupScheduleData.get(server).failed();
      failure(server, exception);
      if (exception instanceof Error fatal) throw fatal;
      return false;
    }
    return true;
  }

  private static Predicate<Path> inclusionPolicy() {
    return WorldBackupFilePolicy.create(
        CommonConfig.getIgnoredPaths(),
        CommonConfig.getIgnoredFiles(),
        CommonConfig.getIgnoredFilesRegex());
  }

  private static void archive(
      MinecraftServer server,
      ManagedBackupArchive snapshot,
      int compression,
      int copies,
      long limit,
      boolean quiet,
      State state) {
    boolean handedOff = false;
    try {
      ManagedBackupArchive.Publication published = null;
      Throwable failure = null;
      try {
        published = snapshot.publish(compression, copies, limit);
      } catch (IOException | RuntimeException | Error exception) {
        failure = exception;
      } finally {
        try {
          snapshot.close();
        } catch (IOException | RuntimeException | Error cleanup) {
          if (published != null) {
            SimpleBackups.LOGGER.warn("Complete backup retained; staging cleanup failed", cleanup);
          }
          if (failure == null) failure = cleanup;
          else if (failure != cleanup) failure.addSuppressed(cleanup);
        }
      }
      ManagedBackupArchive.Publication result = published;
      Throwable error = failure;
      try {
        server.executeIfPossible(
            () -> {
              try {
                // Shutdown can race submission; native execute may otherwise run on the caller.
                if (!server.isSameThread()) {
                  SimpleBackups.LOGGER.warn(
                      "Backup completed after the server thread stopped; published={}",
                      result != null);
                  return;
                }
                if (result != null) {
                  BackupScheduleData.get(server).completed();
                  BackupData.get(server)
                      .updateSaveTime(
                          CommonConfig.useTickCounter()
                              ? server.overworld().getGameTime()
                              : System.currentTimeMillis());
                  BackupData.get(server)
                      .updateFullBackupTime(BackupData.get(server).getLastSaved());
                  SimpleBackups.LOGGER.info("Verified backup published: {}", result.archive());
                  for (String warning : result.retentionWarnings())
                    SimpleBackups.LOGGER.warn(warning);
                  if (!quiet && CommonConfig.sendMessages()) {
                    notify(server, Component.translatable("aeroscapes_reimagine.backup.complete"));
                  }
                  List<String> warnings = result.retentionWarnings().stream().sorted().toList();
                  if (!warnings.isEmpty() && !warnings.equals(state.lastWarnings)) {
                    notify(
                        server,
                        Component.translatable("aeroscapes_reimagine.backup.retention_warning")
                            .withStyle(ChatFormatting.YELLOW));
                  }
                  state.lastWarnings = warnings;
                } else {
                  BackupScheduleData.get(server).failed();
                  failure(
                      server, error == null ? new IOException("Backup did not publish") : error);
                }
              } finally {
                state.running.set(false);
              }
            });
        handedOff = true;
      } catch (RejectedExecutionException exception) {
        state.running.set(false);
        SimpleBackups.LOGGER.warn(
            "Backup ended after server executor stopped; published={}", result != null, exception);
      }
      if (error instanceof Error fatal) throw fatal;
    } finally {
      if (!handedOff) state.running.set(false);
    }
  }

  private static void failure(MinecraftServer server, Throwable exception) {
    SimpleBackups.LOGGER.error(
        "Verified backup failed; prior complete copies were preserved", exception);
    notify(
        server,
        Component.translatable("aeroscapes_reimagine.backup.failed").withStyle(ChatFormatting.RED));
  }

  private static void notify(MinecraftServer server, Component message) {
    for (var player : server.getPlayerList().getPlayers()) {
      if (ServerConfig.messagesForEveryone() || player.hasPermissions(2))
        player.sendSystemMessage(message);
    }
  }
}
