package dev.aeroscapes.progression;

import dev.aeroscapes.equipment.combat.RangedProficiency;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class CombatTalentRequirements {
  private CombatTalentRequirements() {}

  @Nullable
  public static Component missing(@Nullable Player player, String node) {
    int ranged = RangedProficiency.missingPracticeLevel(player, node);
    return ranged > 0 ? RangedProficiency.practiceRequired(ranged) : null;
  }
}
