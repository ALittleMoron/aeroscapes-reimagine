package dev.aeroscapes.equipment.engineering;

import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Reduces only the provider's manual-opening burn roll, never fluid quantity or capacity. */
public final class LadleReforge {
  private LadleReforge() {}

  public static boolean applyNativeBurn(ItemStack ladle, RandomSource random) {
    int percent =
        switch (ReforgeProfiles.basePath(ladle)) {
          case "ladle_fine" -> 75;
          case "ladle_exceptional" -> 50;
          case "ladle_epic" -> 25;
          case "ladle_legendary" -> 0;
          default -> 100;
        };
    return percent == 100 || (percent > 0 && random.nextInt(100) < percent);
  }
}
