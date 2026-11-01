package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.atmosphere.DistantSceneryPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = "com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper_neoforge",
    remap = false)
public abstract class DistantHorizonsLightMaterialMixin {
  @ModifyReturnValue(method = "getMaterialId()B", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Called by the pinned wrapper getter.
  private byte aeroscapes$material(byte original) {
    return DistantSceneryPolicy.material(original);
  }
}
