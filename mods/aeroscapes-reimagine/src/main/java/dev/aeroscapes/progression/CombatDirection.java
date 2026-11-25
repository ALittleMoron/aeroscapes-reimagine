package dev.aeroscapes.progression;

import java.util.Set;

public enum CombatDirection {
  MELEE("melee", "melee_strike", "melee_onslaught", "melee_footing", "melee_veteran"),
  DEFENSE(
      "defense", "guard_armor", "guard_vitality", "guard_toughness", "quick_block", "shield_march"),
  RANGED(
      "ranged",
      "bow_stride",
      "steady_aim",
      "crossbow_handling",
      "starlight_bow_handling",
      "autoloader_handling"),
  LIGHT_WEAPONS("light_weapons", "quick_recovery", "opening_strike");

  private final String key;
  private final Set<String> nodes;

  CombatDirection(String key, String... nodes) {
    this.key = key;
    this.nodes = Set.of(nodes);
  }

  public String key() {
    return key;
  }

  public Set<String> nodes() {
    return nodes;
  }
}
