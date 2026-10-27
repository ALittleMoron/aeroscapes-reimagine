package dev.aeroscapes.mixin.scalablelux;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class PlotChunkSkyMixin {
  @Inject(
      method = "setBlockState",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/lighting/LevelLightEngine;checkBlock(Lnet/minecraft/core/BlockPos;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this before the chunk checks light.
  private void aeroscapes$updatePlotSky(
      BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
    LevelChunk chunk = (LevelChunk) (Object) this;
    if (!(chunk.getLevel() instanceof ServerLevel level)) return;
    SubLevelContainer container = SubLevelContainer.getContainer(level);
    if (container == null || container.getPlot(chunk.getPos()) == null) return;
    ChunkSkyLightSources sources = ((ChunkSkyAccess) chunk).aeroscapes$getSkySources();
    if (sources == null)
      throw new IllegalStateException("Missing sky sources for Sable plot chunk " + chunk.getPos());
    sources.update(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
  }
}
