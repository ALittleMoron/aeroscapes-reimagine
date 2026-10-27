package dev.aeroscapes.mixin.scalablelux;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.neoforge.common.world.LevelChunkAuxiliaryLightManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LevelChunkAuxiliaryLightManager.class, remap = false)
public abstract class PlotAuxLightMixin {
  @Shadow @Final private LevelChunk owner;

  @WrapOperation(
      method = "setLightAt",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/lighting/LevelLightEngine;checkBlock(Lnet/minecraft/core/BlockPos;)V",
              remap = true))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras wraps the NeoForge light notification.
  private void aeroscapes$plotAux(LevelLightEngine engine, BlockPos pos, Operation<Void> original) {
    if (owner.getLevel() instanceof ServerLevel level) {
      SubLevelContainer container = SubLevelContainer.getContainer(level);
      var plot = container == null ? null : container.getPlot(new ChunkPos(pos));
      if (plot != null) {
        // NeoForge normally notifies the world engine; this chunk belongs to a Sable plot.
        plot.getLightEngine().checkBlock(pos);
        return;
      }
    }
    original.call(engine, pos);
  }
}
