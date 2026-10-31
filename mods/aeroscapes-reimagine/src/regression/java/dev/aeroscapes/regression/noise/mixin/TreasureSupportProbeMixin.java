package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.regression.StructureCommitObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BuriedTreasurePieces.BuriedTreasurePiece.class)
public abstract class TreasureSupportProbeMixin {
  @WrapOperation(
      method = "postProcess",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/WorldGenLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
              ordinal = 1))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Captures the initialized current state at the native below read.
  private BlockState aeroscapesRegression$treasureSupport(
      WorldGenLevel region,
      BlockPos below,
      Operation<BlockState> original,
      @Local(ordinal = 0) BlockState current) {
    BlockState support = original.call(region, below);
    StructureCommitObserver.treasureSupportRead(region, below, current, support);
    return support;
  }
}
