package dev.aeroscapes.races;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.origin.SetOriginResult;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import tictim.paraglider.api.stamina.Stamina;

public final class RaceIdentity {
  private static final String NAMESPACE = "aeroscapes_reimagine";
  private static final ResourceLocation LAYER = ResourceLocation.parse("neoorigins:origin");
  private static final String DATA = "aeroscapes_reimagine_racial_traits";

  private RaceIdentity() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RaceIdentity::protectDuringSelection);
    NeoForge.EVENT_BUS.addListener(RaceIdentity::copyPlayer);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RaceIdentity::login);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RaceIdentity::respawn);
  }

  public static boolean awaitingChoice(ServerPlayer player) {
    return !(player instanceof FakePlayer)
        && CharacterClass.creationAvailable()
        && (race(player) == null || characterClass(player) == null || grantPending(player));
  }

  private static void protectDuringSelection(EntityInvulnerabilityCheckEvent event) {
    if (event.getEntity() instanceof ServerPlayer player
        && !event.getSource().is(DamageTypes.GENERIC_KILL)
        && awaitingChoice(player)) {
      event.setInvulnerable(true);
    }
  }

  @Nullable
  public static Race race(Player player) {
    if (!ModList.get().isLoaded("neoorigins")) return null;
    var origin = player.getData(OriginAttachments.originData()).getOrigin(LAYER);
    return origin != null && NAMESPACE.equals(origin.getNamespace())
        ? Race.byId(origin.getPath())
        : null;
  }

  public static int style(Player player) {
    return player.getPersistentData().getCompound(DATA).getInt("style");
  }

  @Nullable
  public static CharacterClass characterClass(Player player) {
    var identity = player.getPersistentData().getCompound(DATA);
    var chosen = CharacterClass.byId(identity.getString("class"));
    return chosen != null && chosen.starters().contains(identity.getString("starter"))
        ? chosen
        : null;
  }

  public static String starter(Player player) {
    return player.getPersistentData().getCompound(DATA).getString("starter");
  }

  public static boolean grantPending(Player player) {
    return player.getPersistentData().getCompound(DATA).getBoolean("grant_pending");
  }

  public static boolean choose(
      ServerPlayer player,
      Race race,
      int style,
      @Nullable CharacterClass chosenClass,
      String starter) {
    if (!CharacterClass.creationAvailable()
        || race == null
        || !race.validStyle(style)
        || !player.isAlive()
        || chosenClass == null
        || !chosenClass.starters().contains(starter)) return false;
    Race previous = race(player);
    if (previous != null && (previous != race || style(player) != style)) return false;
    CharacterClass existing = characterClass(player);
    if (existing != null) {
      return grantPending(player)
          && existing == chosenClass
          && starter(player).equals(starter)
          && finishGrant(player);
    }
    if (!ClassProgression.available(player, chosenClass, starter)) return false;
    if (previous == null && !chooseRace(player, race, style)) return false;
    var identity = data(player);
    identity.putString("class", chosenClass.id());
    identity.putString("starter", starter);
    identity.putBoolean("grant_pending", true);
    return finishGrant(player);
  }

  private static boolean finishGrant(ServerPlayer player) {
    if (!ClassProgression.afterChoice(player)) return false;
    ClassTraits.refresh(player);
    data(player).remove("grant_pending");
    return true;
  }

  private static boolean chooseRace(ServerPlayer player, Race race, int style) {
    float previousHealth = player.getMaxHealth();
    var stamina = ModList.get().isLoaded("paraglider") ? Stamina.get(player) : null;
    double previousStamina = stamina == null ? 0 : stamina.maxStamina();
    var result =
        NeoOriginsAPI.setOrigin(
            player, LAYER, ResourceLocation.fromNamespaceAndPath(NAMESPACE, race.id()));
    if (result != SetOriginResult.SUCCESS) return false;
    data(player).putInt("style", style);
    RaceTraits.applyBody(player, race);
    player.setHealth(
        Math.clamp(
            player.getHealth() + player.getMaxHealth() - previousHealth, 1, player.getMaxHealth()));
    if (stamina != null) {
      stamina.setStamina(
          Math.clamp(
              stamina.stamina() + stamina.maxStamina() - previousStamina, 0, stamina.maxStamina()),
          false);
    }
    return true;
  }

  static CompoundTag data(Player player) {
    CompoundTag persistent = player.getPersistentData();
    if (!persistent.contains(DATA, Tag.TAG_COMPOUND)) persistent.put(DATA, new CompoundTag());
    return persistent.getCompound(DATA);
  }

  private static void copyPlayer(PlayerEvent.Clone event) {
    var previous = event.getOriginal().getPersistentData();
    if (previous.contains(DATA, Tag.TAG_COMPOUND))
      event.getEntity().getPersistentData().put(DATA, previous.getCompound(DATA).copy());
  }

  private static void login(PlayerEvent.PlayerLoggedInEvent event) {
    restoreBody(event.getEntity());
  }

  private static void respawn(PlayerEvent.PlayerRespawnEvent event) {
    restoreBody(event.getEntity());
  }

  private static void restoreBody(Player player) {
    if (player instanceof ServerPlayer serverPlayer) {
      var race = race(player);
      if (race != null) RaceTraits.applyBody(serverPlayer, race);
      if (race != null && characterClass(player) != null && grantPending(player))
        finishGrant(serverPlayer);
    }
  }
}
