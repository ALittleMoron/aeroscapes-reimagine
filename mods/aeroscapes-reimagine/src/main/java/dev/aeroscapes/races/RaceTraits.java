package dev.aeroscapes.races;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import virtuoel.pehkui.api.ScaleTypes;

public final class RaceTraits {
  private static final int PRACTICE_DIVISOR = 5;
  public static final int PRACTICE_AFFINITY_PERCENT = 100 / PRACTICE_DIVISOR;

  private static final ThreadLocal<ServerPlayer> SUBCLASS_PRACTICE = new ThreadLocal<>();

  private RaceTraits() {}

  public static void withSubclassPractice(ServerPlayer player, Runnable award) {
    var prior = SUBCLASS_PRACTICE.get();
    SUBCLASS_PRACTICE.set(player);
    try {
      award.run();
    } finally {
      if (prior == null) SUBCLASS_PRACTICE.remove();
      else SUBCLASS_PRACTICE.set(prior);
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
    if (characterClass != null && characterClass.practiceSkill().equals(skill)) affinities++;
    if (profession != null && profession.practiceSkill().equals(skill)) affinities++;
    return affinities * PRACTICE_AFFINITY_PERCENT;
  }

  public static long practiceAward(ServerPlayer player, String skill, long amount) {
    if (amount <= 0) return amount;
    int fifths =
        practiceBonusPercent(
                RaceIdentity.race(player),
                RaceIdentity.characterClass(player),
                RaceIdentity.profession(player),
                skill)
            / PRACTICE_AFFINITY_PERCENT;
    if (skill.equals("combat") && SUBCLASS_PRACTICE.get() == player) {
      SUBCLASS_PRACTICE.remove();
      fifths++;
    }
    if (fifths == 0) return amount;
    var data = RaceIdentity.data(player);
    String key = skill + "_fifths";
    long remainder = amount % PRACTICE_DIVISOR * fifths + data.getInt(key);
    long bonus = amount / PRACTICE_DIVISOR * fifths + remainder / PRACTICE_DIVISOR;
    data.putInt(key, (int) (remainder % PRACTICE_DIVISOR));
    return amount + Math.min(bonus, Long.MAX_VALUE - amount);
  }
}
