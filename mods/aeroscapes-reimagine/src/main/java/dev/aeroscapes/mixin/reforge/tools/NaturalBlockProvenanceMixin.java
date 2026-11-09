package dev.aeroscapes.mixin.reforge.tools;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.tools.NaturalBlockProvenance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelChunk.class)
abstract class NaturalBlockProvenanceMixin {
  @WrapOperation(
      method = "setBlockState",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private BlockState aeroscapes$recordBlockChanges(
      LevelChunkSection section,
      int x,
      int y,
      int z,
      BlockState incoming,
      Operation<BlockState> original,
      @Local(argsOnly = true) BlockPos pos) {
    BlockState previous = original.call(section, x, y, z, incoming);
    NaturalBlockProvenance.recordChange((LevelChunk) (Object) this, pos, incoming, previous);
    return previous;
  }
}
