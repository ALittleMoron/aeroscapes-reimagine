package dev.aeroscapes.races;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;

public final class ProfessionRecipeTransfer {
  private ProfessionRecipeTransfer() {}

  /** The viewer can fill a menu directly, outside the native click and recipe book handlers. */
  public static Runnable afterTransfer(Player player) {
    if (player instanceof ServerPlayer cook && !(cook instanceof FakePlayer)) {
      if (cook.containerMenu instanceof CookingPotMenu pot) {
        var ingredients = new ProfessionPot(pot.blockEntity);
        return () -> ingredients.afterClick(pot.blockEntity, cook, -1);
      }
      if (cook.containerMenu instanceof AbstractFurnaceMenu menu
          && menu.getSlot(0).container instanceof AbstractFurnaceBlockEntity furnace) {
        var ingredient = new ProfessionFurnace(furnace);
        return () -> ingredient.afterClick(furnace, cook, false);
      }
    }
    return () -> {};
  }
}
