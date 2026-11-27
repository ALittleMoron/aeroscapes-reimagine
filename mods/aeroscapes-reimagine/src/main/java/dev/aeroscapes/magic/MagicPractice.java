package dev.aeroscapes.magic;

import dev.aeroscapes.progression.RefundPenalty;
import dev.aeroscapes.races.RaceTraits;
import harmonised.pmmo.api.APIUtils;
import harmonised.pmmo.storage.Experience;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Persistent magic credit projects truthful readiness/fraction into PMMO's global XP scale. */
public final class MagicPractice {
  private static final String DATA = "aeroscapes_reimagine_magic_practice";

  private MagicPractice() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(MagicPractice::clonePlayer);
    NeoForge.EVENT_BUS.addListener(MagicPractice::observeStudy);
  }

  private static CompoundTag data(ServerPlayer player) {
    if (!player.getPersistentData().contains(DATA, Tag.TAG_COMPOUND))
      player.getPersistentData().put(DATA, new CompoundTag());
    return player.getPersistentData().getCompound(DATA);
  }

  private static Experience nativeExperience(ServerPlayer player) {
    return APIUtils.getRawXpMap(player).getOrDefault("magic", new Experience());
  }

  public static void synchronize(ServerPlayer player) {
    CompoundTag state = data(player);
    Experience experience = nativeExperience(player);
    int nativeLevel = Math.clamp(experience.getLevel().getLevel(), 0, 51);
    long nativeXp = Math.max(0, experience.getXp());
    boolean first = !state.getBoolean("initialized");
    if (first) {
      state.putBoolean("initialized", true);
      state.remove("admin_edit");
      state.putLong(
          "credit",
          MagicPracticeProgress.fromNative(
              nativeLevel, nativeXp, Experience.XpLevel.getXpForNextLevel(nativeLevel)));
    } else if (state.getBoolean("admin_edit")) {
      state.remove("admin_edit");
      state.putLong(
          "credit",
          MagicPracticeProgress.fromNative(
              nativeLevel, nativeXp, Experience.XpLevel.getXpForNextLevel(nativeLevel)));
    } else if (nativeLevel != state.getInt("native_level")
        || nativeXp != state.getLong("native_xp")) {
      long converted =
          MagicPracticeProgress.fromNative(
              nativeLevel, nativeXp, Experience.XpLevel.getXpForNextLevel(nativeLevel));
      int previousLevel = state.getInt("native_level");
      long previous =
          MagicPracticeProgress.fromNative(
              previousLevel,
              state.getLong("native_xp"),
              Experience.XpLevel.getXpForNextLevel(previousLevel));
      // An external native XP award must not replace the post-level-50 talent credit.
      state.putLong("credit", state.getLong("credit") + converted - previous);
    }
    state.putLong(
        "credit",
        Math.clamp(
            state.getLong("credit"),
            0,
            MagicPracticeProgress.xpForPoints(MagicTalents.pointCap())));
    project(player, state);
  }

  public static void adminEdited(ServerPlayer player) {
    data(player).putBoolean("admin_edit", true);
  }

  public static long credit(ServerPlayer player) {
    synchronize(player);
    return data(player).getLong("credit");
  }

  public static int level(ServerPlayer player) {
    return MagicPracticeProgress.level(credit(player));
  }

  public static void award(ServerPlayer player, long amount) {
    synchronize(player);
    CompoundTag state = data(player);
    long current = state.getLong("credit");
    long maximum = MagicPracticeProgress.xpForPoints(MagicTalents.pointCap());
    long earned = RaceTraits.practiceAward(player, "magic", amount);
    RefundPenalty.practice(player, "magic", earned);
    state.putLong("credit", current + Math.clamp(earned, 0, Math.max(0, maximum - current)));
    project(player, state);
  }

  private static void project(ServerPlayer player, CompoundTag state) {
    long credit = state.getLong("credit");
    int desiredLevel = MagicPracticeProgress.level(credit);
    long desiredRemainder =
        MagicPracticeProgress.nativeRemainder(
            credit, Experience.XpLevel.getXpForNextLevel(desiredLevel));
    Experience current = nativeExperience(player);
    if (current.getLevel().getLevel() != desiredLevel)
      APIUtils.setLevel("magic", player, desiredLevel);
    if (current.getXp() != desiredRemainder) APIUtils.setXp("magic", player, desiredRemainder);
    state.putInt("native_level", desiredLevel);
    state.putLong("native_xp", desiredRemainder);
  }

  private static void observeStudy(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
      SpellProgression.observeInventory(player);
      SpellProgression.restoreStudyGrants(player);
    }
  }

  private static void clonePlayer(PlayerEvent.Clone event) {
    if (event.getOriginal().getPersistentData().contains(DATA, Tag.TAG_COMPOUND))
      event
          .getEntity()
          .getPersistentData()
          .put(DATA, event.getOriginal().getPersistentData().getCompound(DATA).copy());
  }
}
