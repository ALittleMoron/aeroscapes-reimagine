package dev.aeroscapes.races;

import java.util.List;
import javax.annotation.Nullable;

public enum CharacterSubclass {
  DEFENDER("defender", CharacterClass.WARRIOR, "shield_shove", "wide_shield_shove"),
  BERSERKER("berserker", CharacterClass.WARRIOR, "last_stand", "offensive_stance");

  private final String id;
  private final CharacterClass mainClass;
  private final String starter;
  private final List<String> branch;

  CharacterSubclass(String id, CharacterClass mainClass, String starter, String upgrade) {
    this.id = id;
    this.mainClass = mainClass;
    this.starter = starter;
    branch = List.of(starter, upgrade);
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

  List<String> branch() {
    return branch;
  }

  public static List<CharacterSubclass> choices(@Nullable CharacterClass mainClass) {
    return mainClass == CharacterClass.WARRIOR ? List.of(DEFENDER, BERSERKER) : List.of();
  }

  @Nullable
  public static CharacterSubclass byId(String id) {
    return switch (id) {
      case "defender" -> DEFENDER;
      case "berserker" -> BERSERKER;
      default -> null;
    };
  }

  public static boolean validChoice(@Nullable CharacterClass mainClass, String id) {
    var selected = byId(id);
    return selected == null
        ? id.isEmpty() && choices(mainClass).isEmpty()
        : selected.mainClass() == mainClass;
  }
}
