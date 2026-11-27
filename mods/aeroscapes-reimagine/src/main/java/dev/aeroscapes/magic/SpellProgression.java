package dev.aeroscapes.magic;

import dev.aeroscapes.progression.PracticeActions;
import dev.aeroscapes.progression.ProgressionBridge;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.item.Scroll;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

/** Repeat study is separate from paid talent mastery and the existing higher-rank inscription. */
public final class SpellProgression {
  private static final String MISSILE = "irons_spellbooks:magic_missile";
  private static final String SEEN_SCROLLS = "study_discovery";
  private static final String CASTS = "study_casts";
  private static final String STUDIED = "studied_spells";
  private static final String GRANTS = "study_grants";
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
  private static final ResourceLocation GRANT_POINTS =
      ResourceLocation.parse("aeroscapes_reimagine:scroll_study");
  private static final WeakHashMap<ServerPlayer, CompletedScroll> COMPLETED = new WeakHashMap<>();

  public record FirstStudyEntry(
      String spellId, int casts, int requiredCasts, int requiredMastery, String reason) {}

  private record CompletedScroll(AbstractSpell spell, ItemStack stack) {}

  private SpellProgression() {}

  public static String studyReason(int known, int scroll, int cap) {
    if (known < 1) return "unlearned";
    if (scroll < 1 || scroll > cap) return "mastery";
    return scroll <= known ? "no_improvement" : "";
  }

  public static boolean studied(ServerPlayer player, String spellId) {
    return SpellKnowledge.data(player).getCompound(STUDIED).getBoolean(spellId);
  }

  public static Set<String> studyGrants(ServerPlayer player) {
    var grants = SpellKnowledge.data(player).getCompound(GRANTS);
    return grants.getAllKeys().stream()
        .filter(grants::getBoolean)
        .collect(Collectors.toUnmodifiableSet());
  }

  public static void restoreStudyGrants(ServerPlayer player) {
    var provider = SkillsMod.getInstance();
    if (!provider.hasCategory(MAGIC)) return;
    int points = 0;
    for (String node : studyGrants(player)) {
      var definition = MagicTalents.definitions().get(node);
      if (definition == null || !definition.effects().isEmpty()) continue;
      if (!unlocked(provider, player, node)) provider.unlockSkill(player, MAGIC, node);
      if (unlocked(provider, player, node)) points += definition.cost();
    }
    if (provider.getPoints(player, MAGIC, GRANT_POINTS).orElse(0) != points)
      provider.setPoints(player, MAGIC, GRANT_POINTS, points, true);
  }

  public static void beginCast(ServerPlayer player) {
    COMPLETED.remove(player);
  }

  public static void completedScrollCast(
      ServerPlayer player, AbstractSpell spell, ItemStack stack) {
    if (!PracticeActions.personal(player)
        || stack.isEmpty()
        || !(stack.getItem() instanceof Scroll)
        || !spell.isEnabled()
        || MagicTalents.spell(spell.getSpellId()) == null) return;
    var scroll = ISpellContainer.getOrCreate(stack).getSpellAtIndex(0);
    if (scroll.getSpell() == spell && scroll.getLevel() > 0)
      COMPLETED.put(player, new CompletedScroll(spell, stack));
  }

  public static void scrollConsumed(ServerPlayer player, ItemStack stack, int previousCount) {
    var completed = COMPLETED.remove(player);
    if (completed == null || completed.stack() != stack || stack.getCount() >= previousCount)
      return;
    String id = completed.spell().getSpellId();
    var route = MagicTalents.spell(id);
    if (route == null) return;
    var data = SpellKnowledge.data(player);
    var casts = data.getCompound(CASTS);
    int required = requiredCasts(route);
    int previous = Math.clamp(casts.getInt(id), 0, required);
    int count = Math.min(required, previous + 1);
    casts.putInt(id, count);
    data.put(CASTS, casts);
    var seen = data.getCompound(SEEN_SCROLLS);
    seen.putBoolean(id, true);
    data.put(SEEN_SCROLLS, seen);
    if (count > previous
        && SpellKnowledge.rank(player, completed.spell(), SpellKnowledge.unlocked(player)) == 0) {
      player.displayClientMessage(
          Component.translatable(
              "aeroscapes_reimagine.abilities.study.notice.progress",
              completed.spell().getDisplayName(player),
              count,
              required),
          true);
    }
  }

  public static void observeInventory(ServerPlayer player) {
    var data = SpellKnowledge.data(player);
    var seen = data.getCompound(SEEN_SCROLLS);
    boolean changed = false;
    var inventory = player.getInventory();
    for (int slot = 0; slot < inventory.getContainerSize(); slot++)
      changed |= observeScroll(inventory.getItem(slot), seen);
    if (player.containerMenu instanceof InscriptionTableMenu menu)
      changed |= observeScroll(menu.getScrollSlot().getItem(), seen);
    if (changed) data.put(SEEN_SCROLLS, seen);
  }

  private static boolean observeScroll(ItemStack stack, CompoundTag seen) {
    if (stack.isEmpty() || !(stack.getItem() instanceof Scroll)) return false;
    var scroll = ISpellContainer.getOrCreate(stack).getSpellAtIndex(0);
    String id = scroll.getSpell().getSpellId();
    if (scroll.getLevel() < 1 || MagicTalents.spell(id) == null || seen.getBoolean(id))
      return false;
    seen.putBoolean(id, true);
    return true;
  }

