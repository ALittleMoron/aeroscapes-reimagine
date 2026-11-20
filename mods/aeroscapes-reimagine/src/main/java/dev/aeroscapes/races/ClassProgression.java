package dev.aeroscapes.races;

import dev.aeroscapes.progression.ProgressionBridge;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;
import top.theillusivec4.curios.api.CuriosApi;

public final class ClassProgression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);

  private ClassProgression() {}

  public static boolean available(
      ServerPlayer player, CharacterClass characterClass, String starter) {
    return CharacterClass.creationAvailable()
        && characterClass.starters().contains(starter)
        && ProgressionBridge.categoriesAvailable()
        && SkillsMod.getInstance()
            .getSkillState(player, COMBAT, node(characterClass, starter))
            .isPresent();
  }

  private static String node(CharacterClass characterClass, String starter) {
    return characterClass == CharacterClass.MAGE ? "magic_mastery_1" : starter;
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
    var skills = SkillsMod.getInstance();
    if (skills.getSkillState(player, COMBAT, node).orElse(null) != Skill.State.UNLOCKED) {
      skills.tryUnlockSkill(player, COMBAT, node, false);
      if (skills.getSkillState(player, COMBAT, node).orElse(null) != Skill.State.UNLOCKED)
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
