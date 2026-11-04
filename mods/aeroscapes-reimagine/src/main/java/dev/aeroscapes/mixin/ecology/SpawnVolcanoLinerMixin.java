package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ninni.spawn.server.level.structure.VolcanicIslandPiece;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VolcanicIslandPiece.class)
public abstract class SpawnVolcanoLinerMixin {
  @WrapOperation(
      method = "generateVolcano",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native chamber depth extends below a contained ocean's natural
  // liner.
  private boolean aeroscapes$preserveBasinLiner(
      WorldGenLevel level,
      BlockPos position,
      BlockState state,
      int flags,
      Operation<Boolean> original) {
    IslandTerrain terrain = IslandTerrain.from(level.getLevel().getChunkSource().randomState());
    if (terrain != null) {
      var basin = terrain.basin(position.getX(), position.getZ());
      if (basin != null
          && basin.water(position.getX(), basin.waterline(), position.getZ())
          && position.getY() <= basin.floor(position.getX(), position.getZ()) + 5) return false;
    }
    return original.call(level, position, state, flags);
  }
}
