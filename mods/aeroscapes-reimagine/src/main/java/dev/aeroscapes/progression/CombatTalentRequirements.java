package dev.aeroscapes.progression;

import dev.aeroscapes.equipment.combat.RangedProficiency;
import harmonised.pmmo.api.APIUtils;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

public final class CombatTalentRequirements {
  private CombatTalentRequirements() {}

  @Nullable
  public static Component missing(@Nullable Player player, String node) {
    int ranged = RangedProficiency.missingPracticeLevel(player, node);
    if (ranged > 0) return RangedProficiency.practiceRequired(ranged);
    var gate =
        switch (node) {
          case "light_armor_handling" -> new Gate("light_armor", 2);
          case "heavy_armor_handling" -> new Gate("heavy_armor", 2);
          case "ranger_armor_training" -> new Gate("archery", 4);
          case "rogue_armor_training" -> new Gate("combat", 4);
          default -> null;
        };
    if (gate == null) return null;
    if (player != null
        && ModList.get().isLoaded("pmmo")
        && APIUtils.getLevel(gate.skill(), player) >= gate.level()) return null;
    return Component.translatable(
        "aeroscapes_reimagine.progression.practice_required",
        Component.translatable("pmmo." + gate.skill()),
        gate.level());
  }

  private record Gate(String skill, int level) {}
}
