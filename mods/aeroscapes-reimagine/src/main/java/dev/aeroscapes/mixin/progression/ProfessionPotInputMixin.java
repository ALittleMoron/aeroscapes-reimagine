package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.races.ProfessionPot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

@Mixin(
    targets = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity$1",
    remap = false)
abstract class ProfessionPotInputMixin extends ItemStackHandler {
  @Shadow @Final private CookingPotBlockEntity this$0;

  @Override
  public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    if (simulate || !ProfessionPot.tracks(this$0, slot))
      return super.insertItem(slot, stack, simulate);
    var before = new ProfessionPot(this$0);
    var remainder = super.insertItem(slot, stack, false);
    if (remainder.getCount() < stack.getCount()) before.afterClick(this$0, null, -1);
    return remainder;
  }

  @Override
  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    if (simulate || !ProfessionPot.tracks(this$0, slot))
      return super.extractItem(slot, amount, simulate);
    var before = new ProfessionPot(this$0);
    var extracted = super.extractItem(slot, amount, false);
    if (!extracted.isEmpty()) before.afterClick(this$0, null, -1);
    return extracted;
  }

  @Inject(method = "onContentsChanged", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$invalidateIngredient(int slot, CallbackInfo callback) {
    ProfessionPot.changed(this$0, slot);
  }
}
