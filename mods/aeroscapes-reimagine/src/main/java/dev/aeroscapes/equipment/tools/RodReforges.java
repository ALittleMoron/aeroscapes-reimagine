package dev.aeroscapes.equipment.tools;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RodReforges {
  private RodReforges() {}

  public static int biteWait(int ticks, String profile) {
    return switch (profile) {
      case "rod_poor", "rod_poor_with_short_window" -> Math.round(ticks * 1.10F);
      case "rod_fine", "rod_fine_with_steady_window" -> Math.max(10, Math.round(ticks * 0.94F));
      case "rod_exceptional_bite", "rod_exceptional_bite_with_conservation" ->
          Math.max(10, Math.round(ticks * 0.88F));
      case "rod_epic_reel",
          "rod_epic_reel_with_steady_window",
          "rod_epic_bait",
          "rod_legendary_catch",
          "rod_legendary_salvager" ->
          Math.max(10, Math.round(ticks * 0.92F));
      default -> ticks;
    };
  }

  public static int biteWindow(int ticks, String profile) {
    return switch (profile) {
      case "rod_poor_with_short_window" -> Math.round(ticks * 0.85F);
      case "rod_ordinary_with_steady_window" -> Math.round(ticks * 1.05F);
      case "rod_fine_with_steady_window" -> Math.round(ticks * 1.10F);
      case "rod_exceptional_window" -> Math.round(ticks * 1.18F);
      case "rod_exceptional_window_with_steady_window" -> Math.round(ticks * 1.24F);
      case "rod_epic_reel_with_steady_window" -> Math.round(ticks * 1.30F);
      case "rod_epic_reel", "rod_legendary_catch", "rod_legendary_salvager" ->
          Math.round(ticks * 1.10F);
      default -> ticks;
    };
  }

  public static boolean trySalvage(ItemEntity drop, RandomSource random) {
    ItemStack item = drop.getItem();
    if (item.getCount() != 1
        || !item.getComponentsPatch().isEmpty()
        || (!item.is(Items.STICK) && !item.is(Items.ROTTEN_FLESH) && !item.is(Items.BOWL)))
      return false;
    if (random.nextFloat() < 0.25F) drop.setItem(new ItemStack(Items.IRON_NUGGET));
    return true;
  }

  public interface VanillaCast {
    void aeroscapes$rememberRodProfile(String profile);
  }
}
