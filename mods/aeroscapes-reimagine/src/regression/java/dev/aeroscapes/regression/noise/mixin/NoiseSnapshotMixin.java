package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.regression.NoiseSnapshotRegression;
import dev.aeroscapes.regression.StageSnapshotRegression;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;

/** Regression-only stage boundary; never present in the shipped pack. */
@Mixin(ChunkStatusTasks.class)
public abstract class NoiseSnapshotMixin {
  @WrapMethod(method = "generateNoise")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras wraps the actual NOISE task future.
  private static CompletableFuture<ChunkAccess> aeroscapesRegression$freezeNoise(
      WorldGenContext context,
      ChunkStep step,
      StaticCache2D<GenerationChunkHolder> chunks,
      ChunkAccess chunk,
      Operation<CompletableFuture<ChunkAccess>> original) {
    return StageSnapshotRegression.capture(
        context.level(),
        step.targetStatus(),
        chunk.getPos(),
        NoiseSnapshotRegression.captureNoise(
            context.level(), chunk.getPos(), original.call(context, step, chunks, chunk)));
  }

  @WrapMethod(method = {"generateSurface", "generateCarvers", "generateFeatures"})
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Freeze each native task before its future completes.
  private static CompletableFuture<ChunkAccess> aeroscapesRegression$freezeStage(
      WorldGenContext context,
      ChunkStep step,
      StaticCache2D<GenerationChunkHolder> chunks,
      ChunkAccess chunk,
      Operation<CompletableFuture<ChunkAccess>> original) {
    if (step.targetStatus() == ChunkStatus.FEATURES)
      StageSnapshotRegression.captureFeatureInput(context.level(), chunk);
    return StageSnapshotRegression.capture(
        context.level(),
        step.targetStatus(),
        chunk.getPos(),
        original.call(context, step, chunks, chunk));
  }
}
