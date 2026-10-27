package dev.aeroscapes.mixin.scalablelux;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.platform.SablePlotPlatform;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerLevelPlot.class, remap = false)
public abstract class PlotAuxSaveMixin {
  @WrapOperation(
      method = "save",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/ryanhcode/sable/platform/SablePlotPlatform;writeLightData(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/LevelChunk;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras wraps Sable's per-chunk light save.
  private void aeroscapes$writeChunkLight(
      SablePlotPlatform platform,
      CompoundTag root,
      RegistryAccess registry,
      LevelChunk chunk,
      Operation<Void> original,
      @Local(index = 9) CompoundTag chunkTag) {
    // The pinned Sable loader reads this data from the chunk tag, not the plot root.
    original.call(platform, chunkTag, registry, chunk);
  }
}
