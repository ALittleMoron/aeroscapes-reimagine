package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.enchanting.CurseCleansing;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$3")
abstract class GrindstoneCleansingSlotMixin {
  @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$allowCleansingReagent(
      ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
    if (CurseCleansing.isReagent(stack)) callback.setReturnValue(true);
  }
}
