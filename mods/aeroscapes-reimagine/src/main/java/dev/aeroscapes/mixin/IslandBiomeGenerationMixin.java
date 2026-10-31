package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.IslandBiomeSampler;
import dev.aeroscapes.worldgen.IslandBiomeSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class IslandBiomeGenerationMixin {
  @ModifyArgs(
      method = "doCreateBiomes",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Runs after Lithostitched unwraps its injector.
  private void aeroscapesReimagine$cloneDelegate(
      Args arguments,
      Blender blender,
      RandomState random,
      StructureManager structures,
      ChunkAccess chunk) {
    BiomeResolver resolver = arguments.get(0);
    if (resolver instanceof IslandBiomeSource source) {
      arguments.set(0, source.forGeneration());
    }
    Climate.Sampler sampler = arguments.get(1);
    ((IslandBiomeSampler) (Object) sampler)
        .aeroscapesReimagine$terrainMask(
            ((IslandBiomeSampler) (Object) random.sampler()).aeroscapesReimagine$terrainMask());
  }
}
