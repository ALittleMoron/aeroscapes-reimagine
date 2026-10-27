package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.IslandBiomeSource;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class IslandBiomeGenerationMixin {
  @ModifyArg(
      method = "doCreateBiomes",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Runs after Lithostitched unwraps its injector.
  private BiomeResolver aeroscapesReimagine$cloneDelegate(BiomeResolver resolver) {
    return resolver instanceof IslandBiomeSource source ? source.forGeneration() : resolver;
  }
}
