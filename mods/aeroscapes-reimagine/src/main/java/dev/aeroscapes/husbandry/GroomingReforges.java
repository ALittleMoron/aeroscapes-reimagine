package dev.aeroscapes.husbandry;

import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.world.item.ItemStack;

public final class GroomingReforges {
  private GroomingReforges() {}

  public static int happiness(ItemStack brush, int nativeGain) {
    return Math.max(0, Math.round(nativeGain * multiplier(ReforgeProfiles.basePath(brush))));
  }

  public static float multiplier(String profile) {
    return switch (profile) {
      case "grooming_poor" -> 0.75F;
      case "grooming_fine" -> 1.25F;
      case "grooming_exceptional" -> 1.5F;
      case "grooming_epic" -> 2.0F;
      case "grooming_legendary" -> 2.5F;
      default -> 1.0F;
    };
  }
}
