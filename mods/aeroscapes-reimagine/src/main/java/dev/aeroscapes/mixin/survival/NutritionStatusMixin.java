package dev.aeroscapes.mixin.survival;

import com.dannyandson.nutritionalbalance.api.IPlayerNutrient;
import com.dannyandson.nutritionalbalance.api.IPlayerNutrient.NutrientStatus;
import dev.aeroscapes.survival.NutritionDeficits;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    targets = "com.dannyandson.nutritionalbalance.capabilities.DefaultNutritionalBalancePlayer",
    remap = false)
public abstract class NutritionStatusMixin {
  @Shadow private NutrientStatus cachedStatus;

  @Shadow
  public abstract List<IPlayerNutrient> getPlayerNutrients();

  @Inject(method = "getStatus", at = @At("RETURN"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$prioritizeDeficits(CallbackInfoReturnable<NutrientStatus> callback) {
    // The provider stops at the first excess or deficit, so an early excess can hide a deficit.
    if (callback.getReturnValue() == NutrientStatus.ENGORGED
        && NutritionDeficits.count(getPlayerNutrients()) > 0) {
      cachedStatus = NutrientStatus.MALNOURISHED;
      callback.setReturnValue(cachedStatus);
    }
  }
}
