package dev.aeroscapes.progression;

import java.util.Set;

public enum CombatDirection {
  MELEE(
      "melee",
      "melee_strike",
      "melee_onslaught",
      "melee_footing",
      "melee_veteran",
      "sword_handling",
      "advanced_sword_handling",
      "combat_axe_handling",
      "heavy_weapon_handling",
      "polearm_handling",
      "heavy_strike"),
  DEFENSE(
      "defense",
      "guard_armor",
      "guard_vitality",
      "guard_toughness",
      "quick_block",
      "shield_march",
      "shield_handling",
      "light_armor_handling",
      "heavy_armor_handling",
      "shield_brace",
      "counterattack"),
  RANGED(
      "ranged",
      "bow_stride",
      "steady_aim",
      "crossbow_handling",
      "starlight_bow_handling",
      "autoloader_handling",
      "ranger_armor_training",
      "slowing_shot",
      "marking_shot",
      "quick_shot"),
  LIGHT_WEAPONS(
      "light_weapons",
      "quick_recovery",
      "opening_strike",
      "light_weapon_handling",
      "rogue_armor_training",
      "disengage",
      "dash",
      "flank_strike");

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
