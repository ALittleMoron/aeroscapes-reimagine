package dev.aeroscapes.regression;

import com.ishland.c2me.opts.accel.opencl.common.compiler.OpenCLCGen;
import com.ishland.c2me.opts.accel.opencl.common.ducks.MinecraftServerExtension;
import com.ishland.c2me.opts.accel.opencl.common.ducks.TACSExtension;
import com.ishland.c2me.opts.accel.opencl.common.gen.CLServerWorldContext;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.regression.opencl.OpenClBatchEvidence;
import dev.aeroscapes.regression.opencl.mixin.OpenClDeviceEvidenceAccess;
import dev.aeroscapes.regression.opencl.mixin.OpenClGlobalEvidenceAccess;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Starts the existing workload only after every current GPU world has compiled programs. */
public final class OpenClPerformanceRegression {
  private final ServerLevel level;
  private final Runnable workload;
  private final long deadline = System.nanoTime() + 180_000_000_000L;
  private Set<String> previousDimensions = Set.of();
  private Set<CLServerWorldContext> previousContexts = Set.of();
  private boolean previousReady;
  private boolean started;

  private OpenClPerformanceRegression(ServerLevel level, Runnable workload) {
    this.level = level;
    this.workload = workload;
  }

  public static void start(ServerLevel level) {
    start(level, () -> ChunkPerformanceRegression.start(level));
  }

  public static void start(ServerLevel level, Runnable workload) {
    OpenClPerformanceRegression fixture = new OpenClPerformanceRegression(level, workload);
    NeoForge.EVENT_BUS.addListener(fixture::tick);
    NeoForge.EVENT_BUS.addListener(fixture::stopping);
  }

  private void tick(ServerTickEvent.Post event) {
    if (started || event.getServer() != level.getServer()) return;
    if (System.nanoTime() > deadline)
      throw new IllegalStateException("Timed out waiting for all OpenCL world programs");
    Set<String> dimensions = new HashSet<>();
    var global = ((MinecraftServerExtension) level.getServer()).c2me$getCLContext();
    if (global == null) throw new IllegalStateException("Missing global OpenCL context");
    Set<CLServerWorldContext> contexts =
        new HashSet<>(
            List.copyOf(
                ((OpenClGlobalEvidenceAccess) global).aeroscapesRegression$registeredWorlds()));
    boolean ready = true;
    for (ServerLevel world : level.getServer().getAllLevels()) {
      dimensions.add(world.dimension().location().toString());
      if (!(world.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator)) continue;
      var context = ((TACSExtension) world.getChunkSource().chunkMap).c2me$getCLContext();
      if (context == null) {
        ready = false;
        continue;
      }
      contexts.add(context);
    }
    var devices = global.getOpenDevices();
    if (devices.isEmpty()) throw new IllegalStateException("No GPU device for OpenCL benchmark");
    for (var device : devices) {
      var pending =
          ((OpenClDeviceEvidenceAccess) device).aeroscapesRegression$pendingActions().get();
      if (!pending.isDone()) ready = false;
      else pending.join();
    }
    for (var context : contexts) {
      var borrowed = context.tryBorrowCommandQueue();
      if (borrowed == null) {
        ready = false;
        continue;
      }
      try (var queue = borrowed.left()) {
        if (queue.getCommandQueue() == 0)
          throw new IllegalStateException("Invalid borrowed OpenCL queue");
        for (var program : OpenCLCGen.ProgramType.values())
          if (borrowed.right().getProgram(program) == 0)
            throw new IllegalStateException("Missing compiled OpenCL program " + program);
      }
    }
    boolean stable =
        previousReady && dimensions.equals(previousDimensions) && contexts.equals(previousContexts);
    previousDimensions = dimensions;
    previousContexts = contexts;
    previousReady = ready;
    if (!ready || !stable) return;
    started = true;
    OpenClBatchEvidence.arm(level.getChunkSource().randomState());
    LogUtils.getLogger()
        .info(
            "OpenCL benchmark programs ready for {} dimensions and {} contexts: {}",
            dimensions.size(),
            contexts.size(),
            dimensions);
    workload.run();
  }

  private void stopping(ServerStoppingEvent event) {
    if (event.getServer() != level.getServer()) return;
    if (!started)
      throw new IllegalStateException("OpenCL benchmark stopped before programs were ready");
    OpenClBatchEvidence.verifyAndLog();
  }
}
