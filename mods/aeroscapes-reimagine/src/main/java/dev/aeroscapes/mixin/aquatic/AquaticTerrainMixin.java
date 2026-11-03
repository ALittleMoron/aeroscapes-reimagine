package dev.aeroscapes.mixin.aquatic;

import dev.aeroscapes.aquatic.AquaticWater;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = {
      "dev.hybridlabs.aquatic.world.gen.densityfunction.DensityFunctionWrappers",
      "dev.hybridlabs.aquatic.world.gen.densityfunction.TrenchCarver"
    },
    remap = false)
public abstract class AquaticTerrainMixin {
  @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Version-gated upstream entry points.
  private static void aeroscapes$keepSealedBasins(MinecraftServer server, CallbackInfo callback) {
    var overworld =
        server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
    if (overworld != null && AquaticWater.ownedSource(overworld.generator().getBiomeSource())) {
      callback.cancel();
    }
  }
}
