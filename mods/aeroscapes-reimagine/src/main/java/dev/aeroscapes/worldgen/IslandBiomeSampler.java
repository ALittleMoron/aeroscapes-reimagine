package dev.aeroscapes.worldgen;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Associates a climate sampler with the same world's native terrain, including C2ME clones. */
public interface IslandBiomeSampler {
  @Nullable
  BiomeTerrainMask aeroscapesReimagine$terrainMask();

  void aeroscapesReimagine$terrainMask(@Nullable BiomeTerrainMask mask);

  boolean aeroscapesReimagine$deferTerrainMask();

  void aeroscapesReimagine$deferTerrainMask(boolean deferred);

  @Nullable
  Holder<Biome> aeroscapesReimagine$voidBiome();

  void aeroscapesReimagine$voidBiome(@Nullable Holder<Biome> biome);
}
