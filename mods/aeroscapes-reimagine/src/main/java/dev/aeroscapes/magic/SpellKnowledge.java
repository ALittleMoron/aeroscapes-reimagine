package dev.aeroscapes.magic;

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
import java.util.function.IntSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.puffish.skillsmod.SkillsMod;

public final class SpellKnowledge {
  static final String DATA = "aeroscapes_reimagine_abilities";
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ThreadLocal<Boolean> SCROLL_LEVEL = new ThreadLocal<>();

  private SpellKnowledge() {}

  public static boolean scrollLevel() {
    return Boolean.TRUE.equals(SCROLL_LEVEL.get());
  }

  public static int scrollLevel(IntSupplier operation) {
    Boolean previous = SCROLL_LEVEL.get();
    SCROLL_LEVEL.set(true);
    try {
      return operation.getAsInt();
    } finally {
      if (previous == null) SCROLL_LEVEL.remove();
      else SCROLL_LEVEL.set(previous);
    }
  }

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
        SkillsMod.getInstance().getUnlockedSkills(player, COMBAT).orElse(List.of());
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
    if (!MagicTalents.learned(spell.getSpellId(), skills)) return 0;
    return Math.max(1, data(player).getCompound("ranks").getInt(spell.getSpellId()));
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
    if (!spell.isEnabled()) return "disabled";
    int rank = rank(player, spell, skills);
    if (rank == 0) return "unlearned";
    if (rank > cap(spell, skills)) return "mastery";
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
    String reason;
    if (!player.isAlive() || player.isSpectator()) reason = "state";
    else if (!spell.isEnabled()) reason = "disabled";
    else if (source == CastSource.SCROLL) {
      SpellData scroll = ISpellContainer.getOrCreate(item).getSpellAtIndex(0);
      if (!(item.getItem() instanceof Scroll)
          || item.isEmpty()
          || !held(player, item)
          || scroll.getSpell() != spell) reason = "scroll";
      else if (scroll.getLevel() < 1 || level < 1) reason = "scroll";
      else reason = "";
    } else {
      reason = knowledgeReason(player, spell, skills);
      if (reason.isEmpty()
          && !PreparedAbilities.preparation(player).slots().contains(spell.getSpellId()))
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
        Component.translatable("puffish_skills.aeroscapes_reimagine.combat." + mastery + ".title"));
  }
}
