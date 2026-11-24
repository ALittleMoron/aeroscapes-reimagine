package dev.aeroscapes.progression;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Associates each available profession tree with the practice that funds it. */
public enum ProfessionDiscipline {
  SMITHING("smithing", 11, "material_repair", "frugal_repair"),
  MINING("mining", 2, "mining_efficiency", "mining_speed", "mining_fortune"),
  BUILDING("building", 2, "long_reach", "quick_dismantling"),
  FARMING("farming", 2, "extra_harvest", "careful_tilling"),
  COOKING("cooking", 2, "hearty_meal", "quick_meal");

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

  public int earned(long level, boolean starterCredit) {
    int practice = Math.clamp(level, 0, 50) / ProgressionBridge.LEVELS_PER_POINT;
    if (this == SMITHING && level >= 1) practice++;
    return Math.min(cap, practice + (starterCredit ? 1 : 0));
  }

  public static Optional<ProfessionDiscipline> bySkill(String skill) {
    return Arrays.stream(values()).filter(tree -> tree.skill().equals(skill)).findFirst();
  }

  public static Optional<ProfessionDiscipline> byLegacyNode(String node) {
    return Arrays.stream(values()).filter(tree -> tree.legacyNodes.contains(node)).findFirst();
  }
}
