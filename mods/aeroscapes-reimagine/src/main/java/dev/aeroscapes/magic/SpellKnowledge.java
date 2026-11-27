package dev.aeroscapes.magic;

import dev.aeroscapes.equipment.expedition.ExceptionalAccessory;
import dev.aeroscapes.progression.ProgressionBridge;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.item.Scroll;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.puffish.skillsmod.SkillsMod;

public final class SpellKnowledge {
  static final String DATA = "aeroscapes_reimagine_abilities";
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
  private static final String ACCESS = "magic_mastery_1";
  private static final String STARTERS = "starter_spells";

  private SpellKnowledge() {}

  public static List<AbstractSpell> spells() {
    return SpellRegistry.REGISTRY.stream()
        .sorted(Comparator.comparing(AbstractSpell::getSpellId))
        .toList();
  }

  public static AbstractSpell spell(String id) {
    ResourceLocation location = ResourceLocation.tryParse(id);
    return location == null ? SpellRegistry.none() : SpellRegistry.getSpell(location);
  }

  public static Set<String> unlocked(ServerPlayer player) {
    Collection<String> skills =
        SkillsMod.getInstance().getUnlockedSkills(player, MAGIC).orElse(List.of());
    return new HashSet<>(skills);
  }

  public static int depth(AbstractSpell spell, Set<String> skills) {
    MagicTalents.Spell route = MagicTalents.spell(spell.getSpellId());
    return route == null ? 0 : MagicTalents.mastery(skills, route.direction());
  }

  public static int cap(AbstractSpell spell, Set<String> skills) {
    return MagicTalents.cap(spell.getSpellId(), spell.getMaxLevel(), skills);
  }

  public static int rank(ServerPlayer player, AbstractSpell spell, Set<String> skills) {
    return rank(player, spell, skills, starter(player, spell.getSpellId()));
  }

  private static int rank(
      ServerPlayer player, AbstractSpell spell, Set<String> skills, boolean starter) {
    String id = spell.getSpellId();
    if (!starter && !SpellProgression.studied(player, id) && !MagicTalents.learned(id, skills))
      return 0;
    return Math.max(1, data(player).getCompound("ranks").getInt(id));
  }

  public static boolean hasAccess(ServerPlayer player) {
    return hasAccess(unlocked(player));
  }

  public static boolean hasAccess(Set<String> skills) {
    return skills.contains(ACCESS);
  }

  public static void setStarterSpells(ServerPlayer player, Collection<String> spellIds) {
    var starterSpells = new CompoundTag();
    for (String id : spellIds) {
      if (MagicTalents.spell(id) != null) starterSpells.putBoolean(id, true);
    }
    data(player).put(STARTERS, starterSpells);
  }

  private static boolean starter(ServerPlayer player, String id) {
    return data(player).getCompound(STARTERS).getBoolean(id);
  }

  static CompoundTag data(ServerPlayer player) {
    CompoundTag persistent = player.getPersistentData();
    if (!persistent.contains(DATA, net.minecraft.nbt.Tag.TAG_COMPOUND))
      persistent.put(DATA, new CompoundTag());
    return persistent.getCompound(DATA);
  }

  public static boolean hasFocus(ServerPlayer player) {
    ItemStack stack = Utils.getPlayerSpellbookStack(player);
    return stack != null
        && !stack.isEmpty()
        && stack.getItem() instanceof io.redspace.ironsspellbooks.api.item.ISpellbook;
  }

  public static String knowledgeReason(
      ServerPlayer player, AbstractSpell spell, Set<String> skills) {
    return knowledgeReason(player, spell, skills, starter(player, spell.getSpellId()));
  }

  public static String knowledgeReason(
      ServerPlayer player,
      AbstractSpell spell,
      Set<String> skills,
      Collection<String> futureStarters) {
    return knowledgeReason(player, spell, skills, futureStarters.contains(spell.getSpellId()));
  }

  private static String knowledgeReason(
      ServerPlayer player, AbstractSpell spell, Set<String> skills, boolean starter) {
    if (!spell.isEnabled()) return "disabled";
    int rank = rank(player, spell, skills, starter);
    if (rank == 0) return "unlearned";
    if (!hasAccess(skills)) return "magic_access";
    if (rank > cap(spell, skills)) return "mastery";
    var route = MagicTalents.spell(spell.getSpellId());
    if (route != null && !starter && MagicTalents.mastery(skills, route.direction()) < route.tier())
      return "mastery";
    if (spell.requiresLearning() && !spell.isLearned(player)) return "discovery";
    return "";
  }

  public static boolean canStart(
      AbstractSpell spell,
      ServerPlayer player,
      ItemStack item,
      int level,
      CastSource source,
      boolean feedback) {
    Set<String> skills = unlocked(player);
    var accessory = ExceptionalAccessory.bySpell(spell);
    String reason;
    if (!player.isAlive() || player.isSpectator()) reason = "state";
    else if (!spell.isEnabled()) reason = "disabled";
    else if (source != CastSource.SCROLL && !hasAccess(skills)) reason = "magic_access";
    else if (source == CastSource.SPELLBOOK && accessory != null && accessory.matches(item)) {
      if (accessory.equipped(player) != item) reason = "equipment_required";
      else if (!PreparedAbilities.loadout(player).contains(accessory.id)) reason = "unprepared";
      else reason = level == 1 ? "" : "mastery";
    } else if (source == CastSource.SCROLL) {
      SpellData scroll = ISpellContainer.getOrCreate(item).getSpellAtIndex(0);
      if (!(item.getItem() instanceof Scroll)
          || item.isEmpty()
          || !held(player, item)
          || scroll.getSpell() != spell) reason = "scroll";
      else if (scroll.getLevel() < 1 || level != scroll.getLevel()) reason = "scroll";
      else reason = "";
    } else {
      reason = knowledgeReason(player, spell, skills);
      if (reason.isEmpty() && !PreparedAbilities.loadout(player).contains(spell.getSpellId()))
        reason = "unprepared";
      if (reason.isEmpty() && !hasFocus(player)) reason = "focus";
      if (reason.isEmpty()
          && (level < 1
              || level > cap(spell, skills)
              || level > spell.getLevelFor(rank(player, spell, skills), player)))
        reason = "mastery";
    }
    if (feedback && !reason.isEmpty()) message(player, reason);
    return reason.isEmpty();
  }

  private static boolean held(ServerPlayer player, ItemStack stack) {
    return player.getItemInHand(InteractionHand.MAIN_HAND) == stack
        || player.getItemInHand(InteractionHand.OFF_HAND) == stack;
  }

  public static boolean executionAllowed(
      AbstractSpell spell, ServerPlayer player, int level, CastSource source) {
    MagicData magic = MagicData.getPlayerMagicData(player);
    return magic.isCasting()
        && spell.getSpellId().equals(magic.getCastingSpellId())
        && source == magic.getCastSource()
        && canStart(spell, player, magic.getPlayerCastingItem(), level, source, true);
  }

  public static void message(ServerPlayer player, String reason) {
    player.displayClientMessage(
        Component.translatable("aeroscapes_reimagine.abilities.reason." + reason), true);
  }

  public static Component requiredMasteryMessage(String mastery) {
    return Component.translatable(
        "aeroscapes_reimagine.magic_tree.mastery_required",
        Component.translatable("puffish_skills.aeroscapes_reimagine.magic." + mastery + ".title"));
  }
}
