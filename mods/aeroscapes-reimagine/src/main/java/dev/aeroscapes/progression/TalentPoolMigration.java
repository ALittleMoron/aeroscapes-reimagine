package dev.aeroscapes.progression;

import dev.aeroscapes.magic.MagicTalents;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.SkillsMod;

final class TalentPoolMigration {
  private static final String MIGRATED = "aeroscapes_reimagine_split_talent_pools";
  private static final String PROFESSIONS_MIGRATED = "aeroscapes_reimagine_split_profession_pools";
  private static final String UNLOCKED = "unlocked_skills";
  private static final String LEGACY_PROFESSION = "aeroscapes_reimagine:profession";

  private TalentPoolMigration() {}

  static void split(ServerPlayer player) {
    var state = player.getPersistentData();
    if (state.getBoolean(MIGRATED)) return;
    var provider = SkillsMod.getInstance();
    var snapshot = new CompoundTag();
    provider.exportPlayerData(player, snapshot);
    var categories = snapshot.getCompound("categories");
    var combat = categories.getCompound(ProgressionBridge.COMBAT_CATEGORY);
    var magic = categories.getCompound(ProgressionBridge.MAGIC_CATEGORY);
    var previous = combat.getList(UNLOCKED, Tag.TAG_STRING);
    var retained = new ListTag();
    var moved = magic.getList(UNLOCKED, Tag.TAG_STRING);
    var seen = new HashSet<String>();
    for (Tag node : moved) seen.add(node.getAsString());
    for (Tag node : previous) {
      String id = node.getAsString();
      if (MagicTalents.nodes().contains(id)) {
        if (seen.add(id)) moved.add(node);
      } else {
        retained.add(node);
      }
    }
    if (retained.size() != previous.size()) {
      combat.put(UNLOCKED, retained);
      magic.putBoolean("unlocked", true);
      magic.put(UNLOCKED, moved);
      categories.put(ProgressionBridge.MAGIC_CATEGORY, magic);
      provider.importPlayerData(player, snapshot);
    }
    state.putBoolean(MIGRATED, true);
  }

  static void splitProfessions(ServerPlayer player, Map<String, Long> levels) {
    var state = player.getPersistentData();
    if (state.getBoolean(PROFESSIONS_MIGRATED)) return;
    var provider = SkillsMod.getInstance();
    var snapshot = new CompoundTag();
    provider.exportPlayerData(player, snapshot);
    var categories = snapshot.getCompound("categories");
    if (splitProfessionData(categories)) provider.importPlayerData(player, snapshot);
    RefundPenalty.splitProfessionDebt(player, levels);
    state.putBoolean(PROFESSIONS_MIGRATED, true);
  }

  static boolean splitProfessionData(CompoundTag categories) {
    if (!categories.contains(LEGACY_PROFESSION, Tag.TAG_COMPOUND)) return false;
    var previous = categories.getCompound(LEGACY_PROFESSION);
    var unresolved = new ListTag();
    for (Tag node : previous.getList(UNLOCKED, Tag.TAG_STRING)) {
      var tree = ProfessionDiscipline.byLegacyNode(node.getAsString());
      if (tree.isEmpty()) {
        unresolved.add(node);
        continue;
      }
      String category = tree.get().category().toString();
      var target = categories.getCompound(category);
      var learned = target.getList(UNLOCKED, Tag.TAG_STRING);
      if (!learned.contains(node)) learned.add(node);
      target.put(UNLOCKED, learned);
      target.putBoolean("unlocked", true);
      categories.put(category, target);
    }
    // The old shared point source does not migrate. Each tree derives its own credit from practice.
    if (unresolved.isEmpty()) categories.remove(LEGACY_PROFESSION);
    else previous.put(UNLOCKED, unresolved);
    return true;
  }
}
