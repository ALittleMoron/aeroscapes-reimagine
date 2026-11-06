package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.atmosphere.TooltipScrollBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.stalemated.sts.scroll.TooltipScrollManager", remap = false)
public abstract class TooltipScrollBoundaryMixin {
  @Shadow private static int targetScroll;

  @WrapMethod(method = "scroll")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // A clamped tooltip yields to its outer container.
  private static boolean aeroscapesReimagine$releaseAtBoundary(
      double amount, Operation<Boolean> original) {
    return TooltipScrollBoundary.scroll(amount, () -> targetScroll, value -> original.call(value));
  }
}
