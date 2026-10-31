package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.regression.FeatureCommitObserver;
import dev.aeroscapes.regression.StructureCommitObserver;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(OreFeature.class)
public abstract class OreFeatureCommitMixin {
  @WrapOperation(
      method = "doPlace",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/feature/OreFeature;canPlaceOre(Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/feature/configurations/OreConfiguration;Lnet/minecraft/world/level/levelgen/feature/configurations/OreConfiguration$TargetBlockState;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Observe the native target predicate's actual outcome.
  private static boolean aeroscapesRegression$oreTarget(
      BlockState actual,
      Function<BlockPos, BlockState> neighbors,
      RandomSource random,
      OreConfiguration configuration,
      OreConfiguration.TargetBlockState target,
      BlockPos.MutableBlockPos position,
      Operation<Boolean> original,
      @Local(argsOnly = true) WorldGenLevel region) {
    boolean accepted = original.call(actual, neighbors, random, configuration, target, position);
    if (FeatureCommitObserver.armed())
      FeatureCommitObserver.oreTargetTest(region, actual, target.state, position, accepted);
    return accepted;
  }

  @WrapOperation(
      method = "doPlace",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native ore writes bypass WorldGenRegion.setBlock.
  private BlockState aeroscapesRegression$oreCommit(
      LevelChunkSection section,
      int x,
      int y,
      int z,
      BlockState state,
      boolean lock,
      Operation<BlockState> original,
      @Local(argsOnly = true) WorldGenLevel region,
      @Local BlockPos.MutableBlockPos position) {
    BlockState before = original.call(section, x, y, z, state, lock);
    BlockState placed = section.getBlockState(x, y, z);
    if (FeatureCommitObserver.armed() || StructureCommitObserver.armed()) {
      if ((position.getX() & 15) != x || (position.getY() & 15) != y || (position.getZ() & 15) != z)
        throw new IllegalStateException("Native ore local/absolute write coordinates differ");
      if (FeatureCommitObserver.armed())
        FeatureCommitObserver.committed(region.getLevel(), region, position, before, placed);
      if (StructureCommitObserver.armed())
        StructureCommitObserver.committed(region.getLevel(), region, position, before, placed);
    }
    return before;
  }
}
