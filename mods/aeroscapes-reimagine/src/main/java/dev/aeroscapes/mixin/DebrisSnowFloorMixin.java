package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "eu.midnightdust.motschen.rocks.world.feature.SnowFeature", remap = false)
public abstract class DebrisSnowFloorMixin {
  @WrapOperation(
      method = "place",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/WorldGenLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Keep the selected island when resampling nearby.
  private int aeroscapes$snowFloor(
      WorldGenLevel level,
      Heightmap.Types type,
      int x,
      int z,
      Operation<Integer> original,
      FeaturePlaceContext<?> context) {
    int nativeHeight = original.call(level, type, x, z);
    if (IslandTerrain.from(level.getLevel().getChunkSource().randomState()) == null)
      return nativeHeight;
    int origin = context.origin().getY();
    if (Math.abs(origin - nativeHeight) <= 16) return nativeHeight;
    var cursor = new BlockPos.MutableBlockPos();
    for (int delta = 0; delta <= 16; delta++) {
      int[] candidates = {origin + delta, origin - delta};
      for (int y : candidates) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
        if (level.getBlockState(cursor.set(x, y, z)).is(Blocks.SNOW)) return y;
      }
    }
    return origin;
  }
}
