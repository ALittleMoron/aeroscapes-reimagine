package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.regression.FeatureCommitObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ConfiguredFeature.class)
public abstract class ConfiguredFeatureAttemptMixin {
  @WrapMethod(
      method =
          "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Mixin observes native modifier-surviving candidates.
  private boolean aeroscapesRegression$configuredAttempt(
      WorldGenLevel level,
      ChunkGenerator generator,
      RandomSource random,
      BlockPos origin,
      Operation<Boolean> original) {
    if (!FeatureCommitObserver.armed()) return original.call(level, generator, random, origin);
    var attempt =
        FeatureCommitObserver.beginAttempt((ConfiguredFeature<?, ?>) (Object) this, level, origin);
    boolean success;
    try {
      success = original.call(level, generator, random, origin);
    } catch (RuntimeException | Error failure) {
      try {
        FeatureCommitObserver.endAttempt(attempt, null);
      } catch (RuntimeException | Error callbackFailure) {
        if (callbackFailure != failure) failure.addSuppressed(callbackFailure);
      }
      throw failure;
    }
    FeatureCommitObserver.endAttempt(attempt, success);
    return success;
  }
}
