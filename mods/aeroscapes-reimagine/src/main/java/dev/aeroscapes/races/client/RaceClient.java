package dev.aeroscapes.races.client;

import dev.aeroscapes.races.CharacterClass;
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
  private static final Map<UUID, Profile> PROFILES = new HashMap<>();

  private RaceClient() {}

  public static void accept(RaceNetwork.Snapshot snapshot) {
    Race race = Race.byId(snapshot.race());
    var characterClass = CharacterClass.byId(snapshot.classId());
    if (race != null && !race.validStyle(snapshot.style())) return;
    var profile =
        new Profile(
            race, snapshot.style(), characterClass, snapshot.starter(), snapshot.grantPending());
    if (profile.grantPending() && !profile.hasSelection()) return;
    var previous = PROFILES.put(snapshot.player(), profile);
    var connection = Minecraft.getInstance().getConnection();
    if (!profile.complete()
        && connection != null
        && snapshot.player().equals(connection.getLocalGameProfile().getId())) {
      RaceVisuals.requestChoice(profile, !profile.equals(previous));
    }
  }

  @Nullable
  static Profile profile(UUID player) {
    return PROFILES.get(player);
  }

  @Nullable
  public static Race race(UUID player) {
    var value = profile(player);
    return value == null ? null : value.race();
  }

  public static int style(UUID player) {
    var value = profile(player);
    return value == null ? 0 : value.style();
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    PROFILES.clear();
  }

  record Profile(
      @Nullable Race race,
      int style,
      @Nullable CharacterClass characterClass,
      String starter,
      boolean grantPending) {
    boolean hasSelection() {
      return race != null && characterClass != null && characterClass.starters().contains(starter);
    }

    boolean complete() {
      return hasSelection() && !grantPending;
    }
  }
}
