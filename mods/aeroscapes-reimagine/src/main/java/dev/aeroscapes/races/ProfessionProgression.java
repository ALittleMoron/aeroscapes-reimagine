package dev.aeroscapes.races;

import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.client.TalentClient;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

public final class ProfessionProgression {
  static final String KIT = "profession_kit_remaining";
  private static final String UNRESOLVED_KIT = "profession_kit_unresolved";

  private ProfessionProgression() {}

  public static boolean learned(ServerPlayer player, String node) {
    return ModList.get().isLoaded("puffish_skills")
        && ProfessionDiscipline.byLegacyNode(node)
            .filter(tree -> learned(player, tree, node))
            .isPresent();
  }

  public static boolean hasTalent(Player player, ProfessionDiscipline tree, String node) {
    if (!ModList.get().isLoaded("puffish_skills")) return false;
    if (player instanceof ServerPlayer serverPlayer) return learned(serverPlayer, tree, node);
    return player.level().isClientSide() && TalentClient.learned(tree.category(), node);
  }

  private static boolean learned(ServerPlayer player, ProfessionDiscipline tree, String node) {
    return SkillsMod.getInstance()
            .getSkillState(player, tree.category(), node)
            .orElse(Skill.State.LOCKED)
        == Skill.State.UNLOCKED;
  }

  static boolean available(ServerPlayer player, String node) {
    return ProgressionBridge.categoriesAvailable()
        && ProfessionDiscipline.byLegacyNode(node)
            .filter(
                tree ->
                    SkillsMod.getInstance()
                        .getSkillState(player, tree.category(), node)
                        .isPresent())
            .isPresent();
  }

  static boolean grant(ServerPlayer player, Profession profession, String starter) {
    // A previously purchased starter still earns the profession's point.
    ProgressionBridge.sync(player);
    if (!restoreStarter(player, starter)) return false;
    var remaining = new ListTag();
    for (var stack : profession.kit()) remaining.add(stack.save(player.registryAccess()));
    RaceIdentity.data(player).put(KIT, remaining);
    return true;
  }

  static boolean restoreStarter(ServerPlayer player, String starter) {
    if (learned(player, starter)) return true;
    if (!available(player, starter)) return false;
    var category = ProfessionDiscipline.byLegacyNode(starter).orElseThrow().category();
    SkillsMod.getInstance().tryUnlockSkill(player, category, starter, false);
    return learned(player, starter);
  }

  static void deliverKit(ServerPlayer player) {
    var data = RaceIdentity.data(player);
    var remaining = data.getList(KIT, Tag.TAG_COMPOUND);
    var inventory = new PlayerMainInvWrapper(player.getInventory());
    // Inventory and undelivered stacks share the same player save, including partial insertions.
    for (var iterator = remaining.listIterator(); iterator.hasNext(); ) {
      var saved = iterator.next();
      var stack = ItemStack.parse(player.registryAccess(), saved).orElse(ItemStack.EMPTY);
      if (stack.isEmpty()) {
        // Preserve the original entry for save repair without parsing it every second.
        var unresolved = data.getList(UNRESOLVED_KIT, Tag.TAG_COMPOUND);
        unresolved.add(saved);
        data.put(UNRESOLVED_KIT, unresolved);
        iterator.remove();
        continue;
      }
      var leftover = ItemHandlerHelper.insertItemStacked(inventory, stack, false);
      if (leftover.isEmpty()) iterator.remove();
      else if (leftover.getCount() < stack.getCount())
        iterator.set(leftover.save(player.registryAccess()));
    }
    if (remaining.isEmpty()) data.remove(KIT);
  }
}
