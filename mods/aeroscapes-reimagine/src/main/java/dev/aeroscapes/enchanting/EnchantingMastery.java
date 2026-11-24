package dev.aeroscapes.enchanting;

import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.progression.PracticeActions;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.races.ProfessionProgression;
import harmonised.pmmo.api.APIUtils;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/** Personal talents and practice for paid enchantment application. */
public final class EnchantingMastery {
  private static final ProfessionDiscipline DISCIPLINE = ProfessionDiscipline.ENCHANTING;
  private static final List<String> MASTERY =
      List.of(
          "enchanting_mastery_1",
          "enchanting_mastery_2",
          "enchanting_mastery_3",
          "enchanting_mastery_4");
  private static final int PRACTICE_PER_LEVEL = 120;

  private EnchantingMastery() {}

  public static int rank(Player player) {
    for (int index = MASTERY.size() - 1; index >= 0; index--) {
      if (ProfessionProgression.hasTalent(player, DISCIPLINE, MASTERY.get(index))) return index + 1;
    }
    return 0;
  }

  public static int discountedLevels(Player player, int baseLevels) {
    if (baseLevels <= 1) return baseLevels;
    int reduction = 0;
    if (ProfessionProgression.hasTalent(player, DISCIPLINE, "enchanting_efficiency_1")) reduction++;
    if (ProfessionProgression.hasTalent(player, DISCIPLINE, "enchanting_efficiency_2")) reduction++;
    return Math.max(1, baseLevels - reduction);
  }

  public static void award(Player player, int appliedLevel) {
    if (appliedLevel <= 0
        || !PracticeActions.personal(player)
        || !ModList.get().isLoaded("pmmo")
        || !ModList.get().isLoaded("puffish_skills")) return;
    var enchanter = (ServerPlayer) player;
    APIUtils.addXp(DISCIPLINE.skill(), enchanter, (long) PRACTICE_PER_LEVEL * appliedLevel);
    MagicPractice.award(enchanter, Math.min(20, 4L * appliedLevel));
  }
}
