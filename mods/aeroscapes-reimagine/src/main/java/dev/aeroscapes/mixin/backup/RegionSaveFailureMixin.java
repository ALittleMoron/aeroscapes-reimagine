package dev.aeroscapes.mixin.backup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.backup.BackupSaveFailures;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RegionFileStorage.class)
public abstract class RegionSaveFailureMixin {
  @Shadow @Final private Path folder;

  @WrapMethod(method = "write")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Records before IOWorker discards its completed write.
  private void aeroscapes$recordFailedRegionWrite(
      ChunkPos chunk, @Nullable CompoundTag data, Operation<Void> original) throws IOException {
    try {
      original.call(chunk, data);
    } catch (Exception | Error failure) {
      BackupSaveFailures.record(folder, failure);
      throw failure;
    }
  }

  @WrapMethod(method = "flush")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // A later flush cannot prove a failed write succeeded.
  private void aeroscapes$recordFailedRegionFlush(Operation<Void> original) throws IOException {
    try {
      original.call();
    } catch (Exception | Error failure) {
      BackupSaveFailures.record(folder, failure);
      throw failure;
    }
  }
}
