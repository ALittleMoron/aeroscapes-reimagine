package dev.aeroscapes.mixin.engineering.steam;

import com.teammoeg.steampowered.content.boiler.BoilerTileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BoilerTileEntity.class, remap = false)
public abstract class BoilerHeatClampMixin {
  @Redirect(
      method = "getHeatFromHeater",
      at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the Mixin injector.
  private float aeroscapes$capExternalHeat(float heat, float maximum) {
    return Math.min(heat, maximum);
  }
}
