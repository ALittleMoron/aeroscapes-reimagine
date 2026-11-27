package dev.aeroscapes.races;

import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.ProgressionBridge;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import virtuoel.pehkui.api.ScaleTypes;

public final class RaceTraits {
  private static final int PRACTICE_DIVISOR = 5;
  public static final int PRACTICE_AFFINITY_PERCENT = 100 / PRACTICE_DIVISOR;

  private static final ThreadLocal<ClassPractice> CLASS_PRACTICE = new ThreadLocal<>();

  private RaceTraits() {}

  public static void withClassPractice(
      ServerPlayer player, String skill, boolean archery, boolean subclass, Runnable award) {
    var prior = CLASS_PRACTICE.get();
    CLASS_PRACTICE.set(new ClassPractice(player, skill, archery, subclass));
    try {
      award.run();
    } finally {
      if (prior == null) CLASS_PRACTICE.remove();
      else CLASS_PRACTICE.set(prior);
    }
  }

  public static float width(Race race) {
    return switch (race) {
      case HUMAN -> 1.0F;
      case DWARF -> 1.08F;
      case GOBLIN -> 0.83F;
      case ELF -> 0.94F;
    };
  }

  public static float height(Race race) {
    return switch (race) {
      case HUMAN -> 1.0F;
      case DWARF -> 0.83F;
      case GOBLIN -> 0.65F;
      case ELF -> 1.06F;
    };
  }

  static void applyBody(ServerPlayer player, Race race) {
    // Width and height propagate to the model, eyes and hitbox without scaling reach or jump.
    ScaleTypes.WIDTH.getScaleData(player).setScale(width(race));
    ScaleTypes.HEIGHT.getScaleData(player).setScale(height(race));
    player.refreshDimensions();
  }

  public static int practiceBonusPercent(
      @Nullable Race race,
      @Nullable CharacterClass characterClass,
      @Nullable Profession profession,
      String skill) {
    int affinities = 0;
    if (race == Race.ELF && ("archery".equals(skill) || "magic".equals(skill))) affinities++;
    if (matchesClass(characterClass, skill)) affinities++;
    if (profession != null && profession.practiceSkill().equals(skill)) affinities++;
    return affinities * PRACTICE_AFFINITY_PERCENT;
  }

  private static boolean matchesClass(@Nullable CharacterClass characterClass, String skill) {
    if (characterClass == null) return false;
    if (characterClass == CharacterClass.RANGER) {
      return "archery".equals(skill) || ProgressionBridge.RANGER_PRACTICE.equals(skill);
    }
    return characterClass.practiceSkill().equals(skill);
  }

  public static long practiceAward(ServerPlayer player, String skill, long amount) {
    if (amount <= 0) return amount;
    var race = RaceIdentity.race(player);
    int fifths =
        practiceBonusPercent(
                race, RaceIdentity.characterClass(player), RaceIdentity.profession(player), skill)
            / PRACTICE_AFFINITY_PERCENT;
    var context = CLASS_PRACTICE.get();
    if (context != null && context.player() == player && context.skill().equals(skill)) {
      CLASS_PRACTICE.remove();
      if (context.archery() && race == Race.ELF) fifths++;
      if (context.subclass()) fifths++;
    }
    if (fifths == 0) return amount;
    var data = RaceIdentity.data(player);
    String key = skill + "_fifths";
    long remainder = amount % PRACTICE_DIVISOR * fifths + data.getInt(key);
    long bonus = amount / PRACTICE_DIVISOR * fifths + remainder / PRACTICE_DIVISOR;
    data.putInt(key, (int) (remainder % PRACTICE_DIVISOR));
    return amount + Math.min(bonus, Long.MAX_VALUE - amount);
  }

  public static boolean qualifiesMagicPractice(ServerPlayer player, String spellId) {
    var subclass = RaceIdentity.subclass(player);
    if (subclass != CharacterSubclass.ELEMENTALIST && subclass != CharacterSubclass.NECROMANCER
        || !ModList.get().isLoaded("irons_spellbooks")) return false;
    var route = MagicTalents.spell(spellId);
    if (route == null) return false;
    var spell = SpellKnowledge.spell(spellId);
    if (spell == SpellRegistry.none()) return false;
    String school = spell.getSchoolType().getId().getPath();
    return switch (subclass) {
      case ELEMENTALIST ->
          route.direction().equals("destruction")
              && (school.equals("fire") || school.equals("ice") || school.equals("lightning"));
      case NECROMANCER ->
          school.equals("blood")
              || route.id().startsWith("irons_spellbooks:summon_")
              || route.id().equals("irons_spellbooks:raise_dead");
      default -> false;
    };
  }

  private record ClassPractice(
      ServerPlayer player, String skill, boolean archery, boolean subclass) {}
}
