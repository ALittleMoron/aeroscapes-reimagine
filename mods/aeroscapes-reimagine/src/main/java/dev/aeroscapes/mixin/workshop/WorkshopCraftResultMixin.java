package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.equipment.workshop.WorkshopQuality;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
abstract class WorkshopCraftResultMixin {
  @Inject(method = "onTake", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$finishManualCraft(
      Player player, ItemStack result, CallbackInfo callback) {
    var inputs = ((CraftingResultInputAccess) this).aeroscapes$craftInput().getItems();
    if (inputs.stream().noneMatch(input -> input.is(result.getItem())))
      WorkshopQuality.finish(player, result);
  }
}
