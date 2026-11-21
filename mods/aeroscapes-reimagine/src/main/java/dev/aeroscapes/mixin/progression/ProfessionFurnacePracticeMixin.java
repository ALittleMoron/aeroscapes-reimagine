package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.progression.PracticeActions;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
abstract class ProfessionFurnacePracticeMixin extends Slot {
  @Shadow @Final private Player player;
  @Shadow private int removeCount;

  protected ProfessionFurnacePracticeMixin(Container container, int slot, int x, int y) {
    super(container, slot, x, y);
  }

  // The native smelted event can include items left in the output slot after a partial transfer.
  @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$countTaken(ItemStack stack, CallbackInfo callback) {
    PracticeActions.takeSmelting(player, stack, removeCount);
  }

  @Override
  protected void onSwapCraft(int amount) {
    removeCount += amount;
  }
}
