package dev.aeroscapes.races;

import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.puffish.skillsmod.SkillsMod;

public final class CharacterChanges {
  static final String STARTER_PERSONALLY_LEARNED = "class_starter_personally_learned";

  public record Selection(
      String kind,
      String target,
      int style,
      String starter,
      boolean refundBranch,
      String subclassId) {
    public Selection(String kind, String target, int style, String starter, boolean refundBranch) {
      this(kind, target, style, starter, refundBranch, "");
    }
  }

  public record Preview(List<String> removed, int returned, int spent, boolean advance) {
    public Preview {
      removed = List.copyOf(removed);
    }
  }

  private record Assessment(@Nullable Preview preview, String problem) {}

  private static final Assessment INVALID = new Assessment(null, "invalid");
  private static final Assessment NO_SPACE = new Assessment(null, "space");
  private static final Assessment NEED_POINT = new Assessment(null, "point");

  private CharacterChanges() {}

  @Nullable
  public static Preview preview(ServerPlayer player, Selection selection) {
    return assess(player, selection).preview();
  }

  public static String problem(ServerPlayer player, Selection selection) {
    return assess(player, selection).problem();
  }

  private static Assessment assess(ServerPlayer player, Selection selection) {
    var currentRace = RaceIdentity.race(player);
    var currentClass = RaceIdentity.characterClass(player);
    var currentProfession = RaceIdentity.profession(player);
    if (!CharacterClass.creationAvailable()
        || !player.isAlive()
        || player.isSpectator()
        || RaceIdentity.grantPending(player)
        || currentRace == null
        || currentClass == null
        || currentProfession == null) return INVALID;
    return switch (selection.kind()) {
      case "race" -> assessRace(player, currentRace, selection);
      case "class" -> assessClass(player, currentClass, selection);
      case "subclass" -> assessSubclass(player, currentClass, selection);
      case "profession" -> assessProfession(player, currentProfession, selection);
      default -> INVALID;
    };
  }

  private static Assessment assessRace(ServerPlayer player, Race current, Selection selection) {
    var target = Race.byId(selection.target());
    if (target == null
        || target == current
        || !target.validStyle(selection.style())
        || !selection.starter().isEmpty()
        || selection.refundBranch()) return INVALID;
    var body = player.getBoundingBox();
    double halfWidth = body.getXsize() * RaceTraits.width(target) / RaceTraits.width(current) / 2;
    double height = body.getYsize() * RaceTraits.height(target) / RaceTraits.height(current);
    var nextBody =
        new AABB(
            player.getX() - halfWidth,
            body.minY,
            player.getZ() - halfWidth,
            player.getX() + halfWidth,
            body.minY + height,
            player.getZ() + halfWidth);
    if (!player.level().noCollision(player, nextBody.deflate(1.0e-7))) return NO_SPACE;
    return new Assessment(new Preview(List.of(), 0, 0, false), "");
  }

