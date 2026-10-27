package dev.aeroscapes.monitor;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Optional local monitoring commands; no game or server keybindings are registered. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class MonitorCommands {
  private static final int DEFAULT_SECONDS = 60;
  private static final int MAX_SECONDS = 3600;
  private static final String SOCKET = "aeroscapes-monitor-" + ProcessHandle.current().pid();
  private static final ScheduledExecutorService WORKER =
      Executors.newSingleThreadScheduledExecutor(
          task -> {
            Thread thread = new Thread(task, "aeroscapes-monitor-control");
            thread.setDaemon(true);
            return thread;
          });
  private static final MonitorController CONTROL =
      new MonitorController(
          WORKER, message -> MangoHudConnection.send(SOCKET, message), MonitorCommands::feedback);

  private MonitorCommands() {}

  @SubscribeEvent
  public static void register(RegisterClientCommandsEvent event) {
    if (!"1".equals(System.getenv("AEROSCAPES_MONITOR_ENABLED"))) return;
    event
        .getDispatcher()
        .register(
            Commands.literal("aeroscapes_monitor")
                .then(
                    Commands.literal("toggle")
                        .executes(
                            context -> {
                              CONTROL.toggle();
                              return 1;
                            }))
                .then(
                    Commands.literal("record")
                        .executes(
                            context -> {
                              CONTROL.record(DEFAULT_SECONDS);
                              return 1;
                            })
                        .then(
                            Commands.argument(
                                    "seconds", IntegerArgumentType.integer(1, MAX_SECONDS))
                                .executes(
                                    context -> {
                                      CONTROL.record(
                                          IntegerArgumentType.getInteger(context, "seconds"));
                                      return 1;
                                    }))
                        .then(
                            Commands.literal("stop")
                                .executes(
                                    context -> {
                                      CONTROL.stop();
                                      return 1;
                                    }))));
  }

  private static void feedback(String event, Integer seconds) {
    Minecraft minecraft = Minecraft.getInstance();
    minecraft.execute(
        () -> {
          if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                Component.translatable("message.aeroscapes_reimagine.monitor." + event, seconds),
                false);
          }
        });
  }
}
