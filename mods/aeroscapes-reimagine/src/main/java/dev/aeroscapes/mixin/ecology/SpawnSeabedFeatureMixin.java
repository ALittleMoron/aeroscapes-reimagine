package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.aquatic.AquaticWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets = {
      "com.ninni.spawn.server.level.feature.RedAlgaeOceanFeature",
      "com.ninni.spawn.server.level.feature.StrandedSpawnFeature"
    },
    remap = false)
public abstract class SpawnSeabedFeatureMixin {
  @WrapOperation(
      method = "place",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/WorldGenLevel;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep seabed ecology in a basin hidden by another island.
  private BlockPos aeroscapes$basinFloor(
      WorldGenLevel level, Heightmap.Types type, BlockPos position, Operation<BlockPos> original) {
    BlockPos nativePosition = original.call(level, type, position);
    return nativePosition.atY(
        AquaticWater.floor(level, position.getX(), position.getZ(), nativePosition.getY()));
  }
}
