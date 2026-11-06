package dev.aeroscapes.mixin.backup;

import dev.aeroscapes.backup.BackupSaveFailures;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ChunkSaveFailureMixin {
  @Inject(method = "reportChunkSaveFailure", at = @At("HEAD"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Also covers failures before chunk I/O is submitted.
  private void aeroscapes$recordFailedChunkSave(
      Throwable failure, RegionStorageInfo storage, ChunkPos chunk, CallbackInfo callback) {
    MinecraftServer server = (MinecraftServer) (Object) this;
    BackupSaveFailures.record(server.getWorldPath(LevelResource.ROOT), failure);
  }
}
