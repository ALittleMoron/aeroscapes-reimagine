package dev.aeroscapes.worldgen;

import javax.annotation.Nullable;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Find the owned source while preserving native Blueprint slices and Lithostitched injectors. */
public final class IslandBiomeSources {
  private IslandBiomeSources() {}

  @Nullable
  public static IslandBiomeSource find(BiomeSource source) {
    while (source instanceof BiomeSourceDelegate wrapper) source = wrapper.aeroscapes$delegate();
    return source instanceof IslandBiomeSource islands ? islands : null;
  }

  public static Climate.Sampler forGeneration(
      BiomeResolver resolver, Climate.Sampler original, @Nullable BiomeTerrainMask mask) {
    IslandBiomeSource source =
        resolver instanceof BiomeSource biomeSource ? find(biomeSource) : null;
    if (source == null) {
      ((IslandBiomeSampler) (Object) original).aeroscapesReimagine$terrainMask(mask);
      return original;
    }
    var sampler =
        new Climate.Sampler(
            original.temperature(),
            original.humidity(),
            original.continentalness(),
            original.erosion(),
            original.depth(),
            original.weirdness(),
            original.spawnTarget());
    IslandBiomeSampler context = (IslandBiomeSampler) (Object) sampler;
    IslandBiomeSampler previous = (IslandBiomeSampler) (Object) original;
    context.aeroscapesReimagine$terrainMask(mask);
    context.aeroscapesReimagine$voidBiome(previous.aeroscapesReimagine$voidBiome());
    context.aeroscapesReimagine$deferTerrainMask(previous.aeroscapesReimagine$deferTerrainMask());
    context.aeroscapesReimagine$generationSource(source.forGeneration());
    return sampler;
  }
}
