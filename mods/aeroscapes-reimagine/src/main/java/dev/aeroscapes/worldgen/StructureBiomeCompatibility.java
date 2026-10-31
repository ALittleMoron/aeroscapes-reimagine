package dev.aeroscapes.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Retains native climate eligibility for underground prefit checks and preferred-biome rings. */
public final class StructureBiomeCompatibility {
  private StructureBiomeCompatibility() {}

  public static Climate.Sampler deferredSampler(Climate.Sampler sampler) {
    IslandBiomeSampler original = (IslandBiomeSampler) (Object) sampler;
    BiomeTerrainMask mask = original.aeroscapesReimagine$terrainMask();
    if (mask == null || original.aeroscapesReimagine$deferTerrainMask()) return sampler;
    Climate.Sampler copy =
        new Climate.Sampler(
            sampler.temperature(),
            sampler.humidity(),
            sampler.continentalness(),
            sampler.erosion(),
            sampler.depth(),
            sampler.weirdness(),
            sampler.spawnTarget());
    IslandBiomeSampler context = (IslandBiomeSampler) (Object) copy;
    context.aeroscapesReimagine$terrainMask(mask);
    context.aeroscapesReimagine$deferTerrainMask(true);
    return copy;
  }

  public static Structure.GenerationContext prefitContext(Structure.GenerationContext context) {
    Climate.Sampler original = context.randomState().sampler();
    Climate.Sampler deferred = deferredSampler(original);
    if (deferred == original) return context;
    return new Structure.GenerationContext(
        context.registryAccess(),
        context.chunkGenerator(),
        new PrefitSource(context.biomeSource(), deferred),
        context.randomState(),
        context.structureTemplateManager(),
        context.random(),
        context.seed(),
        context.chunkPos(),
        context.heightAccessor(),
        context.validBiome());
  }

  public static Climate.Sampler prefitSampler(
      Structure.GenerationContext context, Climate.Sampler original) {
    return context.biomeSource() instanceof PrefitSource prefit ? prefit.sampler : original;
  }

  private static final class PrefitSource extends BiomeSource {
    private final BiomeSource delegate;
    private final Climate.Sampler sampler;

    private PrefitSource(BiomeSource delegate, Climate.Sampler sampler) {
      this.delegate = delegate;
      this.sampler = sampler;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
      throw new UnsupportedOperationException("Invocation-local prefit source is not serialized");
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
      return delegate.possibleBiomes().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler ignored) {
      return delegate.getNoiseBiome(x, y, z, sampler);
    }
  }
}
