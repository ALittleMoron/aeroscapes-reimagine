package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.aeroscapes.worldgen.BiomeTerrainMask;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Retains native search order while evaluating terrain only for matching climate candidates. */
@Mixin(BiomeSource.class)
public abstract class IslandBiomeSearchMixin {
  @WrapOperation(
      method = "findClosestBiome3d",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/biome/BiomeSource;getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Keeps the actual wrapped source and native loop.
  private Holder<Biome> aeroscapesReimagine$testClimateBeforeTerrain(
      BiomeSource source,
      int quartX,
      int quartY,
      int quartZ,
      Climate.Sampler sampler,
      Operation<Holder<Biome>> original,
      @Local Set<Holder<Biome>> matching,
      @Share("islandSearchSampler") LocalRef<Climate.Sampler> searchSampler) {
    BiomeTerrainMask mask =
        ((IslandBiomeSampler) (Object) sampler).aeroscapesReimagine$terrainMask();
    if (mask == null) return original.call(source, quartX, quartY, quartZ, sampler);
    Climate.Sampler deferred = searchSampler.get();
    if (deferred == null) {
      deferred =
          new Climate.Sampler(
              sampler.temperature(),
              sampler.humidity(),
              sampler.continentalness(),
              sampler.erosion(),
              sampler.depth(),
              sampler.weirdness(),
              sampler.spawnTarget());
      IslandBiomeSampler context = (IslandBiomeSampler) (Object) deferred;
      context.aeroscapesReimagine$terrainMask(mask);
      context.aeroscapesReimagine$deferTerrainMask(true);
      searchSampler.set(deferred);
    }
    IslandBiomeSampler context = (IslandBiomeSampler) (Object) deferred;
    context.aeroscapesReimagine$voidBiome(null);
    Holder<Biome> candidate = original.call(source, quartX, quartY, quartZ, deferred);
    Holder<Biome> empty = context.aeroscapesReimagine$voidBiome();
    if (empty == null) return candidate;
    if ((matching.contains(candidate) || matching.contains(empty))
        && !mask.nearTerrain(quartX * 4, quartY * 4, quartZ * 4)) return empty;
    return candidate;
  }
}
