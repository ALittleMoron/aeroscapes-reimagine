package dev.aeroscapes.mixin.scalablelux;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.platform.SablePlotPlatform;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerLevelPlot.class, remap = false)
public abstract class PlotAuxLoadMixin {
  @WrapOperation(
      method = "load",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/ryanhcode/sable/platform/SablePlotPlatform;readLightData(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/LevelChunk;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras wraps Sable's per-chunk light load.
  private void aeroscapes$readLegacyAux(
      SablePlotPlatform platform,
      CompoundTag chunkTag,
      RegistryAccess registry,
      LevelChunk chunk,
      Operation<Void> original,
      @Local(argsOnly = true) CompoundTag root) {
    original.call(platform, chunkTag, registry, chunk);
    // Sable 2.0.6 stored at most the last chunk's auxiliary list on the plot root.
    // Recover only entries belonging to this chunk; a current chunk list takes precedence.
    String key = "neoforge:aux_lights";
    if (chunkTag.contains(key, Tag.TAG_LIST) || !root.contains(key, Tag.TAG_LIST)) return;
    ListTag legacy = root.getList(key, Tag.TAG_COMPOUND);
    ListTag matching = new ListTag();
    for (int i = 0; i < legacy.size(); i++) {
      CompoundTag entry = legacy.getCompound(i);
      if (entry.contains("pos", Tag.TAG_LONG)
          && new ChunkPos(BlockPos.of(entry.getLong("pos"))).equals(chunk.getPos()))
        matching.add(entry.copy());
    }
    if (!matching.isEmpty()) {
      CompoundTag migrated = new CompoundTag();
      migrated.put(key, matching);
      original.call(platform, migrated, registry, chunk);
    }
  }
}
