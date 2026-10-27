package dev.aeroscapes.regression;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Starts the same natural 81-chunk workload in each disposable benchmark world. */
public final class ChunkPerformanceRegression {
  private ChunkPerformanceRegression() {}

  public static void start(ServerLevel level) {
    if (!level.getServer().isDedicatedServer()
        || level.getSeed() != 1
        || level.dimension() != Level.OVERWORLD) {
      throw new IllegalStateException("Use a disposable dedicated Overworld with seed 1");
    }
    command(level, "jfr start");
    command(level, "chunky world minecraft:overworld");
    command(level, "chunky shape square");
    command(level, "chunky center 12288 12288");
    command(level, "chunky radius 64");
    command(level, "chunky start");
    LogUtils.getLogger()
        .info(
            "Chunk performance workload requested: seed=1 center=12288,12288 radius=64 chunks=81;"
                + " wait for Chunky's task-finished marker before JFR stop, save and shutdown");
  }

  private static void command(ServerLevel level, String command) {
    var server = level.getServer();
    var source = server.createCommandSourceStack().withLevel(level).withPermission(4);
    try {
      int result = server.getCommands().getDispatcher().execute(command, source);
      if (result <= 0) {
        throw new IllegalStateException(
            "Chunk benchmark command returned " + result + ": " + command);
      }
    } catch (CommandSyntaxException exception) {
      throw new IllegalStateException("Chunk benchmark command failed: " + command, exception);
    }
  }
}
