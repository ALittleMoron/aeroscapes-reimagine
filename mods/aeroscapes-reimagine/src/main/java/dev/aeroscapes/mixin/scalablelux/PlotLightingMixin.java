package dev.aeroscapes.mixin.scalablelux;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.lighting.PlotLightEngine;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLevelPlot.class, remap = false)
public abstract class PlotLightingMixin {
  @WrapOperation(
      method = "<init>",
      at =
          @At(
              value = "NEW",
              target =
                  "(Lnet/minecraft/world/level/chunk/LightChunkGetter;ZZ)Lnet/minecraft/world/level/lighting/LevelLightEngine;",
              remap = true))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras wraps Sable's constructor call.
  private LevelLightEngine aeroscapes$plotEngine(
      LightChunkGetter source, boolean block, boolean sky, Operation<LevelLightEngine> original) {
    return new PlotLightEngine(
        source,
        ((net.minecraft.server.level.ServerLevel) source.getLevel()).dimensionType().hasSkyLight());
  }

  @Inject(method = "lightChunk", at = @At("HEAD"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Mixin invokes this before Sable lights a plot chunk.
  private void aeroscapes$plotSkySources(LevelChunk chunk, CallbackInfo ci) {
    ChunkSkyLightSources sources = new ChunkSkyLightSources(chunk);
    ((ChunkSkyAccess) chunk).aeroscapes$setSkySources(sources);
    sources.fillFrom(chunk);
  }
}
