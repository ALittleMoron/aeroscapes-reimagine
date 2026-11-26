package dev.aeroscapes.races.client;

import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.CharacterSubclass;
import dev.aeroscapes.races.Profession;
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
    if (race != null && !race.canTint(snapshot.style(), snapshot.tint())) return;
    var profile =
        new Profile(
            race,
            snapshot.style(),
            snapshot.tint(),
            characterClass,
            snapshot.starter(),
            Profession.byId(snapshot.professionId()),
            snapshot.professionStarter(),
            CharacterSubclass.byId(snapshot.subclassId()),
            snapshot.subclassDeferred(),
            snapshot.grantPending());
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

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    PROFILES.clear();
  }

  record Profile(
      @Nullable Race race,
      int style,
      int tint,
      @Nullable CharacterClass characterClass,
      String starter,
      @Nullable Profession profession,
      String professionStarter,
      @Nullable CharacterSubclass subclass,
      boolean subclassDeferred,
      boolean grantPending) {
    Profile(
        Race race,
        int style,
        int tint,
        CharacterClass characterClass,
        String starter,
        Profession profession,
        String professionStarter,
        boolean grantPending) {
      this(
          race,
          style,
          tint,
          characterClass,
          starter,
          profession,
          professionStarter,
          null,
          false,
          grantPending);
    }

    boolean needsSubclass() {
      return complete() && subclass == null && !CharacterSubclass.choices(characterClass).isEmpty();
    }

    boolean hasClass() {
      return characterClass != null && characterClass.starters().contains(starter);
    }

    boolean hasProfession() {
      return profession != null && profession.starters().contains(professionStarter);
    }

    boolean complete() {
      return race != null && hasClass() && hasProfession() && !grantPending;
    }
  }
}
