package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.equipment.expedition.ExceptionalAccessory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
abstract class ExpeditionAccessoryResultMixin {
  @Inject(method = "onTake", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$finishAccessory(Player player, ItemStack result, CallbackInfo callback) {
    ExceptionalAccessory.completeCraft(result, player);
  }
}
