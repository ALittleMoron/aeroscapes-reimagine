package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.races.ProfessionEffects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;

@Mixin(
    targets = "vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity$1",
    remap = false)
abstract class ProfessionSkilletInputMixin {
  @Shadow @Final private SkilletBlockEntity this$0;

  @Inject(method = "onContentsChanged", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$trackPortions(int slot, CallbackInfo callback) {
    ProfessionEffects.cookingSlotChanged(this$0, slot, this$0.getStoredStack().getCount());
  }
}
