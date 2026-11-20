package dev.aeroscapes.races;

import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.progression.ProgressionBridge;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.puffish.skillsmod.SkillsMod;

public final class CharacterChanges {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ResourceLocation PROFESSION =
      ResourceLocation.parse(ProgressionBridge.PROFESSION_CATEGORY);
  static final String STARTER_PERSONALLY_LEARNED = "class_starter_personally_learned";

  public record Selection(
      String kind, String target, int style, String starter, boolean refundBranch) {}

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
    var skills = SkillsMod.getInstance();
    var unlocked = skills.getUnlockedSkills(player, COMBAT).orElse(List.of());
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
    return talentChange(
        player, COMBAT, unlocked, remove, ClassProgression.node(target, selection.starter()));
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
    var unlocked = SkillsMod.getInstance().getUnlockedSkills(player, PROFESSION).orElse(List.of());
    return talentChange(player, PROFESSION, unlocked, current.starters(), selection.starter());
  }

  private static Assessment talentChange(
      ServerPlayer player,
      ResourceLocation category,
      Collection<String> unlocked,
      Collection<String> scope,
      String nextStarter) {
    var removed = unlocked.stream().filter(scope::contains).sorted().toList();
    // All nodes in the owned combat and profession catalogues cost one point.
    int cost = unlocked.contains(nextStarter) ? 0 : 1;
    int available = SkillsMod.getInstance().getPointsLeft(player, category).orElse(0);
    long balance = (long) available + removed.size() - cost;
    // The new class starter can use the next point when the old one must continue supporting
    // purchased talents. No points are minted. Native purchases wait until the balance recovers.
    if (balance < (category.equals(COMBAT) ? -1 : 0)) return NEED_POINT;
    return new Assessment(new Preview(removed, removed.size(), cost, balance < 0 && cost > 0), "");
  }

  public static boolean change(ServerPlayer player, Selection selection, Preview expected) {
    var checked = preview(player, selection);
    if (checked == null || !checked.equals(expected)) return false;
    if (selection.kind().equals("race"))
      return RaceIdentity.chooseRace(player, Race.byId(selection.target()), selection.style());
    boolean changeClass = selection.kind().equals("class");
    var category = changeClass ? COMBAT : PROFESSION;
    String nextStarter =
        changeClass
            ? ClassProgression.node(CharacterClass.byId(selection.target()), selection.starter())
            : selection.starter();
    if (!applyTalents(player, category, nextStarter, checked)) return false;
    var identity = RaceIdentity.data(player);
    if (changeClass) {
      identity.putString("class", selection.target());
      identity.putString("starter", selection.starter());
      identity.putBoolean(STARTER_PERSONALLY_LEARNED, checked.spent() == 0);
      // Future login restores the selected node, but only creation gives a journal.
      identity.putBoolean("journal_given", true);
      ClassTraits.refresh(player);
    } else {
      identity.putString("profession", selection.target());
      identity.putString("profession_starter", selection.starter());
    }
    return true;
  }

  private static boolean applyTalents(
      ServerPlayer player, ResourceLocation category, String nextStarter, Preview checked) {
    var provider = SkillsMod.getInstance();
    var before = Set.copyOf(provider.getUnlockedSkills(player, category).orElseThrow());
    var expected = new HashSet<>(before);
    expected.removeAll(checked.removed());
    expected.add(nextStarter);
    boolean complete = false;
    try {
      for (String id : checked.removed()) provider.lockSkill(player, category, id);
      if (checked.spent() != 0)
        provider.tryUnlockSkill(player, category, nextStarter, checked.advance());
      complete =
          expected.equals(Set.copyOf(provider.getUnlockedSkills(player, category).orElseThrow()));
      return complete;
    } finally {
      if (!complete) {
        var after = Set.copyOf(provider.getUnlockedSkills(player, category).orElseThrow());
        for (String id : after) if (!before.contains(id)) provider.lockSkill(player, category, id);
        for (String id : before)
          if (!after.contains(id)) provider.unlockSkill(player, category, id);
      }
    }
  }
}
