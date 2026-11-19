package dev.aeroscapes.magic;

import harmonised.pmmo.api.APIUtils;
import harmonised.pmmo.storage.Experience;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

/** Persistent magic credit projects truthful readiness/fraction into PMMO's global XP scale. */
public final class MagicPractice {
  private static final String DATA = "aeroscapes_reimagine_magic_practice";

  private MagicPractice() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(MagicPractice::clonePlayer);
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
      migrateTalents(player);
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
    state.putLong("credit", current + Math.clamp(amount, 0, maximum - current));
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

  private static void migrateTalents(ServerPlayer player) {
    CompoundTag exported = new CompoundTag();
    SkillsMod.getInstance().exportPlayerData(player, exported);
    CompoundTag combat =
        exported.getCompound("categories").getCompound("aeroscapes_reimagine:combat");
    ListTag previous = combat.getList("unlocked_skills", Tag.TAG_STRING);
    ListTag retained = new ListTag();
    var removed = new HashSet<String>();
    CompoundTag ranks = SpellKnowledge.data(player).getCompound("ranks");
    for (Tag value : previous) {
      String id = value.getAsString();
      if (!id.startsWith("magic_")) retained.add(StringTag.valueOf(id));
      else {
        removed.add(id);
        if (id.startsWith("magic_spell_")) {
          String spell = "irons_spellbooks:" + id.substring("magic_spell_".length());
          ranks.putInt(spell, Math.max(1, ranks.getInt(spell)));
        }
      }
    }
    SpellKnowledge.data(player).put("ranks", ranks);
    if (!removed.isEmpty()) {
      combat.put("unlocked_skills", retained);
      SkillsMod.getInstance().importPlayerData(player, exported);
      player.displayClientMessage(
          Component.translatable("aeroscapes_reimagine.magic_tree.migrated"), false);
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
