package dev.aeroscapes.mixin.ecology;

import com.terraformersmc.biolith.api.biome.sub.BiomeParameterTargets;
import com.terraformersmc.biolith.api.biome.sub.Criterion;
import com.terraformersmc.biolith.api.biome.sub.CriterionBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "uwu.juni.wetland_whimsy.events.WetlandWhimsyBootstap", remap = false)
public abstract class WetlandMarshCriterionMixin {
  @ModifyArg(
      method = "marshification",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/terraformersmc/biolith/api/biome/BiomePlacement;addSubOverworld(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;Lcom/terraformersmc/biolith/api/biome/sub/Criterion;)V"),
      index = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned registration call site.
  private static Criterion aeroscapes$reachableMarsh(Criterion original) {
    // The native -.44 deviation lies outside the retained delegate's swamp leaves.
    return CriterionBuilder.deviationMax(BiomeParameterTargets.CONTINENTALNESS, 0.0f);
  }
}
