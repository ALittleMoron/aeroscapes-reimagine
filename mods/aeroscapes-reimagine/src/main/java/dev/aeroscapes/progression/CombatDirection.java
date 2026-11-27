package dev.aeroscapes.progression;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public enum CombatDirection {
  MELEE(
      ProgressionBridge.COMBAT_CATEGORY,
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
      "heavy_strike",
      "last_stand",
      "offensive_stance"),
  DEFENSE(
      ProgressionBridge.COMBAT_CATEGORY,
      "defense",
      "guard_armor",
      "guard_vitality",
      "guard_toughness",
      "quick_block",
      "shield_march",
      "shield_handling",
      "heavy_armor_handling",
      "shield_brace",
      "counterattack",
      "shield_shove",
      "wide_shield_shove"),
  RANGED(
      ProgressionBridge.RANGER_CATEGORY,
      "ranged",
      "bow_stride",
      "steady_aim",
      "crossbow_handling",
      "starlight_bow_handling",
      "autoloader_handling",
      "ranger_armor_training",
      "slowing_shot",
      "marking_shot",
      "quick_shot",
      "triple_shot"),
  LIGHT_WEAPONS(
      ProgressionBridge.RANGER_CATEGORY,
      "light_weapons",
      "quick_recovery",
      "opening_strike",
      "light_weapon_handling",
      "light_armor_handling",
      "rogue_armor_training",
      "disengage",
      "dash",
      "flank_strike",
      "duelist_parry",
      "duelist_sidestep");

  private final ResourceLocation category;
  private final String key;
  private final Set<String> nodes;

  CombatDirection(String category, String key, String... nodes) {
    this.category = ResourceLocation.parse(category);
    this.key = key;
    this.nodes = Set.of(nodes);
  }

  public ResourceLocation category() {
    return category;
  }

  @Nullable
  public static ResourceLocation categoryForNode(String node) {
    if (node.startsWith("beastmaster_")) return RANGED.category;
    for (var direction : values()) {
      if (direction.nodes.contains(node)) return direction.category;
    }
    return null;
  }

  public String key() {
    return key;
  }

  public Set<String> nodes() {
    return nodes;
  }
}
