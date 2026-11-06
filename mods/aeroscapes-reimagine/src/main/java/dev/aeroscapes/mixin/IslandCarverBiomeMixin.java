package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.IslandCarvingContext;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class IslandCarverBiomeMixin {
  @WrapOperation(
      method = "applyCarvers",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/carver/ConfiguredWorldCarver;carve(Lnet/minecraft/world/level/levelgen/carver/CarvingContext;Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/Aquifer;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/CarvingMask;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // A separate native pass per physical island layer.
  private boolean aeroscapes$carveLayers(
      ConfiguredWorldCarver<?> carver,
      CarvingContext context,
      ChunkAccess target,
      Function<BlockPos, Holder<Biome>> biomes,
      RandomSource random,
      Aquifer aquifer,
      ChunkPos source,
      CarvingMask mask,
      Operation<Boolean> original) {
    IslandTerrain terrain = IslandTerrain.from(context.randomState());
    if (terrain == null || !aeroscapes$usesIslandHeight(carver))
      return original.call(carver, context, target, biomes, random, aquifer, source, mask);
    var selection = (IslandCarvingContext) (Object) context;
    var previous = selection.aeroscapes$layer();
    boolean carved = false;
    try {
      for (var layer :
          terrain
              .caveHosts(
                  source,
                  selection.aeroscapes$generator(),
                  context.randomState(),
                  target.getHeightAccessorForGeneration())
              .ranges()) {
        selection.aeroscapes$layer(layer);
        carved |= original.call(carver, context, target, biomes, random, aquifer, source, mask);
      }
      return carved;
    } finally {
      selection.aeroscapes$layer(previous);
    }
  }

  private static boolean aeroscapes$usesIslandHeight(ConfiguredWorldCarver<?> carver) {
    return switch (carver.worldCarver().getClass().getName()) {
      case "net.minecraft.world.level.levelgen.carver.CaveWorldCarver",
          "net.minecraft.world.level.levelgen.carver.CanyonWorldCarver",
          "net.hibiscus.naturespirit.world.carver.ReplaceableCaveCarver",
          "net.hibiscus.naturespirit.world.carver.ReplaceableRavineCarver" ->
          true;
      default -> false;
    };
  }

  @WrapOperation(
      method = "applyCarvers",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/ChunkAccess;carverBiome(Ljava/util/function/Supplier;)Lnet/minecraft/world/level/biome/BiomeGenerationSettings;"))
  @SuppressWarnings({
    "PMD.UnusedPrivateMethod",
    "deprecation"
  }) // Uses the same generation-settings API as native applyCarvers, preserving its cache.
  private BiomeGenerationSettings aeroscapes$islandCarverBiome(
      ChunkAccess source,
      Supplier<BiomeGenerationSettings> fallback,
      Operation<BiomeGenerationSettings> original,
      @Local(argsOnly = true) RandomState state) {
    IslandTerrain terrain = IslandTerrain.from(state);
    if (terrain == null) return original.call(source, fallback);
    var generator = (NoiseBasedChunkGenerator) (Object) this;
    return original.call(
        source,
        (Supplier<BiomeGenerationSettings>)
            () -> {
              var hosts =
                  new java.util.ArrayList<>(
                      terrain
                          .caveHosts(
                              source.getPos(),
                              generator,
                              state,
                              source.getHeightAccessorForGeneration())
                          .ranges());
              hosts.sort(
                  java.util.Comparator.comparingInt(
                          (dev.aeroscapes.worldgen.IslandCaveHosts.Range host) ->
                              host.maximum() - host.minimum())
                      .reversed());
              for (var host : hosts) {
                var biome =
                    generator
                        .getBiomeSource()
                        .getNoiseBiome(
                            source.getPos().getMiddleBlockX() >> 2,
                            host.middle() >> 2,
                            source.getPos().getMiddleBlockZ() >> 2,
                            state.sampler());
                var settings = generator.getBiomeGenerationSettings(biome);
                if (settings.getCarvers(GenerationStep.Carving.AIR).iterator().hasNext())
                  return settings;
              }
              return fallback.get();
            });
  }
}
