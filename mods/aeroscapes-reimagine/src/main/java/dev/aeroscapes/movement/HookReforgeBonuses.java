package dev.aeroscapes.movement;

import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.world.item.ItemStack;

public record HookReforgeBonuses(int range, int wearSaving, int hungerSaving) {
  private static final HookReforgeBonuses NONE = new HookReforgeBonuses(0, 0, 0);
  private static final HookReforgeBonuses FINE = new HookReforgeBonuses(1, 10, 10);
  private static final HookReforgeBonuses EXCELLENT = new HookReforgeBonuses(2, 15, 15);
  private static final HookReforgeBonuses EPIC = new HookReforgeBonuses(3, 20, 20);
  private static final HookReforgeBonuses LEGENDARY = new HookReforgeBonuses(4, 25, 25);

  public static HookReforgeBonuses of(ItemStack stack) {
    return switch (ReforgeProfiles.basePath(stack)) {
      case "hook_fine" -> FINE;
      case "hook_exceptional" -> EXCELLENT;
      case "hook_epic_ascent", "hook_epic_descent" -> EPIC;
      case "hook_legendary_stabilizer" -> LEGENDARY;
      default -> NONE;
    };
  }

  public float applyHungerSaving(float exhaustion) {
    return exhaustion * ((100 - hungerSaving) / 100.0F);
  }
}
