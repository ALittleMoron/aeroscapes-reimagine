package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.ninni.spawn.server.data.DodoIslandSavedData;
import dev.aeroscapes.ecology.SpawnIslandGenerator;
import dev.aeroscapes.ecology.SpawnIslandSites;
import dev.aeroscapes.worldgen.IslandBiomeSources;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DodoIslandSavedData.class)
public abstract class SpawnDodoSearchMixin {
  @WrapMethod(method = "findIslandChunk")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Adjacent native candidates share immutable terrain.
  private static ChunkPos aeroscapes$searchColumns(
      long seed,
      Climate.Sampler sampler,
      BiomeSource source,
      ChunkGeneratorStructureState state,
      Operation<ChunkPos> original) {
    if (state == null || IslandTerrain.from(state.randomState()) == null)
      return original.call(seed, sampler, source, state);
    var generator = ((SpawnIslandGenerator) state).aeroscapes$spawnGenerator();
    if (generator == null)
      throw new MixinInitialisationError("Dodo search has no actual island generator");
    return SpawnIslandSites.search(
        generator,
        state.randomState(),
        LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth()),
        () -> original.call(seed, sampler, source, state));
  }

  @WrapOperation(
      method = "findIslandChunk",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/tags/TagKey;)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep native annulus, spiral order, cache and monument exclusion.
  private static boolean aeroscapes$physicalDodoSite(
      Holder<Biome> biome,
      TagKey<Biome> tag,
      Operation<Boolean> original,
      @Local(argsOnly = true) BiomeSource source,
      @Local(argsOnly = true) Climate.Sampler sampler,
      @Local(argsOnly = true) ChunkGeneratorStructureState state,
      @Local(name = "monuments") Holder<StructureSet> monuments,
      @Local(name = "pos") ChunkPos position) {
    if (state == null || IslandTerrain.from(state.randomState()) == null)
      return original.call(biome, tag);
    int centerX = position.getMiddleBlockX();
    int centerZ = position.getMiddleBlockZ();
    int sea = SpawnIslandSites.seaLevel(state.randomState(), centerX, centerZ, Integer.MIN_VALUE);
    if (sea == Integer.MIN_VALUE) return false;
    var selected = source.getNoiseBiome(centerX >> 2, sea >> 2, centerZ >> 2, sampler);
    if (!original.call(selected, tag)) {
      var islands = IslandBiomeSources.find(source);
      var family = islands == null ? null : islands.basinOcean(centerX, centerZ, sampler);
      if (family == null || !original.call(family, tag)) return false;
    }
    if (monuments != null && state.hasStructureChunkInRange(monuments, position.x, position.z, 8))
      return false;
    var generator = ((SpawnIslandGenerator) state).aeroscapes$spawnGenerator();
    if (generator == null)
      throw new MixinInitialisationError("Dodo search has no actual island generator");
    return SpawnIslandSites.accepts(
        generator,
        state.randomState(),
        LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth()),
        centerX,
        centerZ,
        51);
  }
}
