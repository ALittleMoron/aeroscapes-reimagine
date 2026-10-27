package dev.aeroscapes.regression.opencl.mixin;

import com.ishland.c2me.opts.accel.opencl.common.compiler.emitters.misc.CLBlockStateMappings;
import com.ishland.c2me.opts.accel.opencl.common.gen.CLServerBatchedBiomeNoiseContext;
import dev.aeroscapes.regression.NoiseSnapshotRegression;
import dev.aeroscapes.regression.StageSnapshotRegression;
import java.nio.ByteBuffer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native batch future cannot advance until this block-write callback returns. */
@Mixin(
    targets = "com.ishland.c2me.opts.accel.opencl.common.gen.CLServerBatchedBiomeNoiseContext",
    remap = false)
public abstract class OpenClNoiseSnapshotMixin {
  @Shadow @Final private RandomState noiseConfig;
  @Shadow @Final private ChunkPos startingPos;

  @Inject(method = "writeBlocks", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin freezes completed native GPU block output.
  private void aeroscapesRegression$freezeNoise(
      StaticCache2D<ProtoChunk> chunks,
      CLBlockStateMappings mappings,
      int height,
      NoiseGeneratorSettings settings,
      int width,
      ByteBuffer output,
      CallbackInfo callback) {
    NoiseSnapshotRegression.captureOpenCl(
        noiseConfig, chunks, startingPos, CLServerBatchedBiomeNoiseContext.BATCH_SIZE);
    StageSnapshotRegression.captureOpenCl(
        noiseConfig, chunks, startingPos, CLServerBatchedBiomeNoiseContext.BATCH_SIZE);
  }
}
