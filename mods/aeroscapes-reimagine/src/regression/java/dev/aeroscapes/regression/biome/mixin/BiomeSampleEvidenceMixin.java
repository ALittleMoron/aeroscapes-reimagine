package dev.aeroscapes.regression.biome.mixin;

import dev.aeroscapes.regression.biome.BiomeSampleEvidence;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reads completed samples without evaluating the sampler or biome resolver again. */
@Mixin(Climate.Sampler.class)
public abstract class BiomeSampleEvidenceMixin {
  @Inject(method = "sample", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin observes the sampler's actual return value.
  private void aeroscapesRegression$sampled(
      int quartX, int quartY, int quartZ, CallbackInfoReturnable<Climate.TargetPoint> callback) {
    BiomeSampleEvidence.sampled(quartX, quartY, quartZ, callback.getReturnValue());
  }
}
