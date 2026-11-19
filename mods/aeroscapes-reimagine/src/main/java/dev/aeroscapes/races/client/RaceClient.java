package dev.aeroscapes.races.client;

import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class RaceClient {
  private static final Map<UUID, Appearance> APPEARANCES = new HashMap<>();

  private RaceClient() {}

  public static void accept(RaceNetwork.Snapshot snapshot) {
    Race race = Race.byId(snapshot.race());
    if (race != null && race.validStyle(snapshot.style())) {
      APPEARANCES.put(snapshot.player(), new Appearance(race, snapshot.style()));
    } else {
      APPEARANCES.remove(snapshot.player());
      var connection = Minecraft.getInstance().getConnection();
      if (connection != null && snapshot.player().equals(connection.getLocalGameProfile().getId()))
        RaceVisuals.requestChoice();
    }
  }

  @Nullable
  public static Race race(UUID player) {
    var value = APPEARANCES.get(player);
    return value == null ? null : value.race();
  }

  public static int style(UUID player) {
    var value = APPEARANCES.get(player);
    return value == null ? 0 : value.style();
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    APPEARANCES.clear();
  }

  private record Appearance(Race race, int style) {}
}
