package dev.aeroscapes.races;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.neoforged.fml.ModList;

public enum CharacterClass {
  WARRIOR("combat", "quick_block", "shield_march"),
  RANGER("archery", "bow_stride", "steady_aim"),
  MAGE("magic", "first_mastery"),
  ROGUE("combat", "quick_recovery", "opening_strike");

  private final String practiceSkill;
  private final List<String> starters;

  CharacterClass(String practiceSkill, String... starters) {
    this.practiceSkill = practiceSkill;
    this.starters = List.of(starters);
  }

  public static boolean creationAvailable() {
    var mods = ModList.get();
    return mods.isLoaded("neoorigins")
        && mods.isLoaded("pehkui")
        && mods.isLoaded("pmmo")
        && mods.isLoaded("puffish_skills")
        && mods.isLoaded("irons_spellbooks")
        && mods.isLoaded("curios");
  }

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public String practiceSkill() {
    return practiceSkill;
  }

  public List<String> starters() {
    return starters;
  }

  @Nullable
  public static CharacterClass byId(String id) {
    return switch (id) {
      case "warrior" -> WARRIOR;
      case "ranger" -> RANGER;
      case "mage" -> MAGE;
      case "rogue" -> ROGUE;
      default -> null;
    };
  }
}
