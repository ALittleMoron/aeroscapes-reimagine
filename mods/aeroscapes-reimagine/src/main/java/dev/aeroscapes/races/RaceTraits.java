package dev.aeroscapes.races;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import virtuoel.pehkui.api.ScaleTypes;

public final class RaceTraits {
  private RaceTraits() {}

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
      @Nullable Race race, @Nullable CharacterClass characterClass, String skill) {
    int racial = race == Race.ELF && ("archery".equals(skill) || "magic".equals(skill)) ? 20 : 0;
    int vocational =
        characterClass != null && characterClass.practiceSkill().equals(skill) ? 20 : 0;
    return racial + vocational;
  }

  public static long practiceAward(ServerPlayer player, String skill, long amount) {
    if (amount <= 0) return amount;
    int fifths =
        practiceBonusPercent(RaceIdentity.race(player), RaceIdentity.characterClass(player), skill)
            / 20;
    if (fifths == 0) return amount;
    var data = RaceIdentity.data(player);
    String key = skill + "_fifths";
    long remainder = amount % 5 * fifths + data.getInt(key);
    long bonus = amount / 5 * fifths + remainder / 5;
    data.putInt(key, (int) (remainder % 5));
    return amount + Math.min(bonus, Long.MAX_VALUE - amount);
  }
}
