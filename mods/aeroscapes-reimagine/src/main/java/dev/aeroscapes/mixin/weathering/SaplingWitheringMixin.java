package dev.aeroscapes.mixin.weathering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.ordana.immersive_weathering.data.block_growths.growths.ConfigurableBlockGrowth;
import dev.aeroscapes.weathering.WiltedSaplingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ConfigurableBlockGrowth.class, remap = false)
public abstract class SaplingWitheringMixin {
  @Shadow @Final private boolean targetSelf;

  @WrapOperation(
      method = "tryGrowing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/state/BlockState;canSurvive(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // MixinExtras keeps the actual sapling's support rules.
  private boolean aeroscapes$saplingSupport(
      BlockState replacement,
      LevelReader level,
      BlockPos position,
      Operation<Boolean> original,
      @Local(argsOnly = true) BlockState source) {
    return original.call(
        aeroscapes$withersSapling(replacement, source) ? source : replacement, level, position);
  }

  @WrapOperation(
      method = "tryGrowing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
              ordinal = 0))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Runs after the configured desert or Nether rule succeeds.
  private boolean aeroscapes$saveSapling(
      ServerLevel level,
      BlockPos position,
      BlockState replacement,
      Operation<Boolean> original,
      @Local(argsOnly = true) BlockState source) {
    return aeroscapes$withersSapling(replacement, source)
        ? WiltedSaplingBlock.wilt(level, position, source)
        : original.call(level, position, replacement);
  }

  @Unique
  private boolean aeroscapes$withersSapling(BlockState replacement, BlockState source) {
    return targetSelf && replacement.is(Blocks.DEAD_BUSH) && source.is(BlockTags.SAPLINGS);
  }
}
