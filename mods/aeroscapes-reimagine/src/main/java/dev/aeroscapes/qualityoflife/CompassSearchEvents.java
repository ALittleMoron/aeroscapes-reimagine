package dev.aeroscapes.qualityoflife;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class CompassSearchEvents {
  private CompassSearchEvents() {}

  @SubscribeEvent
  public static void tick(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
      CompassSearch.reconcile(player);
    }
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) CompassSearch.cancel(player);
  }

  @SubscribeEvent
  public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) CompassSearch.cancel(player);
  }

  @SubscribeEvent
  public static void stopping(ServerStoppingEvent event) {
    CompassSearch.clear();
  }
}
