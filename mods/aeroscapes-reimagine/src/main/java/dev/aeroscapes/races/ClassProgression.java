package dev.aeroscapes.races;

import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.client.TalentClient;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;
import top.theillusivec4.curios.api.CuriosApi;

public final class ClassProgression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);

  private ClassProgression() {}

  public static boolean available(
      ServerPlayer player, CharacterClass characterClass, String starter) {
    return CharacterClass.creationAvailable()
        && characterClass.starters().contains(starter)
        && ProgressionBridge.categoriesAvailable()
        && SkillsMod.getInstance()
            .getSkillState(player, category(characterClass), node(characterClass, starter))
            .isPresent();
  }

  static String node(CharacterClass characterClass, String starter) {
    return characterClass == CharacterClass.MAGE ? "magic_mastery_1" : starter;
  }

  static ResourceLocation category(CharacterClass characterClass) {
    return characterClass == CharacterClass.MAGE ? MAGIC : COMBAT;
  }

  static boolean learned(ServerPlayer player, CharacterClass characterClass, String node) {
    return learned(player, category(characterClass), node);
  }

  private static boolean learned(ServerPlayer player, ResourceLocation category, String node) {
    return SkillsMod.getInstance().getSkillState(player, category, node).orElse(Skill.State.LOCKED)
        == Skill.State.UNLOCKED;
  }

  public static boolean hasCombatTalent(Player player, String node) {
    if (!ModList.get().isLoaded("puffish_skills")) return false;
    if (player instanceof ServerPlayer serverPlayer) return learned(serverPlayer, COMBAT, node);
    return player.level().isClientSide() && TalentClient.learned(COMBAT, node);
  }

  public static boolean afterChoice(ServerPlayer player) {
    // Migrate legacy talent data before granting the selected node.
    ProgressionBridge.sync(player);
    return restore(player);
  }

  public static boolean restore(ServerPlayer player) {
    CharacterClass characterClass = RaceIdentity.characterClass(player);
    String starter = RaceIdentity.starter(player);
    if (characterClass == null || !available(player, characterClass, starter)) return false;
    String node = node(characterClass, starter);
    var category = category(characterClass);
    var skills = SkillsMod.getInstance();
    if (skills.getSkillState(player, category, node).orElse(null) != Skill.State.UNLOCKED) {
      skills.tryUnlockSkill(player, category, node, false);
      if (skills.getSkillState(player, category, node).orElse(null) != Skill.State.UNLOCKED)
        return false;
    }
    if (characterClass == CharacterClass.MAGE
        && !RaceIdentity.data(player).getBoolean("journal_given")) {
      giveJournal(player);
      RaceIdentity.data(player).putBoolean("journal_given", true);
    }
    return true;
  }

  private static void giveJournal(ServerPlayer player) {
    ItemStack journal = new ItemStack(ItemRegistry.COPPER_SPELL_BOOK.get());
    var slot =
        CuriosApi.getCuriosInventory(player)
            .flatMap(inventory -> inventory.getStacksHandler(Curios.SPELLBOOK_SLOT));
    if (slot.isPresent()) {
      var handler = slot.get();
      var stacks = handler.getStacks();
      if (!handler.getActiveStates().isEmpty()
          && handler.getActiveStates().getFirst()
          && stacks.getSlots() > 0
          && stacks.getStackInSlot(0).isEmpty()) {
        journal = stacks.insertItem(0, journal, false);
      }
    }
    if (!journal.isEmpty()) player.getInventory().placeItemBackInInventory(journal);
  }
}
