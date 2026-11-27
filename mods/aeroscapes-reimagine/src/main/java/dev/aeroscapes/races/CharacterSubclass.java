package dev.aeroscapes.races;

import java.util.List;
import javax.annotation.Nullable;

public enum CharacterSubclass {
  DEFENDER("defender", CharacterClass.WARRIOR, "shield_shove"),
  BERSERKER("berserker", CharacterClass.WARRIOR, "last_stand"),
  MARKSMAN("marksman", CharacterClass.RANGER, "triple_shot"),
  BEASTMASTER("beastmaster", CharacterClass.RANGER, "beastmaster_fox"),
  ELEMENTALIST("elementalist", CharacterClass.MAGE, "irons_spellbooks:icicle"),
  NECROMANCER("necromancer", CharacterClass.MAGE, "irons_spellbooks:blood_needles");

  private final String id;
  private final CharacterClass mainClass;
  private final String starter;

  CharacterSubclass(String id, CharacterClass mainClass, String starter) {
    this.id = id;
    this.mainClass = mainClass;
    this.starter = starter;
  }

  public String id() {
    return id;
  }

  public CharacterClass mainClass() {
    return mainClass;
  }

  public String starter() {
    return starter;
  }

  public boolean isSpellStarter() {
    return mainClass == CharacterClass.MAGE;
  }

  public static List<CharacterSubclass> choices(@Nullable CharacterClass mainClass) {
    if (mainClass == null) return List.of();
    return switch (mainClass) {
      case WARRIOR -> List.of(DEFENDER, BERSERKER);
      case RANGER -> List.of(MARKSMAN, BEASTMASTER);
      case MAGE -> List.of(ELEMENTALIST, NECROMANCER);
    };
  }

  @Nullable
  public static CharacterSubclass byId(String id) {
    return switch (id) {
      case "defender" -> DEFENDER;
      case "berserker" -> BERSERKER;
      case "marksman" -> MARKSMAN;
      case "beastmaster" -> BEASTMASTER;
      case "elementalist" -> ELEMENTALIST;
      case "necromancer" -> NECROMANCER;
      default -> null;
    };
  }

  public static boolean validChoice(@Nullable CharacterClass mainClass, String id) {
    var selected = byId(id);
    return id.isEmpty() || selected != null && selected.mainClass() == mainClass;
  }
}
