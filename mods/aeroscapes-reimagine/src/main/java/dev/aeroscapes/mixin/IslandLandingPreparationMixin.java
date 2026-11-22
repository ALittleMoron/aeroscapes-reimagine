package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.IslandStopStructure;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class IslandLandingPreparationMixin {
  @Inject(method = "applyCarvers", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes the callback.
  private void aeroscapes$prepareLanding(
      WorldGenRegion level,
      long seed,
      RandomState random,
      BiomeManager biomes,
      StructureManager structures,
      ChunkAccess chunk,
      GenerationStep.Carving step,
      CallbackInfo callback) {
    IslandStopStructure.prepareLanding(level, chunk);
  }
}
