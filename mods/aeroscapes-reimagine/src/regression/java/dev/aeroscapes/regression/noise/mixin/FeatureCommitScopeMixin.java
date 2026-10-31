package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.regression.FeatureCommitObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkGenerator.class)
public abstract class FeatureCommitScopeMixin {
  @WrapOperation(
      method = "applyBiomeDecoration",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;placeWithBiomeCheck(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Mixin observes the exact native biome feature loop.
  private boolean aeroscapesRegression$featureScope(
      PlacedFeature feature,
      WorldGenLevel region,
      ChunkGenerator generator,
      RandomSource random,
      BlockPos origin,
      Operation<Boolean> original) {
    if (!FeatureCommitObserver.armed())
      return original.call(feature, region, generator, random, origin);
    var previous = FeatureCommitObserver.enter(feature, region, origin);
    try {
      return original.call(feature, region, generator, random, origin);
    } finally {
      FeatureCommitObserver.restore(previous);
    }
  }
}
