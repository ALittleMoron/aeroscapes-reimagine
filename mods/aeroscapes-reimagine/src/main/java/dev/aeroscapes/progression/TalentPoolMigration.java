package dev.aeroscapes.progression;

import dev.aeroscapes.magic.MagicTalents;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.SkillsMod;

final class TalentPoolMigration {
  private static final String MIGRATED = "aeroscapes_reimagine_split_talent_pools";
  private static final String UNLOCKED = "unlocked_skills";

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
}
