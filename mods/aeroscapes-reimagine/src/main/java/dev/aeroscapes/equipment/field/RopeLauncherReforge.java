package dev.aeroscapes.equipment.field;

import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.world.item.ItemStack;

public final class RopeLauncherReforge {
  public static final String AIM_GUIDE = "rope_launcher_legendary_aim_guide";

  private RopeLauncherReforge() {}

  public static double offsetFactor(ItemStack launcher, boolean braced) {
    return switch (ReforgeProfiles.path(launcher)) {
      case "rope_launcher_poor_offset" -> 1.3;
      case "rope_launcher_fine_align" -> 0.65;
      case "rope_launcher_exceptional_align" -> braced ? 0.2 : 0.55;
      case "rope_launcher_epic_align", AIM_GUIDE -> braced ? 0.1 : 0.4;
      default -> 1.0;
    };
  }
}
