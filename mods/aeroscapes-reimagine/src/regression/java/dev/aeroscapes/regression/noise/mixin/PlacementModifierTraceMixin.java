package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.regression.FeatureCommitObserver;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlacedFeature.class)
public abstract class PlacementModifierTraceMixin {
  @WrapOperation(
      method = "lambda$placeWithContext$3",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/placement/PlacementModifier;getPositions(Lnet/minecraft/world/level/levelgen/placement/PlacementContext;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Ljava/util/stream/Stream;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Observe each native lazy-stream element once.
  private static Stream<BlockPos> aeroscapesRegression$modifier(
      PlacementModifier modifier,
      PlacementContext context,
      RandomSource random,
      BlockPos input,
      Operation<Stream<BlockPos>> original) {
    var stage = FeatureCommitObserver.beginModifier(modifier, context, input);
    Stream<BlockPos> positions = original.call(modifier, context, random, input);
    return stage == null
        ? positions
        : positions.peek(position -> FeatureCommitObserver.modifierOutput(stage, position));
  }
}
