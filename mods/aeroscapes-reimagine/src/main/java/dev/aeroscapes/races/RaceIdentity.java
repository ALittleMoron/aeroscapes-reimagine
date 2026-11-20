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
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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
    NeoForge.EVENT_BUS.addListener(RaceIdentity::deliverRemainingKit);
  }

  public static boolean awaitingChoice(ServerPlayer player) {
    return !(player instanceof FakePlayer)
        && CharacterClass.creationAvailable()
        && (race(player) == null
            || characterClass(player) == null
            || profession(player) == null
            || grantPending(player));
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

  @Nullable
  public static Profession profession(Player player) {
    var identity = player.getPersistentData().getCompound(DATA);
    var chosen = Profession.byId(identity.getString("profession"));
    return chosen != null && chosen.starters().contains(identity.getString("profession_starter"))
        ? chosen
        : null;
  }

  public static String professionStarter(Player player) {
    return player.getPersistentData().getCompound(DATA).getString("profession_starter");
  }

  public static boolean grantPending(Player player) {
    var identity = player.getPersistentData().getCompound(DATA);
    return identity.getBoolean("grant_pending") || identity.getBoolean("profession_grant_pending");
  }

  public static boolean choose(
      ServerPlayer player,
      Race race,
      int style,
      @Nullable CharacterClass chosenClass,
      String starter,
      @Nullable Profession chosenProfession,
      String professionStarter) {
    if (!CharacterClass.creationAvailable()
        || race == null
        || !race.validStyle(style)
        || !player.isAlive()
        || chosenClass == null
        || !chosenClass.starters().contains(starter)
        || chosenProfession == null
        || !chosenProfession.starters().contains(professionStarter)) return false;
    Race previous = race(player);
    if (previous != null && (previous != race || style(player) != style)) return false;
    CharacterClass existing = characterClass(player);
    if (existing != null && (existing != chosenClass || !starter(player).equals(starter)))
      return false;
    Profession previousProfession = profession(player);
    if (previousProfession != null
        && (previousProfession != chosenProfession
            || !professionStarter(player).equals(professionStarter))) return false;
    if (existing != null && previousProfession != null && !grantPending(player)) return false;
    if (existing == null && !ClassProgression.available(player, chosenClass, starter)) return false;
    if (previousProfession == null && !ProfessionProgression.available(player, professionStarter))
      return false;
    if (previous == null && !chooseRace(player, race, style)) return false;
    var identity = data(player);
    if (existing == null) {
      identity.putBoolean(
          CharacterChanges.STARTER_PERSONALLY_LEARNED,
          ClassProgression.learned(player, ClassProgression.node(chosenClass, starter)));
      identity.putString("class", chosenClass.id());
      identity.putString("starter", starter);
      identity.putBoolean("grant_pending", true);
    }
    if (previousProfession == null) {
      identity.putString("profession", chosenProfession.id());
      identity.putString("profession_starter", professionStarter);
      identity.putBoolean("profession_grant_pending", true);
    }
    return finishGrant(player);
  }

  private static boolean finishGrant(ServerPlayer player) {
    var identity = data(player);
    if (identity.getBoolean("grant_pending")) {
      if (!ClassProgression.afterChoice(player)) return false;
      ClassTraits.refresh(player);
      identity.remove("grant_pending");
    }
    if (identity.getBoolean("profession_grant_pending")) {
      var profession = profession(player);
      if (profession == null
          || !ProfessionProgression.grant(player, profession, professionStarter(player)))
        return false;
      identity.remove("profession_grant_pending");
      ProfessionProgression.deliverKit(player);
    }
    return true;
  }

  static boolean chooseRace(ServerPlayer player, Race race, int style) {
    boolean firstSelection = race(player) == null;
    float health = player.getHealth();
    float previousHealth = player.getMaxHealth();
    var stamina = ModList.get().isLoaded("paraglider") ? Stamina.get(player) : null;
    double storedStamina = stamina == null ? 0 : stamina.stamina();
    double previousStamina = stamina == null ? 0 : stamina.maxStamina();
    var result =
        NeoOriginsAPI.setOrigin(
            player, LAYER, ResourceLocation.fromNamespaceAndPath(NAMESPACE, race.id()));
    if (result != SetOriginResult.SUCCESS) return false;
    data(player).putInt("style", style);
    RaceTraits.applyBody(player, race);
    float gain = firstSelection ? player.getMaxHealth() - previousHealth : 0;
    player.setHealth(Math.clamp(health + gain, firstSelection ? 1 : 0, player.getMaxHealth()));
    if (stamina != null) {
      double staminaGain = firstSelection ? stamina.maxStamina() - previousStamina : 0;
      stamina.setStamina(Math.clamp(storedStamina + staminaGain, 0, stamina.maxStamina()), false);
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
      else if (profession(player) != null)
        ProfessionProgression.restoreStarter(serverPlayer, professionStarter(player));
      deliverKitIfPresent(serverPlayer);
    }
  }

  private static void deliverRemainingKit(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0)
      deliverKitIfPresent(player);
  }

  private static void deliverKitIfPresent(ServerPlayer player) {
    if (player
        .getPersistentData()
        .getCompound(DATA)
        .contains(ProfessionProgression.KIT, Tag.TAG_LIST))
      ProfessionProgression.deliverKit(player);
  }
}