  public static List<FirstStudyEntry> firstStudyEntries(ServerPlayer player) {
    observeInventory(player);
    var data = SpellKnowledge.data(player);
    Set<String> skills = SpellKnowledge.unlocked(player);
    var casts = data.getCompound(CASTS);
    var seen = data.getCompound(SEEN_SCROLLS);
    var entries = new ArrayList<FirstStudyEntry>();
    for (var spell : SpellKnowledge.spells()) {
      String id = spell.getSpellId();
      var route = MagicTalents.spell(id);
      if (route == null
          || !spell.isEnabled()
          || !visible(player, spell, skills, seen.getBoolean(id))) continue;
      int required = requiredCasts(route);
      int count = Math.clamp(casts.getInt(id), 0, required);
      String reason = firstStudyReason(player, spell, route, skills, count);
      if (reason.isEmpty()
          && player.containerMenu instanceof InscriptionTableMenu menu
          && !hasBook(menu)) reason = "study_book";
      entries.add(new FirstStudyEntry(id, count, required, requiredMastery(route), reason));
    }
    entries.sort(java.util.Comparator.comparing(entry -> !MISSILE.equals(entry.spellId())));
    return List.copyOf(entries);
  }

  public static boolean firstStudy(ServerPlayer player, InscriptionTableMenu menu, String spellId) {
    if (!PracticeActions.personal(player) || !player.isAlive()) return refuse(player, "state");
    if (player.containerMenu != menu || !menu.stillValid(player))
      return refuse(player, "study_unavailable");
    if (!hasBook(menu)) return refuse(player, "study_book");

    var spell = SpellKnowledge.spell(spellId);
    var route = MagicTalents.spell(spellId);
    if (route == null || spell == SpellRegistry.none()) return refuse(player, "study_unavailable");
    if (!spell.isEnabled()) return refuse(player, "disabled");
    observeInventory(player);
    var data = SpellKnowledge.data(player);
    var skills = SpellKnowledge.unlocked(player);
    if (!visible(player, spell, skills, data.getCompound(SEEN_SCROLLS).getBoolean(spellId)))
      return refuse(player, "study_unavailable");
    int casts = data.getCompound(CASTS).getInt(spellId);
    String reason = firstStudyReason(player, spell, route, skills, casts);
    if (!reason.isEmpty()) return refuse(player, reason);

    String grant = dedicatedNode(route);
    if (grant != null) {
      var provider = SkillsMod.getInstance();
      provider.unlockSkill(player, MAGIC, grant);
      if (!unlocked(provider, player, grant)) return refuse(player, "study_unavailable");
      var grants = data.getCompound(GRANTS);
      grants.putBoolean(grant, true);
      data.put(GRANTS, grants);
    }
    var learned = data.getCompound(STUDIED);
    learned.putBoolean(spellId, true);
    data.put(STUDIED, learned);
    var ranks = data.getCompound("ranks");
    ranks.putInt(spellId, Math.max(1, ranks.getInt(spellId)));
    data.put("ranks", ranks);
    restoreStudyGrants(player);
    notifyStudied(player, spell, 1);
    return true;
  }

  static void notifyStudied(ServerPlayer player, AbstractSpell spell, int rank) {
    PacketDistributor.sendToPlayer(
        player, new AbilityNetwork.StudyCompleted(spell.getSpellId(), rank));
  }

  private static boolean visible(
      ServerPlayer player, AbstractSpell spell, Set<String> skills, boolean observedScroll) {
    return MISSILE.equals(spell.getSpellId())
        || observedScroll
        || SpellKnowledge.rank(player, spell, skills) > 0;
  }

  private static boolean hasBook(InscriptionTableMenu menu) {
    var stack = menu.getSpellBookSlot().getItem();
    return !stack.isEmpty() && stack.getItem() instanceof ISpellbook;
  }

  private static String firstStudyReason(
      ServerPlayer player,
      AbstractSpell spell,
      MagicTalents.Spell route,
      Set<String> skills,
      int casts) {
    if (SpellKnowledge.rank(player, spell, skills) > 0) return "study_complete";
    if (casts < requiredCasts(route)) return "study_casts";
    int required = requiredMastery(route);
    if (required > 0 && !SpellKnowledge.hasAccess(skills)) return "study_access";
    if (MagicTalents.mastery(skills, route.direction()) < required) return "study_mastery";
    if (spell.requiresLearning() && !spell.isLearned(player)) return "discovery";
    return "";
  }

  private static int requiredMastery(MagicTalents.Spell route) {
    return MISSILE.equals(route.id()) ? 0 : route.tier();
  }

  private static int requiredCasts(MagicTalents.Spell route) {
    if (MISSILE.equals(route.id())) return 3;
    return switch (route.tier()) {
      case 1 -> 4;
      case 2 -> 6;
      case 3 -> 9;
      case 4 -> 12;
      default -> 15;
    };
  }

  @Nullable
  private static String dedicatedNode(MagicTalents.Spell route) {
    String expected = "magic_spell_" + ResourceLocation.parse(route.id()).getPath();
    var node = MagicTalents.definitions().get(route.node());
    return route.node().equals(expected) && node != null && node.effects().isEmpty()
        ? expected
        : null;
  }

  private static boolean unlocked(SkillsMod provider, ServerPlayer player, String node) {
    return provider.getSkillState(player, MAGIC, node).orElse(null) == Skill.State.UNLOCKED;
  }

  private static boolean refuse(ServerPlayer player, String reason) {
    SpellKnowledge.message(player, reason);
    return false;
  }
}
