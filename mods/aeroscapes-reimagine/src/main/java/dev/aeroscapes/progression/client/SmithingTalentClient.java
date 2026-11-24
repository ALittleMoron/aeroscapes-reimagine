package dev.aeroscapes.progression.client;

import dev.aeroscapes.mixin.progression.SkillsClientAccessor;
import dev.aeroscapes.progression.ProfessionDiscipline;
import net.puffish.skillsmod.api.Skill;
import net.puffish.skillsmod.client.SkillsClientMod;

public final class SmithingTalentClient {
  private SmithingTalentClient() {}

  public static boolean learned(String node) {
    var skills = SkillsClientMod.getInstance();
    if (skills == null) return false;
    return ((SkillsClientAccessor) skills)
        .aeroscapes$screenData()
        .getCategory(ProfessionDiscipline.SMITHING.category())
        .filter(
            category -> {
              var skill = category.getConfig().skills().get(node);
              return skill != null && category.getSkillState(skill) == Skill.State.UNLOCKED;
            })
        .isPresent();
  }
}