  private static Assessment assessClass(
      ServerPlayer player, CharacterClass current, Selection selection) {
    var target = CharacterClass.byId(selection.target());
    if (target == null
        || target == current
        || selection.style() != 0
        || !ClassProgression.available(player, target, selection.starter())) return INVALID;
    var nextSubclass = CharacterSubclass.byId(selection.subclassId());
    if (!selection.subclassId().isEmpty()
        && (nextSubclass == null
            || nextSubclass.mainClass() != target
            || !SubclassProgression.available(player, nextSubclass))) return INVALID;
    var skills = SkillsMod.getInstance();
    var oldCategory = ClassProgression.category(current);
    var nextCategory = ClassProgression.category(target);
    var unlocked = skills.getUnlockedSkills(player, oldCategory).orElse(List.of());
    var oldStarter = ClassProgression.node(current, RaceIdentity.starter(player));
    Collection<String> remove;
    if (selection.refundBranch()) {
      remove = current == CharacterClass.MAGE ? MagicTalents.nodes() : current.starters();
    } else {
      // 0.6.5 did not record whether a starter was learned before character creation.
      var identity = RaceIdentity.data(player);
      boolean keep =
          !identity.contains(STARTER_PERSONALLY_LEARNED, Tag.TAG_BYTE)
              || identity.getBoolean(STARTER_PERSONALLY_LEARNED)
              || (current == CharacterClass.MAGE
                  && unlocked.stream()
                      .anyMatch(id -> !id.equals(oldStarter) && MagicTalents.nodes().contains(id)));
      remove = keep ? List.of() : List.of(oldStarter);
    }
    var leaving = RaceIdentity.subclass(player);
    Set<String> affected = new HashSet<>(remove);
    if (leaving != null) affected.addAll(leaving.branch());
    String nextStarter = ClassProgression.node(target, selection.starter());
    var removed = unlocked.stream().filter(affected::contains).sorted().toList();
    int returned = paidRefund(player, removed);
    int cost = ClassProgression.learned(player, target, nextStarter) ? 0 : 1;
    int available = skills.getPointsLeft(player, nextCategory).orElse(0);
    // Moving the class-funded point frees credit in the destination, up to its normal cap.
    if (!oldCategory.equals(nextCategory)) {
      int cap =
          target == CharacterClass.MAGE
              ? MagicTalents.pointCap()
              : ProgressionBridge.COMBAT_POINT_CAP;
      try {
        if (ProgressionBridge.storedPoints(player, nextCategory.toString()) < cap) available++;
      } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
        return INVALID;
      }
    } else {
      available += returned;
    }
    int balance = available - cost;
    if (balance < -1 && cost != 0) return NEED_POINT;
    return new Assessment(new Preview(removed, returned, cost, balance < 0 && cost != 0), "");
  }

  private static Assessment assessSubclass(
      ServerPlayer player, CharacterClass mainClass, Selection selection) {
    var current = RaceIdentity.subclass(player);
    var target = CharacterSubclass.byId(selection.target());
    if (current == null
        || target == null
        || target == current
        || target.mainClass() != mainClass
        || selection.style() != 0
        || !selection.starter().isEmpty()
        || selection.refundBranch()
        || !SubclassProgression.available(player, target)) return INVALID;
    var learned =
        SkillsMod.getInstance()
            .getUnlockedSkills(player, ClassProgression.category(mainClass))
            .orElse(List.of());
    var removed = learned.stream().filter(current.branch()::contains).sorted().toList();
    return new Assessment(new Preview(removed, paidRefund(player, removed), 0, false), "");
  }

  private static int paidRefund(ServerPlayer player, List<String> removed) {
    var chosen = RaceIdentity.subclass(player);
    return removed.size()
        - (chosen != null
                && SubclassProgression.funded(player)
                && removed.contains(chosen.starter())
            ? 1
            : 0);
  }

  private static Assessment assessProfession(
      ServerPlayer player, Profession current, Selection selection) {
    var target = Profession.byId(selection.target());
    if (target == null
        || target == current
        || selection.style() != 0
        || selection.refundBranch()
        || !target.starters().contains(selection.starter())
        || !ProfessionProgression.available(player, selection.starter())) return INVALID;
    var provider = SkillsMod.getInstance();
    var oldDiscipline = discipline(current);
    var targetDiscipline = discipline(target);
    var unlocked = provider.getUnlockedSkills(player, oldDiscipline.category()).orElse(List.of());
    var removed = unlocked.stream().filter(current.starters()::contains).sorted().toList();
    int cost = ProfessionProgression.learned(player, selection.starter()) ? 0 : 1;
    int available = provider.getPointsLeft(player, targetDiscipline.category()).orElse(0);
    try {
      if (ProgressionBridge.storedPoints(player, targetDiscipline.category().toString())
          < targetDiscipline.cap()) available++;
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
      return INVALID;
    }
    if (available < cost && cost != 0) return NEED_POINT;
    return new Assessment(new Preview(removed, removed.size(), cost, false), "");
  }

  private static ProfessionDiscipline discipline(Profession profession) {
    return ProfessionDiscipline.bySkill(profession.practiceSkill()).orElseThrow();
  }

  public static boolean change(ServerPlayer player, Selection selection, Preview expected) {
    var checked = preview(player, selection);
    if (checked == null || !checked.equals(expected)) return false;
    if (selection.kind().equals("race"))
      return RaceIdentity.chooseRace(player, Race.byId(selection.target()), selection.style());
    if (selection.kind().equals("subclass")) {
      var category = ClassProgression.category(RaceIdentity.characterClass(player));
      if (!applyTalents(
          player, category, category, null, CharacterSubclass.byId(selection.target()), checked))
        return false;
      ProgressionBridge.sync(player);
      return true;
    }
    boolean changeClass = selection.kind().equals("class");
    var target = changeClass ? CharacterClass.byId(selection.target()) : null;
    var oldCategory =
        changeClass
            ? ClassProgression.category(RaceIdentity.characterClass(player))
            : discipline(RaceIdentity.profession(player)).category();
    var nextCategory =
        changeClass
            ? ClassProgression.category(target)
            : discipline(Profession.byId(selection.target())).category();
    String nextStarter =
        changeClass ? ClassProgression.node(target, selection.starter()) : selection.starter();
    var nextSubclass = changeClass ? CharacterSubclass.byId(selection.subclassId()) : null;
    if (!applyTalents(player, oldCategory, nextCategory, nextStarter, nextSubclass, checked))
      return false;
    var identity = RaceIdentity.data(player);
    if (changeClass) {
      if (nextSubclass == null) SubclassProgression.assign(player, null);
      identity.putString("class", selection.target());
      identity.putString("starter", selection.starter());
      identity.putBoolean(STARTER_PERSONALLY_LEARNED, checked.spent() == 0);
      // Future login restores the selected node, but only creation gives a journal.
      identity.putBoolean("journal_given", true);
      ProgressionBridge.sync(player);
      ClassTraits.refresh(player);
    } else {
      identity.putString("profession", selection.target());
      identity.putString("profession_starter", selection.starter());
      ProgressionBridge.sync(player);
    }
    return true;
  }

  private static boolean applyTalents(
      ServerPlayer player,
      ResourceLocation oldCategory,
      ResourceLocation nextCategory,
      @Nullable String nextStarter,
      @Nullable CharacterSubclass nextSubclass,
      Preview checked) {
    var provider = SkillsMod.getInstance();
    var expectedOld = new HashSet<>(provider.getUnlockedSkills(player, oldCategory).orElseThrow());
    expectedOld.removeAll(checked.removed());
    var expectedNext =
        oldCategory.equals(nextCategory)
            ? expectedOld
            : new HashSet<>(provider.getUnlockedSkills(player, nextCategory).orElseThrow());
    if (nextStarter != null) expectedNext.add(nextStarter);
    var snapshot = new CompoundTag();
    provider.exportPlayerData(player, snapshot);
    boolean complete = false;
    try {
      for (String id : checked.removed()) provider.lockSkill(player, oldCategory, id);
      if (nextStarter != null && checked.spent() != 0)
        provider.tryUnlockSkill(
            player,
            nextCategory,
            nextStarter,
            checked.advance() || !oldCategory.equals(nextCategory));
      complete =
          expectedOld.equals(
                  Set.copyOf(provider.getUnlockedSkills(player, oldCategory).orElseThrow()))
              && expectedNext.equals(
                  Set.copyOf(provider.getUnlockedSkills(player, nextCategory).orElseThrow()));
      if (complete && nextSubclass != null) {
        complete = false;
        complete = SubclassProgression.assign(player, nextSubclass);
      }
      return complete;
    } finally {
      if (!complete) provider.importPlayerData(player, snapshot);
    }
  }
}
