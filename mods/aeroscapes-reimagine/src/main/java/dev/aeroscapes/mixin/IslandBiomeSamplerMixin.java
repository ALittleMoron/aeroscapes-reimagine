package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.BiomeTerrainMask;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Climate.Sampler.class)
public abstract class IslandBiomeSamplerMixin implements IslandBiomeSampler {
  @Unique @Nullable private volatile BiomeTerrainMask aeroscapesReimagine$terrainMask;
  @Unique private boolean aeroscapesReimagine$deferred;
  @Unique @Nullable private volatile Holder<Biome> aeroscapesReimagine$voidBiome;

  @Override
  @Nullable
  public BiomeTerrainMask aeroscapesReimagine$terrainMask() {
    return aeroscapesReimagine$terrainMask;
  }

  @Override
  public void aeroscapesReimagine$terrainMask(@Nullable BiomeTerrainMask mask) {
    aeroscapesReimagine$terrainMask = mask;
  }

  @Override
  public boolean aeroscapesReimagine$deferTerrainMask() {
    return aeroscapesReimagine$deferred;
  }

  @Override
  public void aeroscapesReimagine$deferTerrainMask(boolean deferred) {
    aeroscapesReimagine$deferred = deferred;
  }

  @Override
  @Nullable
  public Holder<Biome> aeroscapesReimagine$voidBiome() {
    return aeroscapesReimagine$voidBiome;
  }

  @Override
  public void aeroscapesReimagine$voidBiome(@Nullable Holder<Biome> biome) {
    aeroscapesReimagine$voidBiome = biome;
  }
}
