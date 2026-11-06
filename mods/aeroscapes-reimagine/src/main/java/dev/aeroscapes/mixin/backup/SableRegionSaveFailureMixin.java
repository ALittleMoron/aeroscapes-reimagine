package dev.aeroscapes.mixin.backup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.backup.BackupSaveFailures;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = SubLevelStorageFile.class, remap = false)
public abstract class SableRegionSaveFailureMixin {
  @Shadow @Final private Path path;

  @WrapMethod(method = "write(ILnet/minecraft/nbt/CompoundTag;)V")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Holding-region helpers also swallow write failures.
  private void aeroscapes$recordFailedSubLevelRegionWrite(
      int index, @Nullable CompoundTag data, Operation<Void> original) throws IOException {
    try {
      original.call(index, data);
    } catch (Exception | Error failure) {
      BackupSaveFailures.record(path.toAbsolutePath().getParent(), failure);
      throw failure;
    }
  }

  @WrapMethod(method = "flush")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Preserve a failure even if the file leaves its cache.
  private void aeroscapes$recordFailedSubLevelRegionFlush(Operation<Void> original)
      throws IOException {
    try {
      original.call();
    } catch (Exception | Error failure) {
      BackupSaveFailures.record(path.toAbsolutePath().getParent(), failure);
      throw failure;
    }
  }
}
