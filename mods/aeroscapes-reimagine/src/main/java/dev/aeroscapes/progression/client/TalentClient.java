package dev.aeroscapes.progression.client;

import dev.aeroscapes.mixin.progression.SkillsClientAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.puffish.skillsmod.api.Skill;
import net.puffish.skillsmod.client.SkillsClientMod;
import net.puffish.skillsmod.client.data.ClientCategoryData;

public final class TalentClient {
  static TalentScreen.SavedView lastView;

  private TalentClient() {}

  static int pointBalance(ClientCategoryData category) {
    // The provider clamps getPointsLeft() to zero for purchase checks.
    return Math.min(category.getEarnedPoints(), category.getConfig().spentPointsLimit())
        - category.getSpentPoints();
  }

  public enum ClassTree {
    WARRIOR("warrior", "combat"),
    RANGER("ranger", "ranger"),
    MAGE("mage", "magic");

    private final String name;
    private final ResourceLocation category;

    ClassTree(String name, String path) {
      this.name = name;
      this.category = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", path);
    }

    public ResourceLocation category() {
      return category;
    }

    Component title() {
      return Component.translatable("aeroscapes_reimagine.race.class." + name + ".name");
    }

    Component balance(int points) {
      return TalentScreen.text("class_balance." + name, points);
    }
  }

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
