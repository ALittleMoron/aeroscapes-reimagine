package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.regression.FeatureCommitObserver;
import dev.aeroscapes.regression.StructureCommitObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldGenRegion.class)
public abstract class FeatureCommitWriteMixin {
  @Shadow @Final private ServerLevel level;

  @WrapOperation(
      method =
          "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/ChunkAccess;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Old state is the native commit result, not an extra read.
  private BlockState aeroscapesRegression$featureWrite(
      ChunkAccess chunk,
      BlockPos position,
      BlockState state,
      boolean moved,
      Operation<BlockState> original) {
    BlockState before = original.call(chunk, position, state, moved);
    if (FeatureCommitObserver.armed())
      FeatureCommitObserver.committed(
          level, (WorldGenLevel) (Object) this, position, before, state);
    if (StructureCommitObserver.armed())
      StructureCommitObserver.committed(
          level, (WorldGenLevel) (Object) this, position, before, state);
    return before;
  }
}
