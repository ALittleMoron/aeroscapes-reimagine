package dev.aeroscapes.regression.biome.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.regression.biome.EcologySurfaceEvidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(
    targets =
        "net.minecraft.world.level.levelgen.SurfaceRules$Context$AbovePreliminarySurfaceCondition")
public abstract class SurfaceConditionProbeMixin {
  @WrapOperation(
      method = "test",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;getMinSurfaceLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Observe the returned cutoff without changing it.
  private int aeroscapes$condition(@Coerce Object context, Operation<Integer> original) {
    int cutoff = original.call(context);
    EcologySurfaceEvidence.record((SurfaceContextProbeAccess) context, cutoff);
    return cutoff;
  }
}
