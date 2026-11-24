package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.equipment.workshop.WorkshopQuality;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tom.storagemod.menu.CraftingTerminalMenu$Result", remap = false)
abstract class WorkshopTerminalResultMixin {
  @Inject(method = "onTake", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$finishTerminalCraft(
      Player player, ItemStack result, CallbackInfo callback) {
    var inputs = ((CraftingResultInputAccess) this).aeroscapes$craftInput().getItems();
    if (inputs.stream().noneMatch(input -> input.is(result.getItem())))
      WorkshopQuality.finish(player, result);
  }
}
