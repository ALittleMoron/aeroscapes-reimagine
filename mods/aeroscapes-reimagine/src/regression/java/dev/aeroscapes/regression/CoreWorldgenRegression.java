package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/** Separates seeded natural-density parity from intentional biome/content substitutions. */
public final class CoreWorldgenRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final List<Long> SEEDS =
      List.of(1L, -8315743817914385108L, -4851711576543251912L, 42L, 0L, 777L, -1L, 123456789L);

  private CoreWorldgenRegression() {}

  public static void verify(ServerLevel level, boolean baseline) {
    var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
    var settings = generator.generatorSettings().value();
    var noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
    // This only samples density math, not biome-source selection for another host seed.
    for (long seed : SEEDS) {
      var random = RandomState.create(settings, noises, seed);
      BasinAquiferCacheRegression.verify(random);
      IslandTerrainCacheRegression.verify(random);
      var router = random.router();
      long hash = 0xcbf29ce484222325L;
      for (int sample = 0; sample < 128; sample++) {
        int x = sample * 137 % 8192 - 4096;
        int z = sample * 367 % 8192 - 4096;
        for (int y = -64; y < 304; y += 8) {
          var position = new DensityFunction.SinglePointContext(x, y, z);
          hash = mix(hash, router.finalDensity().compute(position));
          hash = mix(hash, router.initialDensityWithoutJaggedness().compute(position));
          hash = mix(hash, router.fluidLevelFloodednessNoise().compute(position));
          hash = mix(hash, router.fluidLevelSpreadNoise().compute(position));
        }
      }
      LOGGER.info(
          "Core density fingerprint seed={} hash={}", seed, Long.toUnsignedString(hash, 16));
    }
    if (!baseline) {
      require(!ModList.get().isLoaded("aeroscapes"), "External Aero Islands is still loaded");
      require(
          BuiltInRegistries.BLOCK.keySet().stream()
              .noneMatch(key -> key.getNamespace().equals("aeroscapes")),
          "Removed Aero block content is still registered");
      require(
          BuiltInRegistries.ITEM.keySet().stream()
              .noneMatch(key -> key.getNamespace().equals("aeroscapes")),
          "Removed Aero item content is still registered");
      var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
      for (String name : List.of("void_shores", "voidsea", "deep_voidsea", "hallowed_isles")) {
        require(
            !biomes.containsKey(ResourceLocation.parse("aeroscapes:" + name)),
            "Removed Aero biome " + name);
      }
      for (String name : List.of("pillar", "basalt_cluster")) {
        require(
            BuiltInRegistries.FEATURE.containsKey(ResourceLocation.parse("aeroscapes:" + name)),
            "Missing retained feature " + name);
      }
      WorldgenDataRegression.verify(level);
    }
    LOGGER.info("Core worldgen regression passed: baseline={}", baseline);
  }

  private static long mix(long hash, double value) {
    return (hash ^ Double.doubleToLongBits(value)) * 1099511628211L;
  }

  private static void require(boolean accepted, String message) {
    if (!accepted) {
      throw new IllegalStateException(message);
    }
  }
}
