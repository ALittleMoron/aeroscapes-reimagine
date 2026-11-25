package dev.aeroscapes.progression.client;

import dev.aeroscapes.mixin.progression.SkillsClientAccessor;
import net.minecraft.resources.ResourceLocation;
import net.puffish.skillsmod.api.Skill;
import net.puffish.skillsmod.client.SkillsClientMod;

public final class TalentClient {
  private TalentClient() {}

  public static boolean learned(ResourceLocation tree, String node) {
    var skills = SkillsClientMod.getInstance();
    if (skills == null) return false;
    return ((SkillsClientAccessor) skills)
        .aeroscapes$screenData()
        .getCategory(tree)
        .filter(
            category -> {
              var skill = category.getConfig().skills().get(node);
              return skill != null && category.getSkillState(skill) == Skill.State.UNLOCKED;
            })
        .isPresent();
  }
}
