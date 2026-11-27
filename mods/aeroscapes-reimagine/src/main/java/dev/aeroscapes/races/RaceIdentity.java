package dev.aeroscapes.races;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.origin.SetOriginResult;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.TalentRefunds;
import java.util.HashSet;
import java.util.Set;
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
  private static final String TINT = "appearance_tint";
  private static final String PROFESSION_STARTER_PAID = "profession_starter_personally_learned";
  private static final String PENDING_SUBCLASS = "subclass_grant_pending";
  private static final String DEFERRED_SUBCLASS = "subclass_deferred_for";

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

  public static int tint(Player player) {
    var identity = player.getPersistentData().getCompound(DATA);
    if (!identity.contains(TINT, Tag.TAG_INT)) return Race.DEFAULT_TINT;
    int color = identity.getInt(TINT);
    var race = race(player);
    return race != null && race.canTint(style(player), color) ? color : Race.DEFAULT_TINT;
  }

  static boolean recolor(ServerPlayer player, Race race, int style, int tint) {
    if (!player.isAlive()
        || player.isSpectator()
        || awaitingChoice(player)
        || race != race(player)
        || style != style(player)
        || !(race.hasEars() || race.hasBeard(style))
        || !race.canTint(style, tint)) return false;
    saveTint(data(player), tint);
    return true;
  }

  private static void saveTint(CompoundTag identity, int tint) {
    if (tint == Race.DEFAULT_TINT) identity.remove(TINT);
    else identity.putInt(TINT, tint);
  }

  @Nullable
  public static CharacterClass characterClass(Player player) {
    var identity = player.getPersistentData().getCompound(DATA);
    var chosen = CharacterClass.byId(identity.getString("class"));
    return chosen != null && chosen.starters().contains(identity.getString("starter"))
        ? chosen
        : null;
  }

  @Nullable
  public static CharacterSubclass subclass(Player player) {
    return SubclassProgression.read(
        player.getPersistentData().getCompound(DATA), characterClass(player));
  }

  public static Set<TalentRefunds.Node> identityGrants(ServerPlayer player) {
    var grants = new HashSet<TalentRefunds.Node>();
    var mainClass = characterClass(player);
    if (mainClass != null) {
      String pool = ClassProgression.category(mainClass).getPath();
      grants.add(new TalentRefunds.Node(pool, ClassProgression.node(mainClass, starter(player))));
      var signature = SubclassProgression.talentNode(subclass(player));
      if (signature != null) grants.add(new TalentRefunds.Node(pool, signature));
    }
    if (profession(player) != null) {
      String starter = professionStarter(player);
      ProfessionDiscipline.byLegacyNode(starter)
          .ifPresent(tree -> grants.add(new TalentRefunds.Node(tree.skill(), starter)));
    }
    return Set.copyOf(grants);
  }

  public static Set<TalentRefunds.Node> identityGrantOverlaps(ServerPlayer player) {
    var identity = data(player);
    var paid = new HashSet<TalentRefunds.Node>();
    var mainClass = characterClass(player);
    if (mainClass != null) {
      String pool = ClassProgression.category(mainClass).getPath();
      if (identity.getBoolean(CharacterChanges.STARTER_PERSONALLY_LEARNED))
        paid.add(new TalentRefunds.Node(pool, ClassProgression.node(mainClass, starter(player))));
      var signature = SubclassProgression.talentNode(subclass(player));
      if (signature != null && !SubclassProgression.funded(player))
        paid.add(new TalentRefunds.Node(pool, signature));
    }
    if (identity.getBoolean(PROFESSION_STARTER_PAID)) {
      String starter = professionStarter(player);
      ProfessionDiscipline.byLegacyNode(starter)
          .ifPresent(tree -> paid.add(new TalentRefunds.Node(tree.skill(), starter)));
    }
    return Set.copyOf(paid);
  }

  static boolean finishReset(
      ServerPlayer player,
      Race race,
      int style,
      CharacterClass chosenClass,
      String starter,
      @Nullable CharacterSubclass subclass,
      Profession profession,
      String professionStarter) {
    if (!ClassProgression.available(player, chosenClass, starter)
        || !ProfessionProgression.available(player, professionStarter)
        || subclass != null && !SubclassProgression.available(player, subclass)) return false;

    var snapshot = data(player).copy();
    var identity = data(player);
    var previousClass = characterClass(player);
    identity.putString("class", chosenClass.id());
    identity.putString("starter", starter);
    identity.putString("profession", profession.id());
    identity.putString("profession_starter", professionStarter);
    identity.remove(CharacterChanges.STARTER_PERSONALLY_LEARNED);
    identity.remove(PROFESSION_STARTER_PAID);
    if (subclass == null && previousClass != chosenClass)
      identity.putString(DEFERRED_SUBCLASS, chosenClass.id());
    if (!SubclassProgression.assign(player, subclass)
        || !ClassProgression.restore(player)
        || !ProfessionProgression.restoreStarter(player, professionStarter)
        || (race(player) != race || style(player) != style) && !chooseRace(player, race, style)) {
      player.getPersistentData().put(DATA, snapshot);
      ClassProgression.restoreSpells(player);
      return false;
    }
    SubclassProgression.markGranted(player);
    ProgressionBridge.sync(player);
    ClassTraits.refresh(player);
    return true;
  }

  @Nullable
  static CharacterSubclass pendingSubclass(Player player) {
    return CharacterSubclass.byId(
        player.getPersistentData().getCompound(DATA).getString(PENDING_SUBCLASS));
  }

  static boolean subclassDeferred(Player player) {
    var mainClass = characterClass(player);
    return mainClass != null
        && mainClass
            .id()
            .equals(player.getPersistentData().getCompound(DATA).getString(DEFERRED_SUBCLASS));
  }

  static void deferSubclass(ServerPlayer player) {
    var mainClass = characterClass(player);
    if (mainClass != null
        && !CharacterSubclass.choices(mainClass).isEmpty()
        && canChooseSubclass(player)) data(player).putString(DEFERRED_SUBCLASS, mainClass.id());
  }

  private static boolean canChooseSubclass(ServerPlayer player) {
    return CharacterClass.creationAvailable()
        && player.isAlive()
        && !player.isSpectator()
        && !awaitingChoice(player)
        && subclass(player) == null;
  }

  public static boolean chooseSubclass(ServerPlayer player, CharacterSubclass chosen) {
    if (!canChooseSubclass(player)
        || chosen.mainClass() != characterClass(player)
        || !SubclassProgression.available(player, chosen)
        || !SubclassProgression.assign(player, chosen)) return false;
    ProgressionBridge.sync(player);
    return true;
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
    return identity.getBoolean("grant_pending")
        || identity.getBoolean("profession_grant_pending")
        || identity.contains(PENDING_SUBCLASS, Tag.TAG_STRING);
  }

  public static boolean choose(
      ServerPlayer player,
      Race race,
      int style,
      @Nullable CharacterClass chosenClass,
      String starter,
      @Nullable Profession chosenProfession,
      String professionStarter) {
    return choose(
        player,
        race,
        style,
        Race.DEFAULT_TINT,
        chosenClass,
        starter,
        chosenProfession,
        professionStarter,
        null);
  }

  static boolean choose(
      ServerPlayer player,
      Race race,
      int style,
      int tint,
      @Nullable CharacterClass chosenClass,
      String starter,
      @Nullable Profession chosenProfession,
      String professionStarter,
      @Nullable CharacterSubclass chosenSubclass) {
    if (!CharacterClass.creationAvailable()
        || race == null
        || !race.canTint(style, tint)
        || !player.isAlive()
        || chosenClass == null
        || !chosenClass.starters().contains(starter)
        || chosenProfession == null
        || !chosenProfession.starters().contains(professionStarter)) return false;
    var pending = pendingSubclass(player);
    if (pending != null && pending != chosenSubclass) return false;
    if (chosenSubclass != null
        && (chosenSubclass.mainClass() != chosenClass
            || !SubclassProgression.available(player, chosenSubclass))) return false;
    Race previous = race(player);
    if (previous != null && (previous != race || style(player) != style || tint(player) != tint))
      return false;
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
    if (previous == null) saveTint(identity, tint);
    if (existing == null) {
      identity.putBoolean(
          CharacterChanges.STARTER_PERSONALLY_LEARNED,
          ClassProgression.learned(
              player, chosenClass, ClassProgression.node(chosenClass, starter)));
      identity.putString("class", chosenClass.id());
      identity.putString("starter", starter);
      identity.putBoolean("grant_pending", true);
    }
    if (previousProfession == null) {
      identity.putBoolean(
          PROFESSION_STARTER_PAID, ProfessionProgression.learned(player, professionStarter));
      identity.putString("profession", chosenProfession.id());
      identity.putString("profession_starter", professionStarter);
      identity.putBoolean("profession_grant_pending", true);
    }
    if (chosenSubclass != null && subclass(player) == null)
      identity.putString(PENDING_SUBCLASS, chosenSubclass.id());
    else if (subclass(player) == null && existing == null)
      identity.putString(DEFERRED_SUBCLASS, chosenClass.id());
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
    var pending = pendingSubclass(player);
    if (pending != null) {
      if (subclass(player) == null && !SubclassProgression.assign(player, pending)) return false;
      ProgressionBridge.sync(player);
    }
    identity.remove(PENDING_SUBCLASS);
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
    var identity = data(player);
    identity.putInt("style", style);
    identity.remove(TINT);
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
