package dev.aeroscapes.mixin.aquatic;

import com.li64.tide.data.fishing.CatchResult;
import com.li64.tide.data.fishing.CrateData;
import com.li64.tide.data.fishing.selector.FishingEntry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.Optional;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CatchResult.class, remap = false)
public abstract class TideCrateResultMixin {
  @Shadow @Final private Optional<FishingEntry> entry;

  @ModifyReturnValue(method = "isEmpty", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by native crate selector eligibility.
  private boolean aeroscapes$crateHasBlockReward(boolean empty) {
    return empty && !(entry.orElse(null) instanceof CrateData);
  }
}
