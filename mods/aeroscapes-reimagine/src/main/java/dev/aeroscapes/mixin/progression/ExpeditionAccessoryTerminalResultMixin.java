package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.equipment.expedition.ExceptionalAccessory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tom.storagemod.menu.CraftingTerminalMenu$Result", remap = false)
abstract class ExpeditionAccessoryTerminalResultMixin {
  @Inject(method = "onTake", at = @At("HEAD"), require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$finishAccessory(Player player, ItemStack result, CallbackInfo callback) {
    ExceptionalAccessory.completeCraft(result, player);
  }
}
