package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.worldgen.BlackstoneOres;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(OreFeature.class)
public abstract class BlackstoneOreFeatureMixin {
  @WrapOperation(
      method = "doPlace",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native ore writes bypass WorldGenRegion.setBlock.
  private BlockState aeroscapesReimagine$matchBlackstone(
      LevelChunkSection section,
      int x,
      int y,
      int z,
      BlockState ore,
      boolean lock,
      Operation<BlockState> original) {
    return original.call(
        section, x, y, z, BlackstoneOres.adapt(section.getBlockState(x, y, z), ore), lock);
  }
}
