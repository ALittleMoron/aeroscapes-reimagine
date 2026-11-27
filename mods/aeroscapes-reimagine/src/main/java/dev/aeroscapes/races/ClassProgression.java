package dev.aeroscapes.races;

import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.CombatDirection;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.client.TalentClient;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
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
  private static final ResourceLocation RANGER =
      ResourceLocation.parse(ProgressionBridge.RANGER_CATEGORY);
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
    return switch (characterClass) {
      case WARRIOR -> COMBAT;
      case RANGER -> RANGER;
      case MAGE -> MAGIC;
    };
  }

  static boolean learned(ServerPlayer player, CharacterClass characterClass, String node) {
    return learned(player, category(characterClass), node);
  }

  private static boolean learned(ServerPlayer player, ResourceLocation category, String node) {
    return SkillsMod.getInstance().getSkillState(player, category, node).orElse(Skill.State.LOCKED)
        == Skill.State.UNLOCKED;
  }

  public static boolean hasCombatTalent(Player player, String node) {
    return hasTalent(player, node);
  }

  public static boolean hasTalent(Player player, String node) {
    ResourceLocation category = categoryForTalent(node);
    if (category == null || !ModList.get().isLoaded("puffish_skills")) return false;
    if (player instanceof ServerPlayer serverPlayer) return learned(serverPlayer, category, node);
    return player.level().isClientSide() && TalentClient.learned(category, node);
  }

  public static Component talentTitle(String node) {
    return talentText(node, "title");
  }

  public static Component talentDescription(String node) {
    return talentText(node, "description");
  }

  private static Component talentText(String node, String field) {
    ResourceLocation category = categoryForTalent(node);
    if (category == null) return Component.literal(node);
    return Component.translatable(
        "puffish_skills."
            + category.getNamespace()
            + "."
            + category.getPath()
            + "."
            + node
            + "."
            + field);
  }

  @Nullable
  public static ResourceLocation categoryForTalent(String node) {
    if (node.equals("caster_armor_handling") || node.equals("magic_mastery_1")) return MAGIC;
    return CombatDirection.categoryForNode(node);
  }

  public static boolean afterChoice(ServerPlayer player) {
    // Migrate legacy talent data before granting the selected node.
    ProgressionBridge.sync(player);
    return restore(player, true);
  }

  public static boolean restore(ServerPlayer player) {
    return restore(player, false);
  }

  private static boolean restore(ServerPlayer player, boolean creation) {
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
    if (creation
        && characterClass == CharacterClass.MAGE
        && !RaceIdentity.data(player).getBoolean("journal_given")) {
      giveJournal(player);
      RaceIdentity.data(player).putBoolean("journal_given", true);
    }
    return SubclassProgression.restore(player);
  }

  public static List<String> starterSpellIds(
      @Nullable CharacterClass role, @Nullable CharacterSubclass subclass) {
    if (role != CharacterClass.MAGE) return List.of();
    return subclass != null && subclass.isSpellStarter()
        ? List.of("irons_spellbooks:magic_missile", "irons_spellbooks:heal", subclass.starter())
        : List.of("irons_spellbooks:magic_missile", "irons_spellbooks:heal");
  }

  static void restoreSpells(ServerPlayer player) {
    SpellKnowledge.setStarterSpells(
        player,
        starterSpellIds(RaceIdentity.characterClass(player), RaceIdentity.subclass(player)));
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
