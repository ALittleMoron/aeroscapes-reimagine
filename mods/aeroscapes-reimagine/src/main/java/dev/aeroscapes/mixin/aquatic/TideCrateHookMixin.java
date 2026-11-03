package dev.aeroscapes.mixin.aquatic;

import com.li64.tide.data.fishing.CrateData;
import com.li64.tide.registries.entities.misc.fishing.TideFishingHook;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TideFishingHook.class, remap = false)
public abstract class TideCrateHookMixin {
  @Shadow private CrateData crateData;
  @Shadow protected TideFishingHook.CatchType catchType;

  @ModifyReturnValue(method = "hasHookedItem", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked at the native retrieval presence check.
  private boolean aeroscapes$crateHasBlockReward(boolean present) {
    return present || (crateData != null && catchType == TideFishingHook.CatchType.CRATE);
  }
}
