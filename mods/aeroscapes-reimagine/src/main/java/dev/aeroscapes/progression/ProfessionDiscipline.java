package dev.aeroscapes.progression;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Associates each available profession tree with the practice that funds it. */
public enum ProfessionDiscipline {
  SMITHING("smithing", 12, "material_repair", "frugal_repair", "field_repair"),
  MINING("mining", 2, "mining_efficiency", "mining_speed", "mining_fortune"),
  BUILDING("building", 3, "long_reach", "quick_dismantling", "careful_dismantling"),
  FARMING(
      "farming",
      5,
      "extra_harvest",
      "careful_tilling",
      "animal_lure",
      "animal_lure_radius",
      "animal_lure_duration"),
  COOKING("cooking", 2, "hearty_meal", "quick_meal"),
  ENCHANTING("enchanting", 7),
  PICKAXE_HANDLING("pickaxe_handling", 5),
  AXE_HANDLING("axe_handling", 5),
  SHOVEL_HANDLING("shovel_handling", 5),
  HOE_HANDLING("hoe_handling", 5),
  SHEARS_HANDLING("shears_handling", 1);

  private static final int[] HANDLING_POINT_LEVELS = {1, 2, 4, 6, 10};

  private final ResourceLocation category;
  private final int cap;
  private final Set<String> legacyNodes;

  ProfessionDiscipline(String skill, int cap, String... legacyNodes) {
    this.category = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", skill);
    this.cap = cap;
    this.legacyNodes = Set.of(legacyNodes);
  }

  public ResourceLocation category() {
    return category;
  }

  public String skill() {
    return category.getPath();
  }

  public int cap() {
    return cap;
  }

  public boolean toolHandling() {
    return section() != this;
  }

  public ProfessionDiscipline section() {
    return switch (this) {
      case PICKAXE_HANDLING -> MINING;
      case AXE_HANDLING, SHOVEL_HANDLING -> BUILDING;
      case HOE_HANDLING, SHEARS_HANDLING -> FARMING;
      default -> this;
    };
  }

  public int earned(long level, boolean starterCredit) {
    if (toolHandling()) {
      int points = 0;
      while (points < cap && level >= HANDLING_POINT_LEVELS[points]) points++;
      return points;
    }
    int practice = Math.clamp(level, 0, 50) / ProgressionBridge.LEVELS_PER_POINT;
    if (this == SMITHING) {
      if (level >= 1) practice++;
      if (level >= 28) practice++;
    } else if (this == COOKING && level >= 3) practice = Math.max(1, practice);
    return Math.min(cap, practice + (starterCredit ? 1 : 0));
  }

  public static Optional<ProfessionDiscipline> bySkill(String skill) {
    return Arrays.stream(values()).filter(tree -> tree.skill().equals(skill)).findFirst();
  }

  public static Optional<ProfessionDiscipline> byLegacyNode(String node) {
    return Arrays.stream(values()).filter(tree -> tree.legacyNodes.contains(node)).findFirst();
  }
}
