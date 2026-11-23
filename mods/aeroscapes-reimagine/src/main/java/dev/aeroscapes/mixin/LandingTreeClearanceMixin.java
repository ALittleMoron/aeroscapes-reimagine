package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.worldgen.IslandStopStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TreeFeature.class)
public abstract class LandingTreeClearanceMixin {
  @WrapOperation(
      method = "doPlace",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/feature/TreeFeature;getMaxFreeTreeHeight(Lnet/minecraft/world/level/LevelSimulatedReader;ILnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/feature/configurations/TreeConfiguration;)I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes the wrapper.
  private int aeroscapes$clearLandingTrunks(
      TreeFeature feature,
      LevelSimulatedReader reader,
      int height,
      BlockPos origin,
      TreeConfiguration configuration,
      Operation<Integer> original) {
    if (reader instanceof WorldGenRegion region
        && IslandStopStructure.treeBlocksLanding(
            region, origin, height, configuration.minimumSize)) {
      return -1;
    }
    return original.call(feature, reader, height, origin, configuration);
  }

  @Inject(method = "validTreePos", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes the callback.
  private static void aeroscapes$clearLandingBranches(
      LevelSimulatedReader reader, BlockPos point, CallbackInfoReturnable<Boolean> callback) {
    if (reader instanceof WorldGenRegion region
        && IslandStopStructure.reservedLandingTreeCell(region, point)) {
      callback.setReturnValue(false);
    }
  }
}
