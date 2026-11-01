package dev.aeroscapes.diagnostics;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.StringJoiner;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Read-only client cache and native renderer debug information on the render thread. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class ClientChunkFlow {
  private record RendererMethods(Method instance, Method details) {}

  private static final ChunkFlowWindow WINDOW = new ChunkFlowWindow();
  private static final FrameIntervalStats FRAMES = new FrameIntervalStats();
  @Nullable private static RendererMethods renderer;
  private static boolean rendererChecked;

  private ClientChunkFlow() {}

  @SubscribeEvent
  public static void joined(ClientPlayerNetworkEvent.LoggingIn event) {
    WINDOW.reset();
    FRAMES.reset();
  }

  @SubscribeEvent
  public static void cloned(ClientPlayerNetworkEvent.Clone event) {
    FRAMES.reset();
  }

  @SubscribeEvent
  public static void frame(RenderFrameEvent.Post event) {
    if (!WINDOW.active()) return;
    var client = Minecraft.getInstance();
    var level = client.level;
    var player = client.player;
    if (level == null || player == null) {
      FRAMES.reset();
      return;
    }
    long now = System.nanoTime();
    FRAMES.frame(
        now,
        client.isWindowActive()
            && !client.isPaused()
            && client.screen == null
            && client.getOverlay() == null);
    if (!WINDOW.sample(now)) return;
    var timing = FRAMES.take();
    int nearby = 0;
    var center = player.chunkPosition();
    for (int x = center.x - 1; x <= center.x + 1; x++)
      for (int z = center.z - 1; z <= center.z + 1; z++)
        if (level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null) nearby++;
    LogUtils.getLogger()
        .info(
            "[Aeroscapes chunk flow] client dimension={} pos={},{},{} paused={} fps={} frameIntervals={} measuredFrameMs={} meanFrameMs={} maxFrameMs={} maxFrameEndNanos={} framesOver50Ms={} framesOver100Ms={} cached={} nearby9={} tasks={} sections={} renderer={}",
            level.dimension().location(),
            player.getX(),
            player.getY(),
            player.getZ(),
            client.isPaused(),
            client.getFps(),
            timing.intervals(),
            timing.measuredMs(),
            timing.meanMs(),
            timing.maximumMs(),
            timing.maximumEndNanos(),
            timing.over50Ms(),
            timing.over100Ms(),
            level.getChunkSource().getLoadedChunksCount(),
            nearby,
            client.getPendingTasksCount(),
            client.levelRenderer.getSectionStatistics(),
            rendererDetails());
  }

  private static String rendererDetails() {
    try {
      if (!rendererChecked) {
        rendererChecked = true;
        Class<?> type =
            Class.forName(
                "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer",
                false,
                ClientChunkFlow.class.getClassLoader());
        renderer =
            new RendererMethods(
                type.getMethod("instanceNullable"), type.getMethod("getDebugStrings"));
      }
      RendererMethods methods = renderer;
      if (methods == null) return "unavailable";
      Object instance = methods.instance().invoke(null);
      if (instance == null) return "not initialized";
      Object result = methods.details().invoke(instance);
      StringJoiner text = new StringJoiner("; ");
      if (result instanceof Iterable<?> entries)
        for (Object entry : entries) if (entry instanceof String line) text.add(line);
      return text.toString();
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InvocationTargetException
        | LinkageError failure) {
      renderer = null;
      LogUtils.getLogger().warn("Chunk flow renderer diagnostics unavailable", failure);
      return "unavailable";
    }
  }
}
