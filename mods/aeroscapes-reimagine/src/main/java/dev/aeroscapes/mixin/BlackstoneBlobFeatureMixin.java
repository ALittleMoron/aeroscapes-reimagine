package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.BlackstoneOres;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.ReplaceBlobsFeature;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceSphereConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Retains deepslate ore veins when Mantle's later blobs change their host material. */
@Mixin(ReplaceBlobsFeature.class)
public abstract class BlackstoneBlobFeatureMixin {
  @WrapOperation(
      method = "place",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Match only the unchanged native blob footprint.
  private boolean aeroscapesReimagine$includeExistingOre(
      BlockState state,
      Block target,
      Operation<Boolean> original,
      @Local(argsOnly = true) FeaturePlaceContext<ReplaceSphereConfiguration> context) {
    if (original.call(state, target)) return true;
    var configuration = context.config();
    return configuration.targetState.is(Blocks.DEEPSLATE)
        && configuration.replaceState.is(Blocks.BLACKSTONE)
        && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("deepslate_")
        && BlackstoneOres.adapt(configuration.replaceState, state) != state;
  }

  @WrapOperation(
      method = "place",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/feature/ReplaceBlobsFeature;setBlock(Lnet/minecraft/world/level/LevelWriter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep preexisting ore rather than replacing it by stone.
  private void aeroscapesReimagine$retainExistingOre(
      ReplaceBlobsFeature feature,
      LevelWriter level,
      BlockPos position,
      BlockState replacement,
      Operation<Void> original,
      @Local(argsOnly = true) FeaturePlaceContext<ReplaceSphereConfiguration> context) {
    if (context.config().targetState.is(Blocks.DEEPSLATE) && replacement.is(Blocks.BLACKSTONE)) {
      BlockState before = context.level().getBlockState(position);
      BlockState adapted = BlackstoneOres.adapt(replacement, before);
      if (adapted != before) replacement = adapted;
    }
    original.call(feature, level, position, replacement);
  }
}
